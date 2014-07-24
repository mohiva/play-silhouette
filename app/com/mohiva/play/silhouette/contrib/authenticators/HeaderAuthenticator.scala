/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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

import org.joda.time.DateTime
import scala.concurrent.Future
import play.api.mvc.{ RequestHeader, Result }
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.contrib.authenticators.HeaderAuthenticatorService._
import com.mohiva.play.silhouette.core.services.AuthenticatorService
import com.mohiva.play.silhouette.core.utils.{ CacheLayer, Clock, IDGenerator }
import com.mohiva.play.silhouette.core.{ Authenticator, Identity, Logger, LoginInfo }

/**
 * An authenticator that uses a header based approach. It works by using a user defined header to track
 * the authenticated user and a server side backing store that maps the header to an authenticator instance.
 *
 * Note: If deploying to multiple nodes the backing store will need to synchronize.
 *
 * @param id The authenticator ID.
 * @param loginInfo The user ID.
 * @param lastUsedDate The last used timestamp.
 * @param expirationDate The expiration time.
 * @param idleTimeout The time in seconds an authenticator can be idle before it timed out.
 */
case class HeaderAuthenticator(
    id: String,
    loginInfo: LoginInfo,
    lastUsedDate: DateTime,
    expirationDate: DateTime,
    idleTimeout: Int) extends Authenticator {

  /**
   * Checks if the authenticator is expired. This is an absolute timeout since the creation of
   * the authenticator.
   *
   * @return True if the authenticator is expired, false otherwise.
   */
  def isExpired = expirationDate.isBeforeNow

  /**
   * Checks if the time elapsed since the last time the authenticator was used is longer than
   * the maximum idle timeout specified in the properties.
   *
   * @return True if the authenticator timed out, false otherwise.
   */
  def isTimedOut = lastUsedDate.plusMinutes(idleTimeout).isBeforeNow

  /**
   * Checks if the authenticator isn't expired and isn't timed out.
   *
   * @return True if the authenticator isn't expired and isn't timed out.
   */
  def isValid = !isExpired && !isTimedOut
}

/**
 * The service that handles the header authenticator.
 *
 * @param settings The authenticator settings.
 * @param cacheLayer The cache layer implementation.
 * @param idGenerator The ID generator used to create the authenticator ID.
 * @param clock The clock implementation.
 */
class HeaderAuthenticatorService(
  settings: HeaderAuthenticatorSettings,
  cacheLayer: CacheLayer,
  idGenerator: IDGenerator,
  clock: Clock)
    extends AuthenticatorService[HeaderAuthenticator] with Logger {

  /**
   * Creates a new authenticator for the specified identity.
   *
   * @param identity The identity for which the ID should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create[I <: Identity](identity: I)(implicit request: RequestHeader) = {
    idGenerator.generate.map { id =>
      val now = clock.now
      HeaderAuthenticator(
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
    request.headers.get(settings.headerName) match {
      case Some(value) => cacheLayer.get[HeaderAuthenticator](value)
      case None => Future.successful(None)
    }
  }

  /**
   * Pushes the authenticator to the client.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def init(authenticator: HeaderAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    cacheLayer.set(authenticator.id, authenticator).flatMap {
      case Some(a) => result.map(_.withHeaders(settings.headerName -> a.id))
      case None =>
        logger.error(CacheError.format(authenticator))
        result
    }
  }

  /**
   * Updates the authenticator with the new last used date in cache.
   *
   * @param authenticator The authenticator to update.
   * @param result A function which gets the updated authenticator and returns the original result.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def update(authenticator: HeaderAuthenticator, result: HeaderAuthenticator => Future[Result])(implicit request: RequestHeader) = {
    cacheLayer.set(authenticator.id, authenticator.copy(lastUsedDate = clock.now)).flatMap {
      case Some(a) => result(a)
      case None =>
        logger.error(CacheError.format(authenticator))
        result(authenticator)
    }
  }

  /**
   * Removes the authenticator from cache.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def discard(authenticator: HeaderAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    cacheLayer.remove(authenticator.id)
    result
  }
}

/**
 * The companion object of the authenticator service.
 */
object HeaderAuthenticatorService {

  /**
   * The error messages.
   */
  val CacheError = "[Silhouette][HeaderAuthenticator] Could not cache authenticator: %s"
}

/**
 * The settings for the header authenticator.
 *
 * @param headerName The header name.
 * @param authenticatorIdleTimeout The time in seconds an authenticator can be idle before it timed out. Defaults to 30 minutes.
 * @param authenticatorExpiry The expiry of the authenticator in minutes. Defaults to 12 hours.
 */
case class HeaderAuthenticatorSettings(
  headerName: String = "X-Auth-Token",
  authenticatorIdleTimeout: Int = 30 * 60,
  authenticatorExpiry: Int = 12 * 60 * 60)
