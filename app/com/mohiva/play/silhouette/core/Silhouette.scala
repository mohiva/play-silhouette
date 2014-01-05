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

import play.api.{Play, Logger}
import play.api.mvc._
import play.api.i18n.Messages
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core.services.{AuthenticatorService, IdentityService}

/**
 * Provides the actions that can be used to protect controllers and retrieve the current user
 * if available.
 *
 * class MyController(
 *     val identityService: IdentityService[User],
 *     val authenticatorService: AuthenticatorService
 *   ) extends Silhouette[User] {
 *
 *   def protectedAction = SecuredAction { implicit request =>
 *     Ok("Hello %s".format(request.identity.fullName))
 *   }
 * }
 *
 * @tparam I The type of the identity.
 */
trait Silhouette[I <: Identity] extends Controller {

  /**
   * Gets the identity service implementation.
   *
   * @return The identity service implementation.
   */
  protected def identityService: IdentityService[I]

  /**
   * Gets the authenticator service implementation.
   *
   * @return The authenticator service implementation.
   */
  protected def authenticatorService: AuthenticatorService

  /**
   * Implement this to return a result when the user isn't authenticated.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  protected def notAuthenticated(request: RequestHeader): Option[Future[SimpleResult]] = None

  /**
   * Implement this to return a result when the user isn't authorized.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  protected def notAuthorized(request: RequestHeader): Option[Future[SimpleResult]] = None

  /**
   * Gets the current logged in identity.
   *
   * This method can be used from public actions that need to access the current user if there's any.
   *
   * @param request The request header.
   * @return The identity if any.
   */
  protected def currentIdentity(implicit request: RequestHeader): Future[Option[I]] = {
    authenticatorFromRequest.flatMap {
      case Some(authenticator) => identityService.findByID(authenticator.identityID).map(_.map { identity =>
        authenticatorService.update(authenticator.touch)
        identity
      })
      case None => Future.successful(None)
    }
  }

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  private def authenticatorFromRequest(implicit request: RequestHeader): Future[Option[Authenticator]] = {
    request.cookies.get(Authenticator.cookieName) match {
      case Some(cookie) => authenticatorService.findByID(cookie.value).map {
        case Some(a) if a.isValid => Some(a)
        case Some(a) => {
          authenticatorService.deleteByID(a.id)
          None
        }
        case None => None
      }
      case None => Future.successful(None)
    }
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
   * If there is no identity in the session, the request is forwarded to
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
    def apply(authorize: Authorization) = new SecuredActionBuilder(Some(authorize))
  }

  /**
   * A builder for secured actions.
   *
   * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
   */
  class SecuredActionBuilder(authorize: Option[Authorization] = None) extends ActionBuilder[SecuredRequest] {

    /**
     * Handles the not-authorized result.
     *
     * @param request The request header.
     * @return The result to send to the client if the user isn't authorized.
     */
    def handleNotAuthorized(implicit request: RequestHeader): Future[SimpleResult] = {
      notAuthorized(request).getOrElse {
        Play.current.global match {
          case s: SecuredSettings => s.onNotAuthorized(request, lang)
          case _ => Future.successful(Unauthorized(Messages("silhouette.not.authorized")))
        }
      }
    }

    /**
     * Handles the not-authenticated result.
     *
     * @param request The request header.
     * @return The result to send to the client if the user isn't authenticated.
     */
    def handleNotAuthenticated(implicit request: RequestHeader): Future[SimpleResult] = {
      if (Logger.isDebugEnabled) {
        Logger.debug("[silhouette] anonymous user trying to access : '%s'".format(request.uri))
      }

      notAuthenticated(request).getOrElse {
        Play.current.global match {
          case s: SecuredSettings => s.onNotAuthenticated(request, lang)
          case _ => Future.successful(Forbidden(Messages("silhouette.not.authenticated")))
        }
      }
    }

    /**
     * Invoke the block.
     *
     * @param request The request.
     * @param block The block of code to invoke.
     * @return The result to send to the client.
     */
    def invokeBlock[A](request: Request[A], block: SecuredRequest[A] => Future[SimpleResult]) = {
      currentIdentity(request).flatMap {
        case Some(identity) if authorize.isEmpty || authorize.get.isAuthorized(identity) => {
          block(SecuredRequest(identity, request))
        }
        case Some(identity) => handleNotAuthorized(request)
        case None => handleNotAuthenticated(request)
      }.map(_.discardingCookies(Authenticator.discardingCookie))
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
