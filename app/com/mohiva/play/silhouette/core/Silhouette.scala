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
package com.mohiva.play.silhouette.core

import play.api.Play
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.core.exceptions.{ AuthenticationException, AccessDeniedException }
import com.mohiva.play.silhouette.core.utils.DefaultActionHandler
import scala.concurrent.Future

/**
 * Provides the actions that can be used to protect controllers and retrieve the current user
 * if available.
 *
 * {{{
 * class MyController(env: Environment[User, CachedCookieAuthenticator])
 *   extends Silhouette[User, CachedCookieAuthenticator] {
 *
 *   def protectedAction = SecuredAction { implicit request =>
 *     Ok("Hello %s".format(request.identity.fullName))
 *   }
 * }
 * }}}
 *
 * @tparam I The type of the identity.
 * @tparam T The type of the authenticator.
 */
trait Silhouette[I <: Identity, T <: Authenticator] extends Controller with Logger {

  /**
   * Gets the environment needed to instantiate a Silhouette controller.
   *
   * @return The environment needed to instantiate a Silhouette controller.
   */
  protected def env: Environment[I, T]

  /**
   * Implement this to return a result when the user is not authenticated.
   *
   * As defined by RFC 2616, the status code of the response should be 401 Unauthorized.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  protected def notAuthenticated(request: RequestHeader): Option[Future[SimpleResult]] = None

  /**
   * Implement this to return a result when the user is authenticated but not authorized.
   *
   * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  protected def notAuthorized(request: RequestHeader): Option[Future[SimpleResult]] = None

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
  protected def exceptionHandler(implicit request: RequestHeader): PartialFunction[Throwable, Future[SimpleResult]] = {
    case e: AccessDeniedException => handleNotAuthorized
    case e: AuthenticationException => handleNotAuthenticated
  }

  /**
   * Gets the current logged in identity.
   *
   * This method can be used from public actions that need to access the current user if there's any.
   *
   * @param request The request header.
   * @return The identity if any.
   */
  protected def currentIdentity(implicit request: RequestHeader): Future[Option[I]] = {
    env.authenticatorService.retrieve.flatMap {
      case Some(authenticator) => env.identityService.retrieve(authenticator.loginInfo).map(_.map { identity =>
        env.authenticatorService.update(authenticator)
        identity
      })
      case None => Future.successful(None)
    }
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
  private def handleNotAuthorized(implicit request: RequestHeader): Future[SimpleResult] = {
    logger.debug("[Silhouette] Unauthorized user trying to access '%s'".format(request.uri))

    notAuthorized(request).orElse {
      Play.current.global match {
        case s: SecuredSettings => s.onNotAuthorized(request, lang)
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
  private def handleNotAuthenticated(implicit request: RequestHeader): Future[SimpleResult] = {
    logger.debug("[Silhouette] Unauthenticated user trying to access '%s'".format(request.uri))

    notAuthenticated(request).orElse {
      Play.current.global match {
        case s: SecuredSettings => s.onNotAuthenticated(request, lang)
        case _ => None
      }
    }.getOrElse(DefaultActionHandler.handleUnauthorized)
  }

  /**
   * A request that only allows access if a user is authorized.
   */
  case class SecuredRequest[A](identity: I, request: Request[A]) extends WrappedRequest(request)

  /**
   * A request that adds the User for the current call.
   */
  case class RequestWithUser[A](identity: Option[I], request: Request[A]) extends WrappedRequest(request)

  /**
   * A secured action.
   *
   * If the user is not authenticated or not authorized, the request is forwarded to
   * the [[com.mohiva.play.silhouette.core.Silhouette.notAuthenticated]] or
   * the [[com.mohiva.play.silhouette.core.Silhouette.notAuthorized]] methods.
   *
   * If these methods are not implemented, then
   * the [[com.mohiva.play.silhouette.core.SecuredSettings.onNotAuthenticated]] or
   * the [[com.mohiva.play.silhouette.core.SecuredSettings.onNotAuthorized]] methods
   * will be called as fallback.
   *
   * If the [[com.mohiva.play.silhouette.core.SecuredSettings]] trait isn't implemented,
   * a default message will be displayed.
   */
  object SecuredAction extends SecuredActionBuilder {

    /**
     * Creates a secured action.
     */
    def apply() = new SecuredActionBuilder(None)

    /**
     * Creates a secured action.
     *
     * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
     */
    def apply(authorize: Authorization[I]) = new SecuredActionBuilder(Some(authorize))
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
   * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
   */
  class SecuredActionBuilder(authorize: Option[Authorization[I]] = None) extends ActionBuilder[SecuredRequest] {

    /**
     * Invokes the block.
     *
     * @param request The request.
     * @param block The block of code to invoke.
     * @return The result to send to the client.
     */
    def invokeBlock[A](request: Request[A], block: SecuredRequest[A] => Future[SimpleResult]) = {
      implicit val req = request
      currentIdentity(request).flatMap {
        // A user is both authenticated and authorized. The request will be granted.
        case Some(identity) if authorize.isEmpty || authorize.get.isAuthorized(identity) =>
          env.eventBus.publish(AuthenticatedEvent(identity, req, lang))
          block(SecuredRequest(identity, request))
        // A user is authenticated but not authorized. The request will be forbidden.
        case Some(identity) =>
          env.eventBus.publish(NotAuthorizedEvent(identity, req, lang))
          handleNotAuthorized(request)
        // No user is authenticated. The request will ask for authentication.
        case None =>
          env.eventBus.publish(NotAuthenticatedEvent(req, lang))
          handleNotAuthenticated(request).map(env.authenticatorService.discard)
      }
    }
  }

  /**
   * An action that adds the current user in the request if it's available.
   */
  object UserAwareAction extends ActionBuilder[RequestWithUser] {

    /**
     * Invoke the block.
     *
     * @param request The request.
     * @param block The block of code to invoke.
     * @return The result to send to the client.
     */
    def invokeBlock[A](request: Request[A], block: RequestWithUser[A] => Future[SimpleResult]) = {
      currentIdentity(request).flatMap { identity => block(RequestWithUser(identity, request)) }
    }
  }
}
