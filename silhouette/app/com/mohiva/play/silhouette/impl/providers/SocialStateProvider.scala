/**
 * Copyright 2017 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.AuthInfo
import com.mohiva.play.silhouette.api.crypto.{ Base64, Signer }
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.providers.DefaultSocialStateHandler._
import com.mohiva.play.silhouette.impl.providers.SocialStateItem._
import play.api.libs.json.{ Format, JsValue, Json }
import play.api.mvc.Result

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

/**
 * A stateful auth info, wraps the `AuthInfo` with user state.
 *
 * @param authInfo  The auth info.
 * @param userState The user state.
 * @tparam A The type of the auth info.
 * @tparam S The type of the user state.
 */
case class StatefulAuthInfo[+A <: AuthInfo, +S <: SocialStateItem](authInfo: A, userState: S)

/**
 * Represents the state a social provider can handle.
 *
 * The state consists of a `Set` containing different state items.
 *
 * @param items The list of social state items.
 */
case class SocialState(items: Set[SocialStateItem])

/**
 * An item which can be a part of a social state.
 *
 * The social state consists of one state item per handler. So this item describes the state
 * an handler can handle. A state item can be of any type. The handler to which the state item
 * pertains, must be able to serialize/deserialize this state item.
 */
trait SocialStateItem

/**
 * The companion object of the [[SocialStateItem]].
 */
object SocialStateItem {

  /**
   * A class which represents the serialized structure of a social state item.
   *
   * @param id   A unique identifier for the state item.
   * @param data The state item data as JSON value.
   */
  case class ItemStructure(id: String, data: JsValue) {

    /**
     * Returns the serialized representation of the item.
     *
     * @return The serialized representation of the item.
     */
    def asString = s"${Base64.encode(id)}-${Base64.encode(data)}"
  }

  /**
   * The companion object of the [[ItemStructure]].
   */
  object ItemStructure {

    /**
     * An extractor which unserializes a state item from a string.
     *
     * @param str The string to unserialize.
     * @return Some [[ItemStructure]] instance on success, None on failure.
     */
    def unapply(str: String): Option[ItemStructure] = {
      str.split('-').toList match {
        case List(id, data) =>
          Some(ItemStructure(Base64.decode(id), Json.parse(Base64.decode(data))))
        case _ => None
      }
    }
  }
}

/**
 * Extends the [[SocialProvider]] with the ability to handle provider specific state.
 */
trait SocialStateProvider extends SocialProvider {

  /**
   * Authenticates the user and returns the auth information and the user state.
   *
   * Returns either a [[StatefulAuthInfo]] if all went OK or a `play.api.mvc.Result` that the controller
   * sends to the browser (e.g.: in the case of OAuth where the user needs to be redirected to the service
   * provider).
   *
   * @param format   The JSON format to transform the user state into JSON.
   * @param request  The request.
   * @param classTag The class tag for the user state item.
   * @tparam S The type of the user state item.
   * @tparam B The type of the request body.
   * @return Either a `play.api.mvc.Result` or the [[StatefulAuthInfo]] from the provider.
   */
  def authenticate[S <: SocialStateItem, B](userState: S)(
    implicit
    format: Format[S],
    request: ExtractableRequest[B],
    classTag: ClassTag[S]
  ): Future[Either[Result, StatefulAuthInfo[A, S]]]
}

/**
 * Provides a way to handle different types of state for providers that allow a state param.
 *
 * Some authentication protocols defines a state param which can be used to transport some
 * state to an authentication provider. The authentication provider sends this state back
 * to the application, after the authentication to the provider was granted.
 *
 * The state parameter can be used for different things. Silhouette provides two state handlers
 * out of the box. One state handler can transport additional user state to the provider. This
 * could be an URL were the user should be redirected after authentication to the provider, or
 * any other per-authentication based state. An other important state handler protects the
 * application for CSRF attacks.
 */
trait SocialStateHandler {

  /**
   * The concrete instance of the state handler.
   */
  type Self <: SocialStateHandler

  /**
   * The item handlers configured for this handler
   */
  val handlers: Set[SocialStateItemHandler]

  /**
   * Creates a copy of the state provider with a new handler added.
   *
   * There exists two types of state handlers. The first type are global state handlers which can be configured
   * by the user with the help of a configuration mechanism or through dependency injection. And there a local
   * state handlers which are provided by the application itself. This method exists to handle the last type of
   * state handlers, because it allows to extend the list of user defined state handlers from inside the library.
   *
   * @param handler The handler to add.
   * @return A new state provider with a new handler added.
   */
  def withHandler(handler: SocialStateItemHandler): Self

  /**
   * Gets the social state for all handlers.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The social state for all handlers.
   */
  def state(implicit ec: ExecutionContext): Future[SocialState]

  /**
   * Serializes the given state into a single state value which can be passed with the state param.
   *
   * @param state The social state to serialize.
   * @return The serialized state as string.
   */
  def serialize(state: SocialState): String

