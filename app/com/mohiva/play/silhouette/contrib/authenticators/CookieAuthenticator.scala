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
package com.mohiva.play.silhouette.contrib.authenticators

import scala.concurrent.Future
import org.joda.time.DateTime
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ Cookie, DiscardingCookie, RequestHeader, Result }
import com.mohiva.play.silhouette.contrib.authenticators.CookieAuthenticatorService._
import com.mohiva.play.silhouette.core.services.AuthenticatorService
import com.mohiva.play.silhouette.core.utils.{ CacheLayer, Clock, IDGenerator }
import com.mohiva.play.silhouette.core.{ Authenticator, Identity, Logger, LoginInfo }

/**
 * An authenticator that uses a cookie based approach. It works by storing an ID in a cookie to track
 * the authenticated user and a server side backing store that maps the ID to an authenticator instance.
 *
 * Note: If deploying to multiple nodes the backing store will need to synchronize.
 *
 * @param id The authenticator ID.
 * @param loginInfo The user ID.
 * @param lastUsedDate The last used timestamp.
 * @param expirationDate The expiration time.
 * @param idleTimeout The time in seconds an authenticator can be idle before it timed out.
 */
case class CookieAuthenticator(
    id: String,
    loginInfo: LoginInfo,
    lastUsedDate: DateTime,
    expirationDate: DateTime,
    idleTimeout: Int) extends Authenticator {

  /**
   * Checks if the authenticator isn't expired and isn't timed out.
   *
   * @return True if the authenticator isn't expired and isn't timed out.
   */
  def isValid = !isExpired && !isTimedOut

  /**
   * Checks if the authenticator is expired. This is an absolute timeout since the creation of
   * the authenticator.
   *
   * @return True if the authenticator is expired, false otherwise.
   */
  private def isExpired = expirationDate.isBeforeNow

  /**
   * Checks if the time elapsed since the last time the authenticator was used is longer than
   * the maximum idle timeout specified in the properties.
   *
   * @return True if the authenticator timed out, false otherwise.
   */
  private def isTimedOut = lastUsedDate.plusMinutes(idleTimeout).isBeforeNow
}

/**
 * The service that handles the cookie authenticator.
 *
 * @param settings The cookie settings.
 * @param cacheLayer The cache layer implementation.
 * @param idGenerator The ID generator used to create the authenticator ID.
 * @param clock The clock implementation.
 */
class CookieAuthenticatorService(
  settings: CookieAuthenticatorSettings,
  cacheLayer: CacheLayer,
  idGenerator: IDGenerator,
  clock: Clock)
    extends AuthenticatorService[CookieAuthenticator] with Logger {

  /**
   * Creates a new authenticator ID for the specified identity.
   *
   * @param identity The identity for which the ID should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create[I <: Identity](identity: I)(implicit request: RequestHeader) = {
    idGenerator.generate.map { id =>
      val now = clock.now
      CookieAuthenticator(
        id = id,
        loginInfo = identity.loginInfo,
        lastUsedDate = now,
        expirationDate = now.plusSeconds(settings.authenticatorExpiry),
        idleTimeout = settings.authenticatorIdleTimeout)
    }
  }

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  def retrieve(implicit request: RequestHeader) = {
    request.cookies.get(settings.cookieName) match {
      case Some(cookie) => cacheLayer.get[CookieAuthenticator](cookie.value)
      case None => Future.successful(None)
    }
  }

  /**
   * Stores the authenticator in cache and sends the authenticator cookie to the client.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def init(authenticator: CookieAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    cacheLayer.set(authenticator.id, authenticator).flatMap {
      case Some(a) => result.map(_.withCookies(Cookie(
        name = settings.cookieName,
        value = authenticator.id,
        maxAge = settings.cookieMaxAge,
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie)))
      case None =>
        logger.error(CacheError.format(authenticator))
        result
    }
  }

  /**
   * Updates the authenticator in cache.
   *
   * The cookie gets persisted on the client, so we have not to send it on every subsequent request.
   *
   * @param authenticator The authenticator to update.
   * @param result A function which gets the updated authenticator and returns the original result.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def update(authenticator: CookieAuthenticator, result: CookieAuthenticator => Future[Result])(implicit request: RequestHeader) = {
    cacheLayer.set(authenticator.id, authenticator.copy(lastUsedDate = clock.now)).flatMap {
      case Some(a) => result(a)
      case None =>
        logger.error(CacheError.format(authenticator))
        result(authenticator)
    }
  }

  /**
   * Discards the cookie and remove the authenticator from cache.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def discard(authenticator: CookieAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    cacheLayer.remove(authenticator.id)
    result.map(_.discardingCookies(DiscardingCookie(
      name = settings.cookieName,
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie)))
  }
}

/**
 * The companion object of the authenticator service.
 */
object CookieAuthenticatorService {

  /**
   * The error messages.
   */
  val CacheError = "[Silhouette][CookieAuthenticator] Could not cache authenticator: %s"
}

/**
 * The settings for the cookie authenticator.
 *
 * @param cookieName The cookie name.
 * @param cookiePath The cookie path.
 * @param cookieDomain The cookie domain.
 * @param secureCookie Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnlyCookie Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param cookieMaxAge The cookie expiration date in seconds, `None` for a transient cookie. Defaults to 12 hours.
 * @param authenticatorIdleTimeout The time in seconds an authenticator can be idle before it timed out. Defaults to 30 minutes.
 * @param authenticatorExpiry The expiry of the authenticator in minutes. Defaults to 12 hours.
 */
case class CookieAuthenticatorSettings(
  cookieName: String = "id",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = Play.isProd, // Default to sending only for HTTPS in production, but not for development and test.
  httpOnlyCookie: Boolean = true,
  cookieMaxAge: Option[Int] = Some(12 * 60 * 60),
  authenticatorIdleTimeout: Int = 30 * 60,
  authenticatorExpiry: Int = 12 * 60 * 60)
