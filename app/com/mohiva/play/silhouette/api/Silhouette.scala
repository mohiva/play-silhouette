/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.api

import com.mohiva.play.silhouette.api.exceptions.{ AccessDeniedException, AuthenticationException }
import com.mohiva.play.silhouette.api.util.DefaultActionHandler
import play.api.Play
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future
import scala.language.higherKinds

/**
 * Provides the actions that can be used to protect controllers and retrieve the current user
 * if available.
 *
 * {{{
 * class MyController(env: Environment[User, CookieAuthenticator])
 *   extends Silhouette[User, CookieAuthenticator] {
 *
 *   def protectedAction = SecuredAction { implicit request =>
 *     Ok("Hello %s".format(request.identity.fullName))
 *   }
 * }
 * }}}
 *
 * @tparam I The type of the identity.
 * @tparam A The type of the authenticator.
 */
trait Silhouette[I <: Identity, A <: Authenticator] extends Controller with Logger {

  /**
   * Provides an `extract` method on an `Either` which contains the same types.
   */
  private implicit class ExtractEither[T](r: Either[T, T]) {
    def extract: T = r.fold(identity, identity)
  }

  /**
   * Gets the environment needed to instantiate a Silhouette controller.
   *
   * @return The environment needed to instantiate a Silhouette controller.
   */
  protected def env: Environment[I, A]

  /**
   * Implement this to return a result when the user is not authenticated.
   *
   * As defined by RFC 2616, the status code of the response should be 401 Unauthorized.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  protected def notAuthenticated(request: RequestHeader): Option[Future[Result]] = None

  /**
   * Implement this to return a result when the user is authenticated but not authorized.
   *
   * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  protected def notAuthorized(request: RequestHeader): Option[Future[Result]] = None

  /**
   * Default exception handler for silhouette exceptions which translates an exception into
   * the appropriate result.
   *
   * Translates an AccessDeniedException into a 403 Forbidden result and an AuthenticationException
   * into a 401 Unauthorized result.
   *
   * @param request The request header.
   * @return The result to send to the client based on the exception.
   */
  protected def exceptionHandler(implicit request: RequestHeader): PartialFunction[Throwable, Future[Result]] = {
    case e: AccessDeniedException => handleNotAuthorized
    case e: AuthenticationException => handleNotAuthenticated
  }

  /**
   * Produces a result indicating that the request will be forbidden
   * because the authenticated user is not authorized to perform the requested action.
   *
   * This should be called when the user is authenticated but authorization failed.
   * This indicates a permanent situation. Repeating the request with the same authenticated
   * user will produce the same response.
   *
   * As defined by RFC 2616, the status code of the response will be 403 Forbidden.
   *
   * @param request The request header.
   * @return The result to send to the client if the user isn't authorized.
   */
  private def handleNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    logger.debug("[Silhouette] Unauthorized user trying to access '%s'".format(request.uri))

