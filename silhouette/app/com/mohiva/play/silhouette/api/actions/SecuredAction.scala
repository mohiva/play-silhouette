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
import play.api.i18n.MessagesApi
import play.api.inject.Module
import play.api.mvc._
import play.api.{ Configuration, Environment => PlayEnv }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A request header that only allows access if an identity is authenticated and authorized.
 *
 * @tparam E The type of the environment.
 */
trait SecuredRequestHeader[E <: Env] extends RequestHeader {
  /**
   * @return The identity implementation.
   */
  def identity: E#I

  /**
   * @return The authenticator implementation.
   */
  def authenticator: E#A
}

/**
 * A request that only allows access if an identity is authenticated and authorized.
 *
 * @tparam E The type of the environment.
 * @tparam B The type of the request body.
 */
trait SecuredRequest[E <: Env, +B] extends Request[B] with SecuredRequestHeader[E]

object SecuredRequest {
  /**
   * A request that only allows access if an identity is authenticated and authorized.
   *
   * @param identity      The identity implementation.
   * @param authenticator The authenticator implementation.
   * @param request The current request.
   * @tparam E The type of the environment.
   * @tparam B The type of the request body.
   */
  def apply[E <: Env, B](identity: E#I, authenticator: E#A, request: Request[B]): SecuredRequest[E, B] = {
    new DefaultSecuredRequest(identity, authenticator, request)
  }

