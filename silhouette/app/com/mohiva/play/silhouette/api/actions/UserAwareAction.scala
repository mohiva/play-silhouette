/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.api.actions

import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import play.api.{ Configuration, Environment => PlayEnv }
import play.api.inject.Module
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A request that adds maybe the identity and maybe the authenticator for the current call.
 *
 * @tparam E The type of the environment.
 */
trait UserAwareRequestHeader[E <: Env] extends RequestHeader {
  /**
   * @return Some identity implementation if authentication was successful, None otherwise.
   */
  def identity: Option[E#I]

  /**
   * @return Some authenticator implementation if authentication was successful, None otherwise.
   */
  def authenticator: Option[E#A]
}

trait UserAwareRequest[E <: Env, +B] extends Request[B] with UserAwareRequestHeader[E]

object UserAwareRequest {

  /**
   * A request that adds maybe the identity and maybe the authenticator for the current call.
   *
   * @param identity      Some identity implementation if authentication was successful, None otherwise.
   * @param authenticator Some authenticator implementation if authentication was successful, None otherwise.
   * @param request The current request.
   * @tparam E The type of the environment.
   * @tparam B The type of the request body.
   */
  def apply[E <: Env, B](
    identity: Option[E#I],
    authenticator: Option[E#A],
    request: Request[B]): UserAwareRequest[E, B] = {
    new DefaultUserAwareRequest(identity, authenticator, request)
  }

  /**
   * Unapply method for user aware request.
   *
   * @param userAwareRequest the user aware request.
   * @tparam E The type of the environment.
   * @tparam B The type of the request body.
   */
  def unapply[E <: Env, B](userAwareRequest: UserAwareRequest[E, B]): Option[(Option[E#I], Option[E#A], Request[B])] = {
    userAwareRequest match {
      case duar: DefaultUserAwareRequest[E, B] =>
        Some((duar.identity, duar.authenticator, duar.request))
      case uar: UserAwareRequest[E, B] =>
        Some((uar.identity, uar.authenticator, uar))
    }
  }
}

class DefaultUserAwareRequest[E <: Env, B](
  val identity: Option[E#I],
  val authenticator: Option[E#A],
  val request: Request[B]
) extends WrappedRequest(request) with UserAwareRequest[E, B]

/**
 * Request handler builder implementation to provide the foundation for user-aware request handlers.
 *
 * @param environment The environment instance to handle the request.
 * @tparam E The type of the environment.
 */
case class UserAwareRequestHandlerBuilder[E <: Env](environment: Environment[E])
  extends RequestHandlerBuilder[E, ({ type R[B] = UserAwareRequest[E, B] })#R] {

  /**
   * Invokes the block.
   *
   * @param block   The block of code to invoke.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  override def invokeBlock[B, T](block: UserAwareRequest[E, B] => Future[HandlerResult[T]])(implicit request: Request[B]) = {
    handleAuthentication.flatMap {
      // A valid authenticator was found and the identity may be exists
      case (Some(authenticator), identity) if authenticator.extract.isValid =>
        handleBlock(authenticator, a => block(UserAwareRequest(identity, Some(a), request)))
      // An invalid authenticator was found. The authenticator will be discarded
      case (Some(authenticator), _) if !authenticator.extract.isValid =>
        block(UserAwareRequest(None, None, request)).flatMap {
          case hr @ HandlerResult(pr, _) =>
            environment.authenticatorService.discard(authenticator.extract, pr).map(r => hr.copy(r))
        }
      // No authenticator and no user was found
      case _ => block(UserAwareRequest(None, None, request))
    }
  }
}

/**
 * A user-aware request handler.
 *
 * A handler that can be used for endpoints that need to know if there is a current user but
 * can be executed even if there isn't one.
 */
trait UserAwareRequestHandler {

  /**
   * Applies the environment to the request handler stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A user-aware request handler builder.
   */
  def apply[E <: Env](environment: Environment[E]): UserAwareRequestHandlerBuilder[E]
}

/**
 * Default implementation of the [[UserAwareRequestHandler]].
 */
class DefaultUserAwareRequestHandler extends UserAwareRequestHandler {

  /**
   * Applies the environment to the request handler stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A user-aware request handler builder.
   */
  override def apply[E <: Env](environment: Environment[E]) = UserAwareRequestHandlerBuilder[E](environment)
}

/**
 * Action builder implementation to provide the foundation for user-aware actions.
 *
 * @param requestHandler The request handler instance.
 * @param parser         The body parser.
 * @tparam E The type of the environment.
 * @tparam P The type of the request body.
 */
case class UserAwareActionBuilder[E <: Env, P](
  requestHandler: UserAwareRequestHandlerBuilder[E],
  parser: BodyParser[P]
) extends ActionBuilder[({ type R[B] = UserAwareRequest[E, B] })#R, P] {

  /**
   * Invokes the block.
   *
   * @param request The current request.
   * @param block   The block of code to invoke.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def invokeBlock[B](request: Request[B], block: UserAwareRequest[E, B] => Future[Result]) = {
    implicit val ec = executionContext
    requestHandler(request) { r =>
      block(r).map(r => HandlerResult(r))
    }.map(_.result)
  }

  /**
   * Get the execution context to run the request in.
   *
   * @return The execution context.
   */
  override protected def executionContext: ExecutionContext = requestHandler.executionContext
}

/**
 * An action based on the [[UserAwareRequestHandler]].
 */
trait UserAwareAction {

  /**
   * The instance of the user-aware request handler.
   */
  val requestHandler: UserAwareRequestHandler

  /**
   * The default body parser.
   */
  val bodyParser: BodyParsers.Default

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A user-aware action builder.
   */
  def apply[E <: Env](environment: Environment[E]): UserAwareActionBuilder[E, AnyContent]
}

/**
 * Default implementation of the [[UserAwareAction]].
 *
 * @param requestHandler The instance of the user-aware request handler.
 * @param bodyParser     The default body parser.
 */
class DefaultUserAwareAction @Inject() (
  val requestHandler: UserAwareRequestHandler,
  val bodyParser: BodyParsers.Default
) extends UserAwareAction {

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A user-aware action builder.
   */
  override def apply[E <: Env](environment: Environment[E]) =
    UserAwareActionBuilder[E, AnyContent](requestHandler[E](environment), bodyParser)
}

/**
 * Play module for providing the user-aware action components.
 */
class UserAwareActionModule extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration) = {
    Seq(
      bind[UserAwareAction].to[DefaultUserAwareAction],
      bind[UserAwareRequestHandler].to[DefaultUserAwareRequestHandler]
    )
  }
}

/**
 * Injection helper for user-aware action components
 */
trait UserAwareActionComponents {

  def userAwareBodyParser: BodyParsers.Default

  lazy val userAwareRequestHandler: UserAwareRequestHandler = new DefaultUserAwareRequestHandler()
  lazy val userAwareAction: UserAwareAction = new DefaultUserAwareAction(userAwareRequestHandler, userAwareBodyParser)
}
