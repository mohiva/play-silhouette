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
package com.mohiva.play.silhouette.impl.providers.state

import javax.inject.Inject

import com.mohiva.play.silhouette.api.Logger
import com.mohiva.play.silhouette.api.crypto.Signer
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, IDGenerator }
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import com.mohiva.play.silhouette.impl.providers.state.CsrfStateItemHandler._
import com.mohiva.play.silhouette.impl.providers.{ PublishableSocialStateItemHandler, SocialStateItem, SocialStateItemHandler }
import play.api.libs.json.{ Format, Json }
import play.api.mvc.{ Cookie, RequestHeader, Result }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
 * The item the handler can handle.
 *
 * @param token A unique token used to protect the application against CSRF attacks.
 */
case class CsrfStateItem(token: String) extends SocialStateItem

/**
 * The companion object of the [[CsrfStateItem]].
 */
object CsrfStateItem {

  /**
   * Converts the [[CsrfStateItem]] to JSON and vice versa.
   */
  implicit val csrfFormat: Format[CsrfStateItem] = Json.format[CsrfStateItem]
}

/**
 * Protects the application against CSRF attacks.
 *
 * The handler stores a unique token in provider state and the same token in a signed client side cookie. After the
 * provider redirects back to the application both tokens will be compared. If both tokens are the same than the
 * application can trust the redirect source.
 *
 * @param settings     The state settings.
 * @param idGenerator  The ID generator used to create the state value.
 * @param signer       The signer implementation.
 */
class CsrfStateItemHandler @Inject() (
  settings: CsrfStateSettings,
  idGenerator: IDGenerator,
  signer: Signer
) extends SocialStateItemHandler with Logger
  with PublishableSocialStateItemHandler {

  /**
   * The item the handler can handle.
   */
  override type Item = CsrfStateItem

  /**
   * Gets the state item the handler can handle.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The state params the handler can handle.
   */
  override def item(implicit ec: ExecutionContext): Future[Item] = idGenerator.generate.map(CsrfStateItem.apply)

  /**
   * Indicates if a handler can handle the given [[SocialStateItem]].
   *
   * This method should check if the [[serialize]] method of this handler can serialize the given
   * unserialized state item.
   *
   * @param item The item to check for.
   * @return `Some[Item]` casted state item if the handler can handle the given state item, `None` otherwise.
   */
  override def canHandle(item: SocialStateItem): Option[Item] = item match {
    case i: Item => Some(i)
    case _       => None
  }

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
  override def canHandle[B](item: ItemStructure)(implicit request: ExtractableRequest[B]): Boolean = {
    item.id == ID && {
      clientState match {
        case Success(i) => i == item.data.as[Item]
        case Failure(e) =>
          logger.warn(e.getMessage, e)
          false
      }
    }
  }

  /**
   * Returns a serialized value of the state item.
   *
   * @param item The state item to serialize.
   * @return The serialized state item.
   */
  override def serialize(item: Item): ItemStructure = ItemStructure(ID, Json.toJson(item))

  /**
   * Unserializes the state item.
   *
   * @param item    The state item to unserialize.
   * @param request The request instance to get additional data to validate against.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The unserialized state item.
   */
  override def unserialize[B](item: ItemStructure)(
    implicit
    request: ExtractableRequest[B],
    ec: ExecutionContext
  ): Future[Item] = {
    Future.fromTry(Try(item.data.as[Item]))
  }

  /**
   * Publishes the CSRF token to the client.
   *
   * @param item    The item to publish.
   * @param result  The result to send to the client.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def publish[B](item: Item, result: Result)(implicit request: ExtractableRequest[B]): Result = {
    result.withCookies(Cookie(
      name = settings.cookieName,
      value = signer.sign(item.token),
      maxAge = Some(settings.expirationTime.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie,
      sameSite = settings.sameSite
    ))
  }

  /**
   * Gets the CSRF token from the cookie.
   *
   * @param request The request header.
   * @return The CSRF token on success, otherwise a failure.
   */
  private def clientState(implicit request: RequestHeader): Try[Item] = {
    request.cookies.get(settings.cookieName) match {
      case Some(cookie) => signer.extract(cookie.value).map(token => CsrfStateItem(token))
      case None         => Failure(new OAuth2StateException(ClientStateDoesNotExists.format(settings.cookieName)))
    }
  }
}

/**
 * The companion object.
 */
object CsrfStateItemHandler {

  /**
   * The ID of the handler.
   */
  val ID = "csrf-state"

  /**
   * The error messages.
   */
  val ClientStateDoesNotExists = "[Silhouette][CsrfStateItemHandler] State cookie doesn't exists for name: %s"
}

/**
 * The settings for the Csrf State.
 *
 * @param cookieName     The cookie name.
 * @param cookiePath     The cookie path.
 * @param cookieDomain   The cookie domain.
 * @param secureCookie   Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnlyCookie Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param sameSite       The SameSite attribute for this cookie (for CSRF protection).
 * @param expirationTime State expiration. Defaults to 5 minutes which provides sufficient time to log in, but
 *                       not too much. This is a balance between convenience and security.
 */
case class CsrfStateSettings(
  cookieName: String = "CsrfState",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = true,
  httpOnlyCookie: Boolean = true,
  sameSite: Option[Cookie.SameSite] = Some(Cookie.SameSite.Lax),
  expirationTime: FiniteDuration = 5 minutes
)
