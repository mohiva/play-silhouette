/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.impl.authenticators

import com.mohiva.play.silhouette._
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Base64, Clock, FingerprintGenerator }
import com.mohiva.play.silhouette.api.{ Authenticator, Logger, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticatorService._
import org.joda.time.DateTime
import play.api.http.HeaderNames
import play.api.libs.Crypto
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.{ Cookies, RequestHeader, Result, Session }

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * An authenticator that uses a stateless, session based approach. It works by storing a
 * serialized authenticator instance in the Play Framework session cookie.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property.
 *
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDate The last used timestamp.
 * @param expirationDate The expiration time.
 * @param idleTimeout The time in seconds an authenticator can be idle before it timed out.
 * @param fingerprint Maybe a fingerprint of the user.
 */
case class SessionAuthenticator(
  loginInfo: LoginInfo,
  lastUsedDate: DateTime,
  expirationDate: DateTime,
  idleTimeout: Option[Int],
  fingerprint: Option[String]) extends Authenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  type Value = Session

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
   * @return True if sliding window expiration is activated and the authenticator is timed out, false otherwise.
   */
  private def isTimedOut = idleTimeout.isDefined && lastUsedDate.plusSeconds(idleTimeout.get).isBeforeNow
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
 * @param fingerprintGenerator The fingerprint generator implementation.
 * @param clock The clock implementation.
 */
class SessionAuthenticatorService(
  settings: SessionAuthenticatorSettings,
  fingerprintGenerator: FingerprintGenerator,
  clock: Clock) extends AuthenticatorService[SessionAuthenticator] with Logger {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create(loginInfo: LoginInfo)(implicit request: RequestHeader) = {
    Future.from(Try {
      val now = clock.now
      SessionAuthenticator(
        loginInfo = loginInfo,
        lastUsedDate = now,
        expirationDate = now.plusSeconds(settings.authenticatorExpiry),
        idleTimeout = settings.authenticatorIdleTimeout,
        fingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
      )
    }).recover {
      case e => throw new AuthenticatorCreationException(CreateError.format(ID, loginInfo), e)
    }
  }

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  def retrieve(implicit request: RequestHeader) = {
    Future.from(Try {
      if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
    }).map { fingerprint =>
      request.session.get(settings.sessionKey).flatMap(unserialize) match {
        case Some(a) if fingerprint.isDefined && a.fingerprint != fingerprint =>
          logger.info(InvalidFingerprint.format(ID, fingerprint, a))
          None
        case Some(a) => Some(a)
        case None => None
      }
    }.recover {
      case e => throw new AuthenticatorRetrievalException(RetrieveError.format(ID), e)
    }
  }

  /**
   * Returns a new user session containing the authenticator.
   *
   * @param authenticator The authenticator instance.
   * @param request The request header.
   * @return The serialized authenticator value.
   */
  def init(authenticator: SessionAuthenticator)(implicit request: RequestHeader) = {
    Future.successful(request.session + (settings.sessionKey -> serialize(authenticator)))
  }

  /**
   * Embeds the user session into the result.
   *
   * @param session The session to embed.
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  def embed(session: Session, result: Result)(implicit request: RequestHeader) = {
    Future.successful(result.addingToSession(session.data.toSeq: _*))
  }

  /**
   * Overrides the user session in request.
   *
   * @param session The session to embed.
   * @param request The request header.
   * @return The manipulated request header.
   */
  def embed(session: Session, request: RequestHeader) = {
    val sessionCookie = Session.encodeAsCookie(session)
    val cookies = Cookies.merge(request.headers.get(HeaderNames.COOKIE).getOrElse(""), Seq(sessionCookie))
    val additional = Seq(HeaderNames.COOKIE -> Seq(cookies))
    request.copy(headers = AdditionalHeaders(request.headers, additional))
  }

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  def touch(authenticator: SessionAuthenticator): Either[SessionAuthenticator, SessionAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) {
      Left(authenticator.copy(lastUsedDate = clock.now))
    } else {
      Right(authenticator)
    }
  }

  /**
   * Updates the authenticator and store it in the user session.
   *
   * Because of the fact that we store the authenticator client side in the user session, we must update
   * the authenticator in the session on every subsequent request to keep the last used date in sync.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def update(authenticator: SessionAuthenticator, result: Result)(implicit request: RequestHeader) = {
    Future.from(Try {
      result.addingToSession(settings.sessionKey -> serialize(authenticator))
    }.recover {
      case e => throw new AuthenticatorUpdateException(UpdateError.format(ID, authenticator), e)
    })
  }

  /**
   * Replaces the authenticator in session with a new one. The old authenticator needn't be revoked
   * because we use a stateless approach here. So only one authenticator can be bound to a user session.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def renew(authenticator: SessionAuthenticator, result: Result)(implicit request: RequestHeader) = {
    create(authenticator.loginInfo).flatMap { a =>
      init(a).flatMap(v => embed(v, result))
    }.recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Removes the authenticator from session.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def discard(authenticator: SessionAuthenticator, result: Result)(implicit request: RequestHeader) = {
    Future.from(Try {
      result.removingFromSession(settings.sessionKey)
    }.recover {
      case e => throw new AuthenticatorDiscardingException(DiscardError.format(ID, authenticator), e)
    })
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
      Base64.encode(Json.toJson(authenticator))
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
    else buildAuthenticator(Base64.decode(str))
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
          logger.info(InvalidJsonFormat.format(ID, error))
          None
        case Right(authenticator) => Some(authenticator)
      }
      case Failure(error) =>
        logger.info(JsonParseError.format(ID, str), error)
        None
    }
  }
}

/**
 * The companion object of the authenticator service.
 */
object SessionAuthenticatorService {

  /**
   * The ID of the authenticator.
   */
  val ID = "session-authenticator"

  /**
   * The error messages.
   */
  val JsonParseError = "[Silhouette][%s] Cannot parse Json: %s"
  val InvalidJsonFormat = "[Silhouette][%s] Invalid Json format: %s"
  val InvalidFingerprint = "[Silhouette][%s] Fingerprint %s doesn't match authenticator: %s"
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
  authenticatorIdleTimeout: Option[Int] = Some(30 * 60),
  authenticatorExpiry: Int = 12 * 60 * 60)
