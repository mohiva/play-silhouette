/**
 * Licensed to the Minutemen Group under one or more contributor license
 * agreements. See the COPYRIGHT file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silhouette.play.action

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.inject.{ Binding, Module }
import play.api.mvc._
import play.api.{ Configuration, Environment => PlayEnv }
import silhouette.Identity
import silhouette.play.http.PlayRequestPipeline.fromPlayRequest

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Request handler builder implementation to provide the foundation for unsecured request handlers.
 *
 * @param environment  The environment instance to handle the request.
 * @param errorHandler The instance of the unsecured error handler.
 * @tparam I The type of the identity.
 */
case class UnsecuredRequestHandlerBuilder[I <: Identity](
  environment: Environment[I],
  errorHandler: UnsecuredErrorHandler[I]
) extends RequestHandlerBuilder[I, Request] {

  /**
   * Creates an unsecured action handler builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return An unsecured action handler builder with a new error handler in place.
   */
  def apply(errorHandler: UnsecuredErrorHandler[I]): UnsecuredRequestHandlerBuilder[I] =
    UnsecuredRequestHandlerBuilder[I](environment, errorHandler)

  /**
   * Invokes the block.
   *
   * @param block   The block of code to invoke.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  override def invokeBlock[B, T](block: Request[B] => Future[HandlerResult[T]])(
    implicit
    request: Request[B]
  ): Future[HandlerResult[T]] = {
    handleAuthentication(request).flatMap {
      // A user is authenticated. The request will be forbidden
      case Some((identity, _, _)) =>
        environment.eventBus.publish(NotAuthorizedEvent(identity, request))
        errorHandler.onNotAuthorized(identity)(request).map(r => HandlerResult(r))
      // A user isn't authenticated. The request will be granted
      case None =>
        block(request)
    }
  }
}

/**
 * An unsecured request handler.
 *
 * A handler which intercepts requests and checks if there is no authenticated user.
 * If there is none, the execution continues and the enclosed code is invoked.
 *
 * If the user is authenticated, the request is forwarded to the [[UnsecuredErrorHandler.onNotAuthorized]] method.
 */
trait UnsecuredRequestHandler {

  /**
   * Applies the environment and the error handler to the request handler stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the unsecured error handler.
   * @tparam I The type of the identity.
   * @return An unsecured request handler builder.
   */
  def apply[I <: Identity](
    environment: Environment[I],
    errorHandler: UnsecuredErrorHandler[I]
  ): UnsecuredRequestHandlerBuilder[I]
}

/**
 * Default implementation of the [[UnsecuredRequestHandler]].
 */
case class DefaultUnsecuredRequestHandler()
  extends UnsecuredRequestHandler {

  /**
   * Applies the environment and the error handler to the request handler stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the unsecured error handler.
   * @tparam I The type of the identity.
   * @return A unsecured request handler builder.
   */
  override def apply[I <: Identity](
    environment: Environment[I],
    errorHandler: UnsecuredErrorHandler[I]
  ): UnsecuredRequestHandlerBuilder[I] =
    UnsecuredRequestHandlerBuilder[I](environment, errorHandler)
}

/**
 * Action builder implementation to provide the foundation for unsecured actions.
 *
 * @param requestHandler The request handler instance.
 * @param parser         The body parser.
 * @tparam I The type of the identity.
 * @tparam P The type of the request body.
 */
case class UnsecuredActionBuilder[I <: Identity, P](
  requestHandler: UnsecuredRequestHandlerBuilder[I],
  parser: BodyParser[P]
) extends ActionBuilder[Request, P] {

  /**
   * Creates a unsecured action builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return A unsecured action builder.
   */
  def apply(errorHandler: UnsecuredErrorHandler[I]): UnsecuredActionBuilder[I, P] =
    UnsecuredActionBuilder[I, P](requestHandler(errorHandler), parser)

  /**
   * Invokes the block.
   *
   * @param request The current request.
   * @param block   The block of code to invoke.
   * @tparam B The type of the request body.
   * @return A handler result.
   */
  override def invokeBlock[B](request: Request[B], block: Request[B] => Future[Result]): Future[Result] = {
    implicit val ec: ExecutionContext = executionContext
    val b = (r: Request[B]) => block(r).map(r => HandlerResult(r))

    requestHandler(request)(b).map(_.result)
  }

  /**
   * Get the execution context to run the request in.
   *
   * @return The execution context.
   */
  override protected def executionContext: ExecutionContext = requestHandler.ec
}

/**
 * An action based on the [[UnsecuredRequestHandler]].
 *
 * @tparam B The type of the request body.
 */
trait UnsecuredAction[B] {

  /**
   * The instance of the unsecured request handler.
   */
  val requestHandler: UnsecuredRequestHandler

  /**
   * The body parser.
   */
  val bodyParser: BodyParser[B]

  /**
   * Applies the environment and the error handler to the action stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the unsecured error handler.
   * @tparam I The type of the environment.
   * @return An unsecured action builder.
   */
  def apply[I <: Identity](
    environment: Environment[I],
    errorHandler: UnsecuredErrorHandler[I]
  ): UnsecuredActionBuilder[I, B]
}

/**
 * Default implementation of the [[UnsecuredAction]].
 *
 * This action uses the default body parser that can parse all the content as defined in [[AnyContent]].
 *
 * @param requestHandler The instance of the unsecured request handler.
 * @param bodyParser     The default body parser.
 */
case class DefaultUnsecuredAction @Inject() (
  requestHandler: UnsecuredRequestHandler,
  bodyParser: BodyParsers.Default
) extends UnsecuredAction[AnyContent] {

  /**
   * Applies the environment and the error handler to the action stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the unsecured error handler.
   * @tparam I The type of the identity.
   * @return An unsecured action builder.
   */
  override def apply[I <: Identity](
    environment: Environment[I],
    errorHandler: UnsecuredErrorHandler[I]
  ): UnsecuredActionBuilder[I, AnyContent] =
    UnsecuredActionBuilder[I, AnyContent](requestHandler(environment, errorHandler), bodyParser)
}

/**
 * Error handler for unsecured actions.
 *
 * @tparam I The type of the identity.
 */
trait UnsecuredErrorHandler[I <: Identity] extends NotAuthorizedErrorHandler[I]

/**
 * Default implementation of the [[UnsecuredErrorHandler]].
 *
 * @param messagesApi The Play messages API.
 * @tparam I The type of the identity.
 */
case class DefaultUnsecuredErrorHandler[I <: Identity] @Inject() (messagesApi: MessagesApi)
  extends UnsecuredErrorHandler[I]
  with DefaultNotAuthorizedErrorHandler[I]

/**
 * Play module for providing the unsecured action components.
 */
class UnsecuredActionModule extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[UnsecuredAction[AnyContent]].to[DefaultUnsecuredAction],
      bind[UnsecuredRequestHandler].to[DefaultUnsecuredRequestHandler]
    )
  }
}

/**
 * Play module to provide the unsecured error handler component.
 *
 * We provide an extra module so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other unsecured action module.
 *
 * @tparam I The type of the identity.
 */
class UnsecuredErrorHandlerModule[I <: Identity] extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[UnsecuredErrorHandler[I]].to[DefaultUnsecuredErrorHandler[I]]
    )
  }
}

/**
 * Injection helper for unsecured action components.
 */
trait UnsecuredActionComponents {
  def unsecuredBodyParser: BodyParsers.Default

  lazy val unsecuredRequestHandler: UnsecuredRequestHandler = DefaultUnsecuredRequestHandler()
  lazy val unsecuredAction: UnsecuredAction[AnyContent] =
    DefaultUnsecuredAction(unsecuredRequestHandler, unsecuredBodyParser)
}

/**
 * Injection helper for unsecured error handler component.
 *
 * We provide an extra component so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other secured action component.
 *
 * @tparam I The type of the identity.
 */
trait UnsecuredErrorHandlerComponents[I <: Identity] {
  def messagesApi: MessagesApi

  lazy val unsecuredErrorHandler: UnsecuredErrorHandler[I] = DefaultUnsecuredErrorHandler(messagesApi)
}
