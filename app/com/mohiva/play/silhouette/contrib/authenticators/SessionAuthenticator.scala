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

import scala.util.{ Failure, Success, Try }
import scala.concurrent.Future
import org.joda.time.DateTime
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.{ RequestHeader, Result }
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.contrib.authenticators.SessionAuthenticatorService._
import com.mohiva.play.silhouette.core.{ Authenticator, Identity, Logger, LoginInfo }
import com.mohiva.play.silhouette.core.services.AuthenticatorService
import com.mohiva.play.silhouette.core.utils.{ FingerprintGenerator, Clock }

/**
 * An authenticator that uses a stateless, session based approach. It works by storing a serialized authenticator
 * instance in the Play Framework session cookie.
 *
 * @param loginInfo The user ID.
 * @param lastUsedDate The last used timestamp.
 * @param expirationDate The expiration time.
 * @param idleTimeout The time in seconds an authenticator can be idle before it timed out.
 * @param fingerprint Maybe a fingerprint of the user.
 */
case class SessionAuthenticator(
    loginInfo: LoginInfo,
    lastUsedDate: DateTime,
    expirationDate: DateTime,
    idleTimeout: Int,
    fingerprint: Option[String]) extends Authenticator {

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
 * The companion object of the authenticator.
 */
object SessionAuthenticator {

  /**
   * Converts the SessionAuthenticator to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[SessionAuthenticator]
}

/**
 * The service that handles the session authenticator.
 *
 * @param settings The authenticator settings.
 * @param fingerprintGenerator The fingerprint implementation.
 * @param clock The clock implementation.
 */
class SessionAuthenticatorService(
  settings: SessionAuthenticatorSettings,
  fingerprintGenerator: FingerprintGenerator,
  clock: Clock)
    extends AuthenticatorService[SessionAuthenticator] with Logger {

  /**
   * Creates a new authenticator for the specified identity.
   *
   * @param identity The identity for which the ID should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create[I <: Identity](identity: I)(implicit request: RequestHeader) = {
    val now = clock.now
    Future.successful(SessionAuthenticator(
      loginInfo = identity.loginInfo,
      lastUsedDate = now,
      expirationDate = now.plusSeconds(settings.authenticatorExpiry),
      idleTimeout = settings.authenticatorIdleTimeout,
      fingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
    ))
  }

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  def retrieve(implicit request: RequestHeader) = {
    val fingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
    Future.successful(request.session.get(settings.sessionKey).flatMap(unserialize) match {
      case Some(a) if fingerprint.isDefined && a.fingerprint != fingerprint =>
        logger.info(InvalidFingerprint.format(fingerprint, a))
        None
      case Some(a) => Some(a)
      case None => None
    })
  }

  /**
   * Stores the authenticator in the user session.
   *
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  def init(authenticator: SessionAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    result.map(_.withSession(request.session + (settings.sessionKey -> serialize(authenticator))))
  }

  /**
   * Updates the authenticator and store it in the user session.
   *
   * Because of the fact that we store the authenticator client side in the user session, we must update
   * the authenticator in the session on every subsequent request to keep the last used date in sync.
   *
   * @param authenticator The authenticator to update.
   * @param result A function which gets the updated authenticator and returns the original result.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def update(authenticator: SessionAuthenticator, result: SessionAuthenticator => Future[Result])(implicit request: RequestHeader) = {
    val a = authenticator.copy(lastUsedDate = clock.now)
    result(a).map(_.withSession(request.session + (settings.sessionKey -> serialize(a))))
  }

  /**
   * Removes the authenticator from session.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def discard(authenticator: SessionAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    result.map(_.withSession(request.session - settings.sessionKey))
  }

  /**
   * Serializes the authenticator.
   *
   * @param authenticator The authenticator to serialize.
   * @return The serialized authenticator.
   */
  private def serialize(authenticator: SessionAuthenticator) = {
    if (settings.encryptAuthenticator) {
      Crypto.encryptAES(Json.toJson(authenticator).toString())
    } else {
      Json.toJson(authenticator).toString()
    }
  }

  /**
   * Unserializes the authenticator.
   *
   * @param str The string representation of the authenticator.
   * @return Some authenticator on success, otherwise None.
   */
  private def unserialize(str: String): Option[SessionAuthenticator] = {
    if (settings.encryptAuthenticator) buildAuthenticator(Crypto.decryptAES(str))
    else buildAuthenticator(str)
  }

  /**
   * Builds the authenticator from Json.
   *
   * @param str The string representation of the authenticator.
   * @return Some authenticator on success, otherwise None.
   */
  private def buildAuthenticator(str: String): Option[SessionAuthenticator] = {
    Try(Json.parse(str)) match {
      case Success(json) => json.validate[SessionAuthenticator].asEither match {
        case Left(error) =>
          logger.info(InvalidJsonFormat.format(error))
          None
        case Right(authenticator) => Some(authenticator)
      }
      case Failure(error) =>
        logger.info(InvalidJsonFormat.format(error))
        None
    }
  }
}

/**
 * The companion object of the authenticator service.
 */
object SessionAuthenticatorService {

  /**
   * The error messages.
   */
  val InvalidJsonFormat = "[Silhouette][SessionAuthenticator] Invalid Json format: %s"
  val InvalidFingerprint = "[Silhouette][SessionAuthenticator] Fingerprint %s doesn't match authenticator: %s"
}

/**
 * The settings for the session authenticator.
 *
 * @param sessionKey The key of the authenticator in the session.
 * @param encryptAuthenticator Indicates if the authenticator should be encrypted in session.
 * @param useFingerprinting Indicates if a fingerprint of the user should be stored in the authenticator.
 * @param authenticatorIdleTimeout The time in seconds an authenticator can be idle before it timed out. Defaults to 30 minutes.
 * @param authenticatorExpiry The expiry of the authenticator in minutes. Defaults to 12 hours.
 */
case class SessionAuthenticatorSettings(
  sessionKey: String = "authenticator",
  encryptAuthenticator: Boolean = true,
  useFingerprinting: Boolean = true,
  authenticatorIdleTimeout: Int = 30 * 60,
  authenticatorExpiry: Int = 12 * 60 * 60)