    notAuthorized(request).orElse {
      Play.current.global match {
        case s: SecuredSettings => s.onNotAuthorized(request, request2lang)
        case _ => None
      }
    }.getOrElse(DefaultActionHandler.handleForbidden)
  }

  /**
   * Produces a result indicating that the user must provide authentication before
   * the requested action can be performed.
   *
   * This should be called when the user is not authenticated.
   * This indicates a temporary condition. The user can authenticate and repeat the request.
   *
   * As defined by RFC 2616, the status code of the response will be 401 Unauthorized.
   *
   * @param request The request header.
   * @return The result to send to the client if the user isn't authenticated.
   */
  private def handleNotAuthenticated(implicit request: RequestHeader): Future[Result] = {
    logger.debug("[Silhouette] Unauthenticated user trying to access '%s'".format(request.uri))

    notAuthenticated(request).orElse {
      Play.current.global match {
        case s: SecuredSettings => s.onNotAuthenticated(request, request2lang)
        case _ => None
      }
    }.getOrElse(DefaultActionHandler.handleUnauthorized)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Base implementations for request handlers
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * A result which can transport a result as also additional data through the request handler process.
   *
   * @param result A Play Framework result.
   * @param data Additional data to transport in the result.
   * @tparam T The type of the data.
   */
  case class HandlerResult[+T](result: Result, data: Option[T] = None)

  /**
   * A builder for building request handlers.
   */
  trait RequestHandlerBuilder[+R[_]] {

    /**
     * Constructs a request handler with default content.
     *
     * @param block The block of code to invoke.
     * @param request The current request.
     * @tparam T The type of the data included in the handler result.
     * @return A handler result.
     */
    final def apply[T](block: R[AnyContent] => Future[HandlerResult[T]])(implicit request: Request[AnyContent]): Future[HandlerResult[T]] = {
      invokeBlock(block)
    }

    /**
     * Constructs a request handler with the content of the given request.
     *
     * @param request The current request.
     * @param block The block of code to invoke.
     * @tparam B The type of the request body.
     * @tparam T The type of the data included in the handler result.
     * @return A handler result.
     */
    final def apply[B, T](request: Request[B])(block: R[B] => Future[HandlerResult[T]]): Future[HandlerResult[T]] = {
      invokeBlock(block)(request)
    }

    /**
     * Invoke the block.
     *
     * This is the main method that an request handler has to implement.
     *
     * @param request The current request.
     * @param block The block of code to invoke.
     * @tparam B The type of the request body.
     * @tparam T The type of the data included in the handler result.
     * @return A handler result.
     */
    protected def invokeBlock[B, T](block: R[B] => Future[HandlerResult[T]])(implicit request: Request[B]): Future[HandlerResult[T]]

    /**
     * Handles a block for an authenticator.
     *
     * @param authenticator The authenticator to handle.
     * @param block The block to handle with the authenticator.
     * @param request The current request header.
     * @return A handler result.
     */
    protected def handleBlock[T](authenticator: A, block: A => Future[HandlerResult[T]])(implicit request: RequestHeader) = {
      val auth = env.authenticatorService.touch(authenticator)
      block(auth.extract).flatMap {
        case hr @ HandlerResult(pr: Authenticator.Discard, _) =>
          env.authenticatorService.discard(auth.extract, Future.successful(pr)).map(pr => hr.copy(pr))
        case hr @ HandlerResult(pr: Authenticator.Renew, _) =>
          env.authenticatorService.renew(auth.extract, Future.successful(pr)).map(pr => hr.copy(pr))
        case hr @ HandlerResult(pr, _) => auth match {
          // Authenticator was touched so we update the authenticator and maybe the result
          case Left(a) => env.authenticatorService.update(a, Future.successful(pr)).map(pr => hr.copy(pr))
          // Authenticator was not touched so we return the original result
          case Right(a) => Future.successful(hr)
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Implementations for secured actions and requests
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * A request that only allows access if an identity is authorized.
   *
   * @param identity The identity implementation.
   * @param authenticator The authenticator implementation.
   * @param request The current request.
   * @tparam B The type of the request body.
   */
  case class SecuredRequest[B](identity: I, authenticator: A, request: Request[B]) extends WrappedRequest(request)

  /**
   * Handles secured requests.
   *
   * @param authorize An Authorize object that checks if the user is authorized to invoke the handler.
   */
  class SecuredRequestHandlerBuilder(authorize: Option[Authorization[I]] = None) extends RequestHandlerBuilder[SecuredRequest] {

    /**
     * Invokes the block.
     *
     * @param request The current request.
     * @param block The block of code to invoke.
     * @tparam B The type of the request body.
     * @tparam T The type of the data included in the handler result.
     * @return A handler result.
     */
    protected def invokeBlock[B, T](block: SecuredRequest[B] => Future[HandlerResult[T]])(implicit request: Request[B]): Future[HandlerResult[T]] = {
      env.authenticatorService.retrieve.flatMap {
        // A valid authenticator was found. We try to find the identity for it.
        case Some(authenticator) if authenticator.isValid =>
          env.identityService.retrieve(authenticator.loginInfo).flatMap {
            // A user is both authenticated and authorized. The request will be granted.
            case Some(identity) if authorize.isEmpty || authorize.get.isAuthorized(identity) =>
              env.eventBus.publish(AuthenticatedEvent(identity, request, request2lang))
              handleBlock(authenticator, a => block(SecuredRequest(identity, a, request)))
            // A user is authenticated but not authorized. The request will be forbidden.
            case Some(identity) =>
              env.eventBus.publish(NotAuthorizedEvent(identity, request, request2lang))
              handleBlock(authenticator, _ => handleNotAuthorized(request).map(r => HandlerResult(r)))
            // No user was found. The request will ask for authentication and the authenticator will be discarded.
            case None =>
              env.eventBus.publish(NotAuthenticatedEvent(request, request2lang))
              env.authenticatorService.discard(authenticator, handleNotAuthenticated(request)).map(r => HandlerResult(r))
          }
        // An invalid authenticator was found. The request will ask for authentication and the authenticator will be discarded.
        case Some(authenticator) if !authenticator.isValid =>
          env.eventBus.publish(NotAuthenticatedEvent(request, request2lang))
          env.authenticatorService.discard(authenticator, handleNotAuthenticated(request)).map(r => HandlerResult(r))
        // No authenticator was found. The request will ask for authentication.
        case None =>
          env.eventBus.publish(NotAuthenticatedEvent(request, request2lang))
          handleNotAuthenticated(request).map(r => HandlerResult(r))
      }
    }
  }

  /**
   * A secured request handler.
   */
  object SecuredRequestHandler extends SecuredRequestHandlerBuilder {

    /**
     * Creates a secured action handler.
     *
     * @return A secured action handler.
     */
    def apply() = new SecuredRequestHandlerBuilder(None)

    /**
     * Creates a secured action handler.
     *
     * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
     * @return A secured action handler.
     */
    def apply(authorize: Authorization[I]) = new SecuredRequestHandlerBuilder(Some(authorize))
  }

  /**
   * A builder for secured actions.
   *
   * Requests are subject to authentication logic and, optionally, authorization.
   * HTTP status codes 401 (Unauthorized) and 403 (Forbidden) will be returned when appropriate.
   *
   * For reference see:
   * [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html RFC 2616]],
   * [[http://danielirvine.com/blog/2011/07/18/understanding-403-forbidden/ Understanding 403 Forbidden]],
   * [[http://stackoverflow.com/questions/3297048/403-forbidden-vs-401-unauthorized-http-responses/6937030#6937030 403 Forbidden vs 401 Unauthorized HTTP responses]].
   *
   * @param authorize An Authorize object that checks if the user is authorized to invoke the action.y
   */
  class SecuredActionBuilder(authorize: Option[Authorization[I]] = None) extends ActionBuilder[SecuredRequest] {

    /**
     * Invokes the block.
     *
     * @param request The current request.
     * @param block The block of code to invoke.
     * @tparam B The type of the request body.
     * @return A handler result.
     */
    def invokeBlock[B](request: Request[B], block: SecuredRequest[B] => Future[Result]) = {
      val b = (r: SecuredRequest[B]) => block(r).map(r => HandlerResult(r))
      (authorize match {
        case Some(a) => SecuredRequestHandler(a)(request)(b)
        case None => SecuredRequestHandler(request)(b)
      }).map(_.result).recoverWith(exceptionHandler(request))
    }
  }

  /**
   * A secured action.
   *
   * If the user is not authenticated or not authorized, the request is forwarded to
   * the [[com.mohiva.play.silhouette.api.Silhouette.notAuthenticated]] or
   * the [[com.mohiva.play.silhouette.api.Silhouette.notAuthorized]] methods.
   *
   * If these methods are not implemented, then
   * the [[com.mohiva.play.silhouette.api.SecuredSettings.onNotAuthenticated]] or
   * the [[com.mohiva.play.silhouette.api.SecuredSettings.onNotAuthorized]] methods
   * will be called as fallback.
   *
   * If the [[com.mohiva.play.silhouette.api.SecuredSettings]] trait isn't implemented,
   * a default message will be displayed.
   */
  object SecuredAction extends SecuredActionBuilder {

    /**
     * Creates a secured action.
     *
     * @return A secured action builder.
     */
    def apply() = new SecuredActionBuilder(None)

    /**
     * Creates a secured action.
     *
     * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
     * @return A secured action builder.
     */
    def apply(authorize: Authorization[I]) = new SecuredActionBuilder(Some(authorize))
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Implementations for user aware actions and requests
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * A request that adds the identity and the authenticator for the current call.
   *
   * @param identity Some identity implementation if authentication was successful, None otherwise.
   * @param authenticator Some authenticator implementation if authentication was successful, None otherwise.
   * @param request The current request.
   * @tparam B The type of the request body.
   */
  case class UserAwareRequest[B](identity: Option[I], authenticator: Option[A], request: Request[B]) extends WrappedRequest(request)

  /**
   * An handler that adds the current user in the request if it's available.
   */
  object UserAwareRequestHandler extends RequestHandlerBuilder[UserAwareRequest] {

    /**
     * Invokes the block.
     *
     * @param block The block of code to invoke.
     * @param request The current request.
     * @tparam B The type of the request body.
     * @tparam T The type of the data included in the handler result.
     * @return A handler result.
     */
    protected def invokeBlock[B, T](block: UserAwareRequest[B] => Future[HandlerResult[T]])(implicit request: Request[B]) = {
      env.authenticatorService.retrieve.flatMap {
        // An valid authenticator was found. We try to find the identity for it.
        case Some(authenticator) if authenticator.isValid =>
          env.identityService.retrieve(authenticator.loginInfo).flatMap { identity =>
            handleBlock(authenticator, a => block(UserAwareRequest(identity, Some(a), request)))
          }
        // An invalid authenticator was found. The authenticator will be discarded.
        case Some(authenticator) if !authenticator.isValid =>
          block(UserAwareRequest(None, None, request)).flatMap {
            case hr @ HandlerResult(pr, d) =>
              env.authenticatorService.discard(authenticator, Future.successful(pr)).map(r => hr.copy(pr))
          }
        // No authenticator was found.
        case None =>
          block(UserAwareRequest(None, None, request))
      }
    }
  }

  /**
   * An action that adds the current user in the request if it's available.
   */
  object UserAwareAction extends ActionBuilder[UserAwareRequest] {

    /**
     * Invokes the block.
     *
     * @param request The current request.
     * @param block The block of code to invoke.
     * @tparam B The type of the request body.
     * @return The result to send to the client.
     */
    def invokeBlock[B](request: Request[B], block: UserAwareRequest[B] => Future[Result]) = {
      UserAwareRequestHandler(request) { r =>
        block(r).map(r => HandlerResult(r))
      }.map(_.result).recoverWith(exceptionHandler(request))
    }
  }
}
