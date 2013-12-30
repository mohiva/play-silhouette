/**
 * Copyright 2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 * Copyright 2014 Christian Kaps (christian.kaps at mohiva dot com)
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
 *
 * This file contains source code from the Secure Social project:
 * http://securesocial.ws/
 */
package com.mohiva.play.silhouette.core

import play.api.mvc._
import play.api.i18n.Messages
import play.api.Logger
import play.api.libs.json.Json
import play.api.http.HeaderNames
import scala.concurrent.Future
import scala.Some
import play.api.mvc.SimpleResult
import play.api.libs.oauth.ServiceInfo
import com.mohiva.play.silhouette.core.providers.utils.RoutesHelper

/**
 * A request that adds the User for the current call.
 */
case class SecuredRequest[A](user: Identity, request: Request[A]) extends WrappedRequest(request)

/**
 * A request that adds the User for the current call.
 */
case class RequestWithUser[A](user: Option[Identity], request: Request[A]) extends WrappedRequest(request)

/**
 * Provides the actions that can be used to protect controllers and retrieve the current user
 * if available.
 *
 * object MyController extends Silhouette {
 *    def protectedAction = SecuredAction { implicit request =>
 *      Ok("Hello %s".format(request.user.displayName))
 *    }
 */
trait Silhouette extends Controller {

  /**
   * A Forbidden response for ajax clients.
   *
   * @param request The current request.
   * @tparam A The body content type.
   * @return A play framework result.
   */
  private def ajaxCallNotAuthenticated[A](implicit request: Request[A]): SimpleResult = {
    Unauthorized(Json.toJson(Map("error"->"Credentials required"))).as(JSON)
  }

  /**
   * A Forbidden response for ajax clients.
   *
   * @param request The current request.
   * @tparam A The body content type.
   * @return A play framework result.
   */
  private def ajaxCallNotAuthorized[A](implicit request: Request[A]): SimpleResult = {
    Forbidden( Json.toJson(Map("error" -> "Not authorized"))).as(JSON)
  }

  /**
   * A secured action.
   *
   * If there is no user in the session the request is redirected to the login page.
   */
  object SecuredAction extends SecuredActionBuilder[SecuredRequest[_]] {

    /**
     * Creates a secured action.
     */
    def apply[A]() = new SecuredActionBuilder[A](false, None)

    /**
     * Creates a secured action.
     *
     * @param ajaxCall A boolean indicating whether this is an ajax call or not.
     */
    def apply[A](ajaxCall: Boolean) = new SecuredActionBuilder[A](ajaxCall, None)

    /**
     * Creates a secured action.
     *
     * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
     */
    def apply[A](authorize: Authorization) = new SecuredActionBuilder[A](false, Some(authorize))

    /**
     * Creates a secured action.
     *
     * @param ajaxCall A boolean indicating whether this is an ajax call or not.
     * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
     */
    def apply[A](ajaxCall: Boolean, authorize: Authorization) = new SecuredActionBuilder[A](ajaxCall, Some(authorize))
  }