  /**
   * Unapply method for secured request.
   *
   * @param securedRequest the secured request.
   * @tparam E The type of the environment.
   * @tparam B The type of the request body.
   */
  def unapply[E <: Env, B](securedRequest: SecuredRequest[E, B]): Option[(E#I, E#A, Request[B])] = {
    securedRequest match {
      case dsr: DefaultSecuredRequest[E, B] =>
        Some((dsr.identity, dsr.authenticator, dsr.request))
      case sr: SecuredRequest[E, B] =>
        Some((sr.identity, sr.authenticator, sr))
    }
  }
}

class DefaultSecuredRequest[E <: Env, B](
  val identity: E#I,
  val authenticator: E#A,
  val request: Request[B]
) extends WrappedRequest(request) with SecuredRequest[E, B]

/**
 * Request handler builder implementation to provide the foundation for secured request handlers.
 *
 * @param environment   The environment instance to handle the request.
 * @param errorHandler  The instance of the secured error handler.
 * @param authorization Maybe an authorization instance.
 * @tparam E The type of the environment.
 */
case class SecuredRequestHandlerBuilder[E <: Env](
  environment: Environment[E],
  errorHandler: SecuredErrorHandler,
  authorization: Option[Authorization[E#I, E#A]])
  extends RequestHandlerBuilder[E, ({ type R[B] = SecuredRequest[E, B] })#R] {

  /**
   * Creates a secured action handler builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return A secured action handler builder with a new error handler in place.
   */
  def apply(errorHandler: SecuredErrorHandler) =
    SecuredRequestHandlerBuilder[E](environment, errorHandler, authorization)

  /**
   * Creates a secured action handler builder with an authorization in place.
   *
   * @param authorization An authorization object that checks if the user is authorized to invoke the action.
   * @return A secured action handler builder with an authorization in place.
   */
  def apply(authorization: Authorization[E#I, E#A]) =
    SecuredRequestHandlerBuilder[E](environment, errorHandler, Some(authorization))

  /**
   * Invokes the block.
   *
   * @param block   The block of code to invoke.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  override def invokeBlock[B, T](block: SecuredRequest[E, B] => Future[HandlerResult[T]])(
    implicit
    request: Request[B]
  ): Future[HandlerResult[T]] = {
    withAuthorization(handleAuthentication).flatMap {
      // A user is both authenticated and authorized. The request will be granted
      case (Some(authenticator), Some(identity), Some(authorized)) if authorized =>
        environment.eventBus.publish(AuthenticatedEvent(identity, request))
        handleBlock(authenticator, a => block(SecuredRequest(identity, a, request)))
      // A user is authenticated but not authorized. The request will be forbidden
      case (Some(authenticator), Some(identity), _) =>
        environment.eventBus.publish(NotAuthorizedEvent(identity, request))
        handleBlock(authenticator, _ => errorHandler.onNotAuthorized.map(r => HandlerResult(r)))
      // An authenticator but no user was found. The request will ask for authentication and the authenticator will be discarded
      case (Some(authenticator), None, _) =>
        environment.eventBus.publish(NotAuthenticatedEvent(request))
        for {
          result <- errorHandler.onNotAuthenticated
          discardedResult <- environment.authenticatorService.discard(authenticator.extract, result)
        } yield HandlerResult(discardedResult)
      // No authenticator and no user was found. The request will ask for authentication
      case _ =>
        environment.eventBus.publish(NotAuthenticatedEvent(request))
        errorHandler.onNotAuthenticated.map(r => HandlerResult(r))
    }
  }

  /**
   * Adds the authorization status to the authentication result.
   *
   * @param result  The authentication result.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The authentication result with the additional authorization status.
   */
  private def withAuthorization[B](result: Future[(Option[Either[E#A, E#A]], Option[E#I])])(implicit request: Request[B]) = {
    result.flatMap {
      case (Some(a), Some(i)) =>
        authorization.map(_.isAuthorized(i, a.extract)).getOrElse(Future.successful(true)).map(b => (Some(a), Some(i), Some(b)))
      case (a, i) =>
        Future.successful((a, i, None))
    }
  }
}

/**
 * A secured request handler.
 *
 * A handler which intercepts requests and checks if there is an authenticated user.
 * If there is one, the execution continues and the enclosed code is invoked.
 *
 * If the user is not authenticated or not authorized, the request is forwarded to
 * the [[com.mohiva.play.silhouette.api.actions.SecuredErrorHandler.onNotAuthenticated]] or
 * the [[com.mohiva.play.silhouette.api.actions.SecuredErrorHandler.onNotAuthorized]] methods.
 */
trait SecuredRequestHandler {

  /**
   * The instance of the secured error handler.
   */
  val errorHandler: SecuredErrorHandler

  /**
   * Applies the environment to the request handler stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A secured request handler builder.
   */
  def apply[E <: Env](environment: Environment[E]): SecuredRequestHandlerBuilder[E]
}

/**
 * Default implementation of the [[SecuredRequestHandler]].
 *
 * @param errorHandler The instance of the secured error handler.
 */
class DefaultSecuredRequestHandler @Inject() (val errorHandler: SecuredErrorHandler)
  extends SecuredRequestHandler {

  /**
   * Applies the environment to the request handler stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A secured request handler builder.
   */
  override def apply[E <: Env](environment: Environment[E]) =
    SecuredRequestHandlerBuilder[E](environment, errorHandler, None)
}

/**
 * Action builder implementation to provide the foundation for secured actions.
 *
 * @param requestHandler The request handler instance.
 * @param parser         The body parser.
 * @tparam E The type of the environment.
 * @tparam P The type of the request body.
 */
case class SecuredActionBuilder[E <: Env, P](
  requestHandler: SecuredRequestHandlerBuilder[E],
  parser: BodyParser[P]
) extends ActionBuilder[({ type R[B] = SecuredRequest[E, B] })#R, P] {

  /**
   * Creates a secured action builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return A secured action builder.
   */
  def apply(errorHandler: SecuredErrorHandler) = SecuredActionBuilder[E, P](requestHandler(errorHandler), parser)

  /**
   * Creates a secured action builder with an authorization in place.
   *
   * @param authorization An authorization object that checks if the user is authorized to invoke the action.
   * @return A secured action builder.
   */
  def apply(authorization: Authorization[E#I, E#A]) = SecuredActionBuilder[E, P](requestHandler(authorization), parser)

  /**
   * Invokes the block.
   *
   * @param request The current request.
   * @param block   The block of code to invoke.
   * @tparam B The type of the request body.
   * @return A handler result.
   */
  override def invokeBlock[B](request: Request[B], block: SecuredRequest[E, B] => Future[Result]) = {
    implicit val ec = executionContext
    implicit val req = request
    val b = (r: SecuredRequest[E, B]) => block(r).map(r => HandlerResult(r))

    requestHandler(request)(b).map(_.result).recoverWith(requestHandler.errorHandler.exceptionHandler)
  }

  /**
   * Get the execution context to run the request in.
   *
   * @return The execution context.
   */
  override protected def executionContext: ExecutionContext = requestHandler.executionContext
}

/**
 * An action based on the [[SecuredRequestHandler]].
 */
trait SecuredAction {

  /**
   * The instance of the secured request handler.
   */
  val requestHandler: SecuredRequestHandler

  /**
   * The default body parser.
   */
  val bodyParser: BodyParsers.Default

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A secured action builder.
   */
  def apply[E <: Env](environment: Environment[E]): SecuredActionBuilder[E, AnyContent]
}

/**
 * Default implementation of the [[SecuredAction]].
 *
 * @param requestHandler The instance of the secured request handler.
 * @param bodyParser     The default body parser.
 */
class DefaultSecuredAction @Inject() (
  val requestHandler: SecuredRequestHandler,
  val bodyParser: BodyParsers.Default
) extends SecuredAction {

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A secured action builder.
   */
  override def apply[E <: Env](environment: Environment[E]) =
    SecuredActionBuilder[E, AnyContent](requestHandler[E](environment), bodyParser)
}

/**
 * Error handler for secured actions.
 */
trait SecuredErrorHandler extends NotAuthenticatedErrorHandler with NotAuthorizedErrorHandler {

  /**
   * Exception handler which chains the exceptions handlers from the sub types.
   *
   * @param request The request header.
   * @return A partial function which maps an exception to a Play result.
   */
  override def exceptionHandler(implicit request: RequestHeader): PartialFunction[Throwable, Future[Result]] = {
    super[NotAuthenticatedErrorHandler].exceptionHandler orElse
      super[NotAuthorizedErrorHandler].exceptionHandler
  }
}

/**
 * Default implementation of the [[SecuredErrorHandler]].
 *
 * @param messagesApi The Play messages API.
 */
class DefaultSecuredErrorHandler @Inject() (val messagesApi: MessagesApi)
  extends SecuredErrorHandler
  with DefaultNotAuthenticatedErrorHandler
  with DefaultNotAuthorizedErrorHandler {

  /**
   * Exception handler which chains the exceptions handlers from the sub types.
   *
   * @param request The request header.
   * @return A partial function which maps an exception to a Play result.
   */
  override def exceptionHandler(implicit request: RequestHeader): PartialFunction[Throwable, Future[Result]] = {
    super[DefaultNotAuthenticatedErrorHandler].exceptionHandler orElse
      super[DefaultNotAuthorizedErrorHandler].exceptionHandler
  }
}

/**
 * Play module for providing the secured action components.
 */
class SecuredActionModule extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration) = {
    Seq(
      bind[SecuredAction].to[DefaultSecuredAction],
      bind[SecuredRequestHandler].to[DefaultSecuredRequestHandler]
    )
  }
}

/**
 * Play module to provide the secured error handler component.
 *
 * We provide an extra module so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other secured action module.
 */
class SecuredErrorHandlerModule extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration) = {
    Seq(
      bind[SecuredErrorHandler].to[DefaultSecuredErrorHandler]
    )
  }
}

/**
 * Injection helper for secured action components
 */
trait SecuredActionComponents {

  def securedErrorHandler: SecuredErrorHandler
  def securedBodyParser: BodyParsers.Default

  lazy val securedRequestHandler: SecuredRequestHandler = new DefaultSecuredRequestHandler(securedErrorHandler)
  lazy val securedAction: SecuredAction = new DefaultSecuredAction(securedRequestHandler, securedBodyParser)
}

/**
 * Injection helper for secured error handler component.
 *
 * We provide an extra component so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other secured action component.
 */
trait SecuredErrorHandlerComponents {

  def messagesApi: MessagesApi

  lazy val securedErrorHandler: SecuredErrorHandler = new DefaultSecuredErrorHandler(messagesApi)
}
