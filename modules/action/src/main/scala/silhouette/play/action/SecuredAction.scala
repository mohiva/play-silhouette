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
import silhouette.authorization.Authorization
import silhouette.play.http.PlayRequestPipeline.fromPlayRequest
import silhouette.{ Credentials, Identity, LoginInfo }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A request that only allows access if an identity is authenticated and authorized.
 *
 * @param identity    The identity implementation.
 * @param loginInfo   The login info for which the identity was found.
 * @param credentials The credentials found in the request.
 * @param request The current request.
 * @tparam I The type of the identity.
 * @tparam B The type of the request body.
 */
case class SecuredRequest[I <: Identity, B](
  identity: I,
  loginInfo: LoginInfo,
  credentials: Credentials,
  request: Request[B]
) extends WrappedRequest(request)

/**
 * Request handler builder implementation to provide the foundation for secured request handlers.
 *
 * @param environment   The environment instance to handle the request.
 * @param errorHandler  The instance of the secured error handler.
 * @param authorization Maybe an authorization instance.
 * @tparam I The type of the identity.
 */
case class SecuredRequestHandlerBuilder[I <: Identity](
  environment: Environment[I],
  errorHandler: SecuredErrorHandler[I],
  authorization: Option[Authorization[I, AuthorizationContext]]
) extends RequestHandlerBuilder[I, ({ type R[P] = SecuredRequest[I, P] })#R] { // scalastyle:ignore structural.type

  /**
   * Creates a secured action handler builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return A secured action handler builder with a new error handler in place.
   */
  def apply(errorHandler: SecuredErrorHandler[I]): SecuredRequestHandlerBuilder[I] =
    SecuredRequestHandlerBuilder[I](environment, errorHandler, authorization)

  /**
   * Creates a secured action handler builder with an authorization in place.
   *
   * @param authorization An authorization object that checks if the user is authorized to invoke the action.
   * @return A secured action handler builder with an authorization in place.
   */
  def apply(authorization: Authorization[I, AuthorizationContext]): SecuredRequestHandlerBuilder[I] =
    SecuredRequestHandlerBuilder[I](environment, errorHandler, Some(authorization))

  /**
   * Invokes the block.
   *
   * @param block   The block of code to invoke.
   * @param request The current request.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  override def invokeBlock[A, T](block: SecuredRequest[I, A] => Future[HandlerResult[T]])(
    implicit
    request: Request[A]
  ): Future[HandlerResult[T]] = {
    handleAuthentication(request).flatMap {
      case Some((identity, credentials, loginInfo)) =>
        val context = AuthorizationContext(loginInfo, credentials, request)
        authorization.map(_.isAuthorized(identity, context)).getOrElse(Future.successful(true)).flatMap {
          // A user is both authenticated and authorized. The request will be granted
          case true =>
            environment.eventBus.publish(AuthenticatedEvent(identity, request))
            block(SecuredRequest(identity, loginInfo, credentials, request))
          // A user is authenticated but not authorized. The request will be forbidden
          case false =>
            environment.eventBus.publish(NotAuthorizedEvent(identity, request))
            errorHandler.onNotAuthorized(identity)(request).map(r => HandlerResult(r))
        }

      // A user isn't authenticated. The request will ask for authentication
      case None =>
        environment.eventBus.publish(NotAuthenticatedEvent(request))
        errorHandler.onNotAuthenticated(request).map(r => HandlerResult(r))
    }
  }
}

/**
 * A secured request handler.
 *
 * A handler which intercepts requests and checks if there is an authenticated user.
 * If there is one, the execution continues and the enclosed code is invoked.
 *
 * If the user is not authenticated or not authorized, the request is forwarded to the
 * [[SecuredErrorHandler.onNotAuthenticated]] or the [[SecuredErrorHandler.onNotAuthorized]] methods.
 */
trait SecuredRequestHandler {

  /**
   * Applies the environment and the error handler to the request handler stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the secured error handler.
   * @tparam I The type of the identity.
   * @return A secured request handler builder.
   */
  def apply[I <: Identity, B](
    environment: Environment[I],
    errorHandler: SecuredErrorHandler[I]
  ): SecuredRequestHandlerBuilder[I]
}

/**
 * Default implementation of the [[SecuredRequestHandler]].
 */
case class DefaultSecuredRequestHandler()
  extends SecuredRequestHandler {

  /**
   * Applies the environment and the error handler to the request handler stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the secured error handler.
   * @tparam I The type of the identity.
   * @return A secured request handler builder.
   */
  override def apply[I <: Identity, B](
    environment: Environment[I],
    errorHandler: SecuredErrorHandler[I]
  ): SecuredRequestHandlerBuilder[I] =
    SecuredRequestHandlerBuilder[I](environment, errorHandler, None)
}

/**
 * Action builder implementation to provide the foundation for secured actions.
 *
 * @param requestHandler The request handler instance.
 * @param parser         The body parser.
 * @tparam I The type of the identity.
 * @tparam B The type of the request body.
 */
case class SecuredActionBuilder[I <: Identity, B](
  requestHandler: SecuredRequestHandlerBuilder[I],
  parser: BodyParser[B]
) extends ActionBuilder[({ type R[A] = SecuredRequest[I, A] })#R, B] { // scalastyle:ignore structural.type
  self =>

  /**
   * Creates a secured action builder with a new error handler in place.
   *
   * @param errorHandler An error handler instance.
   * @return A secured action builder.
   */
  def apply(errorHandler: SecuredErrorHandler[I]): SecuredActionBuilder[I, B] =
    SecuredActionBuilder[I, B](requestHandler(errorHandler), parser)

  /**
   * Creates a secured action builder with an authorization in place.
   *
   * @param authorization An authorization object that checks if the user is authorized to invoke the action.
   * @return A secured action builder.
   */
  def apply(authorization: Authorization[I, AuthorizationContext]): SecuredActionBuilder[I, B] =
    SecuredActionBuilder[I, B](requestHandler(authorization), parser)

  /**
   * Invokes the block.
   *
   * @param request The current request.
   * @param block   The block of code to invoke.
   * @tparam A The type of the request body.
   * @return A handler result.
   */
  override def invokeBlock[A](request: Request[A], block: SecuredRequest[I, A] => Future[Result]): Future[Result] = {
    implicit val ec: ExecutionContext = executionContext
    val b = (r: SecuredRequest[I, A]) => block(r).map(r => HandlerResult(r))

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
 * An action based on the [[SecuredRequestHandler]].
 *
 * @tparam B The type of the request body.
 */
trait SecuredAction[B] {

  /**
   * The instance of the secured request handler.
   */
  val requestHandler: SecuredRequestHandler

  /**
   * The body parser.
   */
  val bodyParser: BodyParser[B]

  /**
   * Applies the environment and the error handler to the action stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the secured error handler.
   * @tparam I The type of the environment.
   * @return A secured action builder.
   */
  def apply[I <: Identity](
    environment: Environment[I],
    errorHandler: SecuredErrorHandler[I]
  ): SecuredActionBuilder[I, B]
}

/**
 * Default implementation of the [[SecuredAction]].
 *
 * This action uses the default body parser that can parse all the content as defined in [[AnyContent]].
 *
 * @param requestHandler The instance of the secured request handler.
 * @param bodyParser     The default body parser.
 */
case class DefaultSecuredAction @Inject() (
  requestHandler: SecuredRequestHandler,
  bodyParser: BodyParsers.Default
) extends SecuredAction[AnyContent] {

  /**
   * Applies the environment and the error handler to the action stack.
   *
   * @param environment  The environment instance to handle the request.
   * @param errorHandler The instance of the secured error handler.
   * @tparam I The type of the identity.
   * @return A secured action builder.
   */
  override def apply[I <: Identity](
    environment: Environment[I],
    errorHandler: SecuredErrorHandler[I]
  ): SecuredActionBuilder[I, AnyContent] =
    SecuredActionBuilder[I, AnyContent](requestHandler(environment, errorHandler), bodyParser)
}

/**
 * Error handler for secured actions.
 *
 * @tparam I The type of the identity.
 */
trait SecuredErrorHandler[I <: Identity] extends NotAuthenticatedErrorHandler with NotAuthorizedErrorHandler[I]

/**
 * Default implementation of the [[SecuredErrorHandler]].
 *
 * @param messagesApi The Play messages API.
 * @tparam I The type of the identity.
 */
case class DefaultSecuredErrorHandler[I <: Identity] @Inject() (messagesApi: MessagesApi)
  extends SecuredErrorHandler[I]
  with DefaultNotAuthenticatedErrorHandler
  with DefaultNotAuthorizedErrorHandler[I]

/**
 * Play module for providing the secured action components.
 */
class SecuredActionModule extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[SecuredAction[AnyContent]].to[DefaultSecuredAction],
      bind[SecuredRequestHandler].to[DefaultSecuredRequestHandler]
    )
  }
}

/**
 * Play module to provide the secured error handler component.
 *
 * We provide an extra module so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other secured action module.
 *
 * @tparam I The type of the identity.
 */
class SecuredErrorHandlerModule[I <: Identity] extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[SecuredErrorHandler[I]].to[DefaultSecuredErrorHandler[I]]
    )
  }
}

/**
 * Injection helper for secured action components.
 */
trait SecuredActionComponents {
  def securedBodyParser: BodyParsers.Default

  lazy val securedRequestHandler: SecuredRequestHandler = DefaultSecuredRequestHandler()
  lazy val securedAction: SecuredAction[AnyContent] = DefaultSecuredAction(securedRequestHandler, securedBodyParser)
}

/**
 * Injection helper for secured error handler component.
 *
 * We provide an extra component so that it can be easily replaced with a custom implementation
 * without to declare bindings for the other secured action component.
 *
 * @tparam I The type of the identity.
 */
trait SecuredErrorHandlerComponents[I <: Identity] {
  def messagesApi: MessagesApi

  lazy val securedErrorHandler: SecuredErrorHandler[I] = DefaultSecuredErrorHandler(messagesApi)
}