  /**
   * A builder for secured actions.
   *
   * @param ajaxCall A boolean indicating whether this is an ajax call or not.
   * @param authorize An Authorize object that checks if the user is authorized to invoke the action.
   * @tparam T
   */
  class SecuredActionBuilder[T](ajaxCall: Boolean = false, authorize: Option[Authorization] = None)
    extends ActionBuilder[({ type R[T] = SecuredRequest[T] })#R] {

    def invokeSecuredBlock[A](
      ajaxCall: Boolean,
      authorize: Option[Authorization],
      request: Request[A],
      block: SecuredRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {

      implicit val req = request
      val result = for (
        authenticator <- Silhouette.authenticatorFromRequest ;
        user <- UserService.find(authenticator.identityId)
      ) yield {
        touch(authenticator)
        if ( authorize.isEmpty || authorize.get.isAuthorized(user)) {
          block(SecuredRequest(user, request))
        } else {
          Future.successful {
            if ( ajaxCall ) {
              ajaxCallNotAuthorized(request)
            } else {
              Redirect(RoutesHelper.notAuthorized.absoluteURL(IdentityProvider.sslEnabled))
            }
          }
        }
      }

      result.getOrElse({
        if ( Logger.isDebugEnabled ) {
          Logger.debug("[silhouette] anonymous user trying to access : '%s'".format(request.uri))
        }
        val response = if ( ajaxCall ) {
          ajaxCallNotAuthenticated(request)
        } else {
          Redirect(RoutesHelper.login().absoluteURL(IdentityProvider.sslEnabled))
            .flashing("error" -> Messages("silhouette.loginRequired"))
            .withSession(session + (Silhouette.OriginalUrlKey -> request.uri)
          )
        }
        Future.successful(response.discardingCookies(Authenticator.discardingCookie))
      })
    }

    def invokeBlock[A](request: Request[A], block: SecuredRequest[A] => Future[SimpleResult]) =
       invokeSecuredBlock(ajaxCall, authorize, request, block)
  }

  /**
   * An action that adds the current user in the request if it's available.
   */
  object UserAwareAction extends ActionBuilder[RequestWithUser] {
    protected def invokeBlock[A](
      request: Request[A],
      block: (RequestWithUser[A]) => Future[SimpleResult]): Future[SimpleResult] = {

      implicit val req = request
      val user = for (
        authenticator <- Silhouette.authenticatorFromRequest;
        user <- UserService.find(authenticator.identityId)
      ) yield {
        touch(authenticator)
        user
      }
      block(RequestWithUser(user, request))
    }
  }

  def touch(authenticator: Authenticator) {
    Authenticator.save(authenticator.touch)
  }
}

object Silhouette {
  val OriginalUrlKey = "original-url"

  def authenticatorFromRequest(implicit request: RequestHeader): Option[Authenticator] = {
    val result = for {
      cookie <- request.cookies.get(Authenticator.cookieName) ;
      maybeAuthenticator <- Authenticator.find(cookie.value).fold(e => None, Some(_)) ;
      authenticator <- maybeAuthenticator
    } yield {
      authenticator
    }

    result match {
      case Some(a) => {
        if ( !a.isValid ) {
          Authenticator.delete(a.id)
          None
        } else {
          Some(a)
        }
      }
      case None => None
    }
  }

  /**
   * Get the current logged in user.
   *
   * This method can be used from public actions that need to access the current user if there's any.
   *
   * @param request The request header.withReferrerAsOriginalUrl
   * @return
   */
  def currentUser(implicit request: RequestHeader): Option[Identity] = {
    for (
      authenticator <- authenticatorFromRequest ;
      user <- UserService.find(authenticator.identityId)
    ) yield {
      user
    }
  }

  /**
   * Returns the ServiceInfo needed to sign OAuth1 requests.
   *
   * @param user the user for which the serviceInfo is needed
   * @return an optional service info
   */
  def serviceInfoFor(user: Identity): Option[ServiceInfo] = {
    Registry.providers.get(user.identityId.providerId) match {
      case Some(p: OAuth1Provider) if p.authMethod == AuthenticationMethod.OAuth1 => Some(p.serviceInfo)
      case _ => None
    }
  }

  /**
   * Saves the referrer as original url in the session if it's not yet set.
   *
   * @param result The result that maybe enhanced with an updated session.
   * @return The result that's returned to the client.
   */
  def withReferrerAsOriginalUrl[A](result: Result)(implicit request: Request[A]): Result = {
    request.session.get(OriginalUrlKey) match {
      // If there's already an original url recorded we keep it: e.g. if s.o. goes to
      // login, switches to signup and goes back to login we want to keep the first referrer
      case Some(_) => result
      case None => {
        request.headers.get(HeaderNames.REFERER).map { referrer =>
          // we don't want to use the ful referrer, as then we might redirect from https
          // back to http and loose our session. So let's get the path and query string only
          val idxFirstSlash = referrer.indexOf("/", "https://".length())
          val referrerUri = if (idxFirstSlash < 0) "/" else referrer.substring(idxFirstSlash)
          result.withSession(
            request.session + (OriginalUrlKey -> referrerUri))
        }.getOrElse(result)
      }
    }
  }

  val enableRefererAsOriginalUrl = {
    import play.api.Play
    Play.current.configuration.getBoolean("silhouette.enableReferrerAsOriginalUrl").getOrElse(false)
  }
}
