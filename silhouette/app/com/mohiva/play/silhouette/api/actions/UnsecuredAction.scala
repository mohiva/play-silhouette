/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
 * Request handler builder implementation to provide the foundation for unsecured request handlers.
 *
 * @param environment The environment instance to handle the request.
 * @param errorHandler The instance of the unsecured error handler.
 * @tparam E The type of the environment.
 */
case class UnsecuredRequestHandlerBuilder[E <: Env](
  environment: Environment[E],
  errorHandler: UnsecuredErrorHandler)
  extends RequestHandlerBuilder[E, Request] {

  /**
   * Creates an unsecured action handler builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return An unsecured action handler builder with a new error handler in place.
   */
  def apply(errorHandler: UnsecuredErrorHandler) = UnsecuredRequestHandlerBuilder[E](environment, errorHandler)

  /**
   * Invokes the block.
   *
   * @param block The block of code to invoke.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  override def invokeBlock[B, T](block: Request[B] => Future[HandlerResult[T]])(implicit request: Request[B]) = {
    handleAuthentication.flatMap {
      // A user is authenticated. The request will be forbidden
      case (Some(authenticator), Some(identity)) =>
        environment.eventBus.publish(NotAuthorizedEvent(identity, request))
        handleBlock(authenticator, _ => errorHandler.onNotAuthorized.map(r => HandlerResult(r)))
      // An authenticator but no user was found. The request will be granted and the authenticator will be discarded
      case (Some(authenticator), None) =>
        block(request).flatMap {
          case hr @ HandlerResult(pr, _) =>
            environment.authenticatorService.discard(authenticator.extract, pr).map(r => hr.copy(r))
        }
      // No authenticator and no user was found. The request will be granted
      case _ => block(request)
    }
  }
}

/**
 * An unsecured request handler.
 *
 * A handler which intercepts requests and checks if there is no authenticated user.
 * If there is none, the execution continues and the enclosed code is invoked.
 *
 * If the user is authenticated, the request is forwarded to
 * the [[com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler.onNotAuthorized]] method.
 */
trait UnsecuredRequestHandler {

  /**
   * The instance of the unsecured error handler.
   */
  val errorHandler: UnsecuredErrorHandler

  /**
   * Applies the environment to the request handler stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return An unsecured request handler builder.
   */
  def apply[E <: Env](environment: Environment[E]): UnsecuredRequestHandlerBuilder[E]
}

/**
 * Default implementation of the [[UnsecuredRequestHandler]].
 *
 * @param errorHandler The instance of the unsecured error handler.
 */
class DefaultUnsecuredRequestHandler @Inject() (val errorHandler: UnsecuredErrorHandler)
  extends UnsecuredRequestHandler {

  /**
   * Applies the environment to the request handler stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return A unsecured request handler builder.
   */
  override def apply[E <: Env](environment: Environment[E]) =
    UnsecuredRequestHandlerBuilder[E](environment, errorHandler)
}

/**
 * Action builder implementation to provide the foundation for unsecured actions.
 *
 * @param requestHandler The request handler instance.
 * @param parser         The body parser.
 * @tparam E The type of the environment.
 * @tparam P The type of the request body.
 */
case class UnsecuredActionBuilder[E <: Env, P](
  requestHandler: UnsecuredRequestHandlerBuilder[E],
  parser: BodyParser[P]
) extends ActionBuilder[Request, P] {

  /**
   * Creates a unsecured action builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return A unsecured action builder.
   */
  def apply(errorHandler: UnsecuredErrorHandler) = UnsecuredActionBuilder[E, P](requestHandler(errorHandler), parser)

  /**
   * Invokes the block.
   *
   * @param request The current request.
   * @param block   The block of code to invoke.
   * @tparam B The type of the request body.
   * @return A handler result.
   */
  override def invokeBlock[B](request: Request[B], block: Request[B] => Future[Result]) = {
    implicit val ec = executionContext
    implicit val req = request
    val b = (r: Request[B]) => block(r).map(r => HandlerResult(r))

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
 * An action based on the [[UnsecuredRequestHandler]].
 */
trait UnsecuredAction {

  /**
   * The instance of the unsecured request handler.
   */
  val requestHandler: UnsecuredRequestHandler

  /**
   * The default body parser.
   */
  val bodyParser: BodyParsers.Default

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return An unsecured action builder.
   */
  def apply[E <: Env](environment: Environment[E]): UnsecuredActionBuilder[E, AnyContent]
}

/**
 * Default implementation of the [[UnsecuredAction]].
 *
 * @param requestHandler The instance of the unsecured request handler.
 * @param bodyParser     The default body parser.
 */
class DefaultUnsecuredAction @Inject() (
  val requestHandler: UnsecuredRequestHandler,
  val bodyParser: BodyParsers.Default
) extends UnsecuredAction {

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam E The type of the environment.
   * @return An unsecured action builder.
   */
  override def apply[E <: Env](environment: Environment[E]) =
    UnsecuredActionBuilder[E, AnyContent](requestHandler[E](environment), bodyParser)
}

/**
 * Error handler for unsecured actions.
 */
trait UnsecuredErrorHandler extends NotAuthorizedErrorHandler

/**
 * Default implementation of the [[UnsecuredErrorHandler]].
 *
 * @param messagesApi The Play messages API.
 */
class DefaultUnsecuredErrorHandler @Inject() (val messagesApi: MessagesApi)
  extends UnsecuredErrorHandler
  with DefaultNotAuthorizedErrorHandler

/**
 * Play module for providing the unsecured action components.
 */
class UnsecuredActionModule extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration) = {
    Seq(
      bind[UnsecuredAction].to[DefaultUnsecuredAction],
      bind[UnsecuredRequestHandler].to[DefaultUnsecuredRequestHandler]
    )
  }
}

/**
 * Play module to provide the unsecured error handler component.
 *
 * We provide an extra module so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other unsecured action module.
 */
class UnsecuredErrorHandlerModule extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration) = {
    Seq(
      bind[UnsecuredErrorHandler].to[DefaultUnsecuredErrorHandler]
    )
  }
}

/**
 * Injection helper for unsecured action components
 */
trait UnsecuredActionComponents {

  def unsecuredBodyParser: BodyParsers.Default
  def unsecuredErrorHandler: UnsecuredErrorHandler

  lazy val unsecuredRequestHandler: UnsecuredRequestHandler = new DefaultUnsecuredRequestHandler(unsecuredErrorHandler)
  lazy val unsecuredAction: UnsecuredAction = new DefaultUnsecuredAction(unsecuredRequestHandler, unsecuredBodyParser)
}

/**
 * Injection helper for unsecured error handler component.
 *
 * We provide an extra component so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other secured action component.
 */
trait UnsecuredErrorHandlerComponents {

  def messagesApi: MessagesApi

  lazy val unsecuredErrorHandler: UnsecuredErrorHandler = new DefaultUnsecuredErrorHandler(messagesApi)
}
