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
package com.mohiva.play.silhouette.contrib.services

import org.joda.time.DateTime
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.{ Cookie, SimpleResult, DiscardingCookie, RequestHeader }
import com.mohiva.play.silhouette.core.{ Identity, Authenticator, LoginInfo }
import com.mohiva.play.silhouette.core.utils.{ Clock, IDGenerator, CacheLayer }
import com.mohiva.play.silhouette.core.services.AuthenticatorService

/**
 * A default implementation of the AuthenticatorService that uses a cache to store the authenticators.
 *
 * Note: If deploying to multiple nodes the caches will need to synchronize.
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
    extends AuthenticatorService[CookieAuthenticator] {

  /**
   * Creates a new authenticator ID for the specified identity.
   *
   * @param identity The identity for which the ID should be created.
   * @return An authenticator.
   */
  def create[I <: Identity](identity: I) = {
    idGenerator.generate.flatMap { id =>
      val now = clock.now
      val expirationDate = now.plusSeconds(settings.expiry)
      val authenticator = CookieAuthenticator(id, identity.loginInfo, now, expirationDate, settings)
      cacheLayer.set(authenticator.id, authenticator)
    }
  }

  /**
   * Updates an existing authenticator.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator or None if the authenticator couldn't be updated.
   */
  def update(authenticator: CookieAuthenticator) = {
    cacheLayer.set(authenticator.id, authenticator.copy(lastUsedDate = clock.now))
  }

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  def retrieve(implicit request: RequestHeader): Future[Option[CookieAuthenticator]] = {
    request.cookies.get(settings.name) match {
      case Some(cookie) => cacheLayer.get[CookieAuthenticator](cookie.value).map {
        case Some(a) if a.isValid => Some(a)
        case Some(a) => {
          cacheLayer.remove(a.id)
          None
        }
        case None => None
      }
      case None => Future.successful(None)
    }
  }

  /**
   * Sends the authenticator cookie to the client.
   *
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  override def send(authenticator: CookieAuthenticator, result: SimpleResult): SimpleResult = {
    result.withCookies(Cookie(
      name = settings.name,
      value = authenticator.id,
      maxAge = settings.absoluteTimeout,
      path = settings.path,
      domain = settings.domain,
      secure = settings.secure,
      httpOnly = settings.httpOnly
    ))
  }

  /**
   * Discards the cookie.
   *
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  override def discard(result: SimpleResult): SimpleResult = {
    result.discardingCookies(DiscardingCookie(settings.name, settings.path, settings.domain, settings.secure))
  }
}

/**
 * An authenticator tracks an authenticated user.
 *
 * @param id The authenticator ID.
 * @param loginInfo The user ID.
 * @param lastUsedDate The last used timestamp.
 * @param expirationDate The expiration time.
 */
case class CookieAuthenticator(
    id: String,
    loginInfo: LoginInfo,
    lastUsedDate: DateTime,
    expirationDate: DateTime,
    settings: CookieAuthenticatorSettings) extends Authenticator {

  /**
   * Checks if the authenticator has expired. This is an absolute timeout since the creation of
   * the authenticator.
   *
   * @return True if the authenticator has expired, false otherwise.
   */
  def expired = expirationDate.isBeforeNow

  /**
   * Checks if the time elapsed since the last time the authenticator was used is longer than
   * the maximum idle timeout specified in the properties.
   *
   * @return True if the authenticator timed out, false otherwise.
   */
  def timedOut = lastUsedDate.plusMinutes(settings.idleTimeout).isBeforeNow

  /**
   * Checks if the authenticator isn't expired and isn't timed out.
   *
   * @return True if the authenticator isn't expired and isn't timed out.
   */
  def isValid = !expired && !timedOut
}

/**
 * The settings for the cookie authenticator.
 *
 * @param name The cookie name.
 * @param path The cookie path.
 * @param domain The cookie domain.
 * @param secure Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnly Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param idleTimeout The time in seconds a cookie can be idle before it timed out. Defaults to 30 minutes.
 * @param absoluteTimeout The cookie expiration date in seconds, `None` for a transient cookie. Defaults to 12 hours.
 * @param expiry The expiry of the authenticator in minutes. Defaults to 12 hours.
 */
case class CookieAuthenticatorSettings(
  name: String = "id",
  path: String = "/",
  domain: Option[String] = None,
  secure: Boolean = true,
  httpOnly: Boolean = true,
  idleTimeout: Int = 30 * 60,
  absoluteTimeout: Option[Int] = Some(12 * 60 * 60),
  expiry: Int = 12 * 60 * 60)