  /**
   * Unserializes the social state from the state param.
   *
   * @param state   The state to unserialize.
   * @param request The request to read the value of the state param from.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The social state on success, an error on failure.
   */
  def unserialize[B](state: String)(implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[SocialState]

  /**
   * Publishes the state to the client.
   *
   * @param result  The result to send to the client.
   * @param state   The state to publish.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def publish[B](result: Result, state: SocialState)(implicit request: ExtractableRequest[B]): Result
}

/**
 * The base implementation of the [[SocialStateHandler]].
 *
 * @param handlers The item handlers configured for this handler.
 * @param signer   The signer implementation to sign the state.
 */
class DefaultSocialStateHandler(val handlers: Set[SocialStateItemHandler], signer: Signer)
  extends SocialStateHandler {

  /**
   * The concrete instance of the state provider.
   */
  override type Self = DefaultSocialStateHandler

  /**
   * Creates a copy of the state provider with a new handler added.
   *
   * There exists two types of state handlers. The first type are global state handlers which can be configured
   * by the user with the help of a configuration mechanism or through dependency injection. And there a local
   * state handlers which are provided by the application itself. This method exists to handle the last type of
   * state handlers, because it allows to extend the list of user defined state handlers from inside the library.
   *
   * @param handler The handler to add.
   * @return A new state provider with a new handler added.
   */
  override def withHandler(handler: SocialStateItemHandler): DefaultSocialStateHandler = {
    new DefaultSocialStateHandler(handlers + handler, signer)
  }

  /**
   * Gets the social state for all handlers.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The social state for all handlers.
   */
  override def state(implicit ec: ExecutionContext): Future[SocialState] = {
    Future.sequence(handlers.map(_.item)).map(items => SocialState(items.toSet))
  }

  /**
   * Serializes the given state into a single state value which can be passed with the state param.
   *
   * If no handler is registered on the provider then we omit state signing, because it makes no sense the sign
   * an empty state.
   *
   * @param state The social state to serialize.
   * @return The serialized state as string.
   */
  override def serialize(state: SocialState): String = {
    if (handlers.isEmpty || state.items.isEmpty) {
      ""
    } else {
      signer.sign(state.items.flatMap { i =>
        handlers.flatMap(h => h.canHandle(i).map(h.serialize)).map(_.asString)
      }.mkString("."))
    }
  }

  /**
   * Unserializes the social state from the state param.
   *
   * If no handler is registered on the provider then we omit the state validation. This is needed in some cases
   * where the authentication process was started from a client side library and not from Silhouette.
   *
   * @param state   The state to unserialize.
   * @param request The request to read the value of the state param from.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The social state on success, an error on failure.
   */
  override def unserialize[B](state: String)(
    implicit
    request: ExtractableRequest[B],
    ec: ExecutionContext
  ): Future[SocialState] = {
    if (handlers.isEmpty) {
      Future.successful(SocialState(Set()))
    } else {
      Future.fromTry(signer.extract(state)).flatMap { state =>
        state.split('.').toList match {
          case Nil | List("") =>
            Future.successful(SocialState(Set()))
          case items =>
            Future.sequence {
              items.map {
                case ItemStructure(item) => handlers.find(_.canHandle(item)) match {
                  case Some(handler) => handler.unserialize(item)
                  case None          => throw new ProviderException(MissingItemHandlerError.format(item))
                }
                case item => throw new ProviderException(ItemExtractionError.format(item))
              }
            }.map(items => SocialState(items.toSet))
        }
      }
    }
  }

  /**
   * Publishes the state to the client.
   *
   * @param result  The result to send to the client.
   * @param state   The state to publish.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   * @see [[PublishableSocialStateItemHandler]]
   */
  override def publish[B](result: Result, state: SocialState)(implicit request: ExtractableRequest[B]): Result = {
    handlers.collect { case h: PublishableSocialStateItemHandler => h }.foldLeft(result) { (r, handler) =>
      state.items.foldLeft(r) { (r, item) =>
        handler.canHandle(item).map(item => handler.publish(item, r)).getOrElse(r)
      }
    }
  }
}

/**
 * The companion object for the [[DefaultSocialStateHandler]] class.
 */
object DefaultSocialStateHandler {

  /**
   * Some errors.
   */
  val MissingItemHandlerError = "None of the registered handlers can handle the given state item: %s"
  val ItemExtractionError = "Cannot extract social state item from string: %s"
}

/**
 * Handles state for different purposes.
 */
trait SocialStateItemHandler {

  /**
   * The item the handler can handle.
   */
  type Item <: SocialStateItem

  /**
   * Gets the state item the handler can handle.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The state params the handler can handle.
   */
  def item(implicit ec: ExecutionContext): Future[Item]

  /**
   * Indicates if a handler can handle the given [[SocialStateItem]].
   *
   * This method should check if the [[serialize]] method of this handler can serialize the given
   * unserialized state item.
   *
   * @param item The item to check for.
   * @return `Some[Item]` casted state item if the handler can handle the given state item, `None` otherwise.
   */
  def canHandle(item: SocialStateItem): Option[Item]

  /**
   * Indicates if a handler can handle the given unserialized state item.
   *
   * This method should check if the [[unserialize]] method of this handler can unserialize the given
   * serialized state item.
   *
   * @param item    The item to check for.
   * @param request The request instance to get additional data to validate against.
   * @tparam B The type of the request body.
   * @return True if the handler can handle the given state item, false otherwise.
   */
  def canHandle[B](item: ItemStructure)(implicit request: ExtractableRequest[B]): Boolean

  /**
   * Returns a serialized value of the state item.
   *
   * @param item The state item to serialize.
   * @return The serialized state item.
   */
  def serialize(item: Item): ItemStructure

  /**
   * Unserializes the state item.
   *
   * @param item    The state item to unserialize.
   * @param request The request instance to get additional data to validate against.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The unserialized state item.
   */
  def unserialize[B](item: ItemStructure)(
    implicit
    request: ExtractableRequest[B],
    ec: ExecutionContext
  ): Future[Item]
}

/**
 * A state item handler which can publish its internal state to the client.
 *
 * Some state item handlers, like the CSRF state handler, needs the ability to publish state to a cookie.
 * So if you have such a state item handler, then mixin this trait, to publish the state item to the client.
 */
trait PublishableSocialStateItemHandler {
  self: SocialStateItemHandler =>

  /**
   * Publishes the state to the client.
   *
   * @param item    The item to publish.
   * @param result  The result to send to the client.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def publish[B](item: Item, result: Result)(implicit request: ExtractableRequest[B]): Result
}
