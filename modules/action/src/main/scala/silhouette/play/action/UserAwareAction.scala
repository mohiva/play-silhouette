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
import play.api.inject.{ Binding, Module }
import play.api.mvc._
import play.api.{ Configuration, Environment => PlayEnv }
import silhouette._
import silhouette.play.http.PlayRequestPipeline.fromPlayRequest

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A request that adds maybe the identity and maybe the authenticator for the current call.
 *
 * @param identity    Some identity implementation if authentication was successful, None otherwise.
 * @param loginInfo   Some login info for which the identity was found if authentication was successful, None otherwise.
 * @param credentials Some credentials if authentication was successful, None otherwise.
 * @param request The current request.
 * @tparam I The type of the identity.
 * @tparam B The type of the request body.
 */
case class UserAwareRequest[I <: Identity, +B](
  identity: Option[I],
  loginInfo: Option[LoginInfo],
  credentials: Option[Credentials],
  request: Request[B]
) extends WrappedRequest(request)

/**
 * Request handler builder implementation to provide the foundation for user-aware request handlers.
 *
 * @param environment The environment instance to handle the request.
 * @tparam I The type of the identity.
 */
case class UserAwareRequestHandlerBuilder[I <: Identity](environment: Environment[I])
  extends RequestHandlerBuilder[I, ({ type R[B] = UserAwareRequest[I, B] })#R] { // scalastyle:ignore structural.type

  /**
   * Invokes the block.
   *
   * @param block   The block of code to invoke.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  override def invokeBlock[B, T](block: UserAwareRequest[I, B] => Future[HandlerResult[T]])(
    implicit
    request: Request[B]
  ): Future[HandlerResult[T]] = {
    handleAuthentication(request).flatMap {
      case Some(Authenticated(identity, credentials, loginInfo)) =>
        block(UserAwareRequest(Some(identity), Some(loginInfo), Some(credentials), request))
      case None =>
        block(UserAwareRequest(None, None, None, request))
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
   * @tparam I The type of the identity.
   * @return A user-aware request handler builder.
   */
  def apply[I <: Identity](environment: Environment[I]): UserAwareRequestHandlerBuilder[I]
}

/**
 * Default implementation of the [[UserAwareRequestHandler]].
 */
case class DefaultUserAwareRequestHandler() extends UserAwareRequestHandler {

  /**
   * Applies the environment to the request handler stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam I The type of the identity.
   * @return A user-aware request handler builder.
   */
  override def apply[I <: Identity](environment: Environment[I]): UserAwareRequestHandlerBuilder[I] =
    UserAwareRequestHandlerBuilder[I](environment)
}

/**
 * Action builder implementation to provide the foundation for user-aware actions.
 *
 * @param requestHandler The request handler instance.
 * @param parser         The body parser.
 * @tparam I The type of the identity.
 * @tparam P The type of the request body.
 */
case class UserAwareActionBuilder[I <: Identity, P](
  requestHandler: UserAwareRequestHandlerBuilder[I],
  parser: BodyParser[P]
) extends ActionBuilder[({ type R[B] = UserAwareRequest[I, B] })#R, P] { // scalastyle:ignore structural.type

  /**
   * Invokes the block.
   *
   * @param request The current request.
   * @param block   The block of code to invoke.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def invokeBlock[B](request: Request[B], block: UserAwareRequest[I, B] => Future[Result]): Future[Result] = {
    implicit val ec: ExecutionContext = executionContext
    requestHandler(request) { r =>
      block(r).map(r => HandlerResult(r))
    }.map(_.result)
  }

  /**
   * Get the execution context to run the request in.
   *
   * @return The execution context.
   */
  override protected def executionContext: ExecutionContext = requestHandler.ec
}

/**
 * An action based on the [[UserAwareRequestHandler]].
 *
 * @tparam B The type of the request body.
 */
trait UserAwareAction[B] {

  /**
   * The instance of the user-aware request handler.
   */
  val requestHandler: UserAwareRequestHandler

  /**
   * The body parser.
   */
  val bodyParser: BodyParser[B]

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam I The type of the identity.
   * @return A user-aware action builder.
   */
  def apply[I <: Identity](environment: Environment[I]): UserAwareActionBuilder[I, B]
}

/**
 * Default implementation of the [[UserAwareAction]].
 *
 * @param requestHandler The instance of the user-aware request handler.
 * @param bodyParser     The default body parser.
 * @tparam B The type of the request body.
 */
case class DefaultUserAwareAction[B] @Inject() (
  requestHandler: UserAwareRequestHandler,
  bodyParser: BodyParser[B]
) extends UserAwareAction[B] {

  /**
   * Applies the environment to the action stack.
   *
   * @param environment The environment instance to handle the request.
   * @tparam I The type of the identity.
   * @return A user-aware action builder.
   */
  override def apply[I <: Identity](environment: Environment[I]): UserAwareActionBuilder[I, B] =
    UserAwareActionBuilder[I, B](requestHandler[I](environment), bodyParser)
}

/**
 * Play module for providing the user-aware action components.
 *
 * @tparam B The type of the request body.
 */
class UserAwareActionModule[B] extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[UserAwareAction[B]].to[DefaultUserAwareAction[B]],
      bind[UserAwareRequestHandler].to[DefaultUserAwareRequestHandler]
    )
  }
}

/**
 * Injection helper for user-aware action components
 *
 * @tparam B The type of the request body.
 */
trait UserAwareActionComponents[B] {

  def userAwareBodyParser: BodyParser[B]

  lazy val userAwareRequestHandler: UserAwareRequestHandler = DefaultUserAwareRequestHandler()

  lazy val userAwareAction: UserAwareAction[B] =
    DefaultUserAwareAction[B](userAwareRequestHandler, userAwareBodyParser)
}
