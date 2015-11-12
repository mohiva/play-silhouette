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

import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService }
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, Base64, Clock, FingerprintGenerator }
import com.mohiva.play.silhouette.api.util.JsonFormats._
import com.mohiva.play.silhouette.api.{ Authenticator, ExpirableAuthenticator, Logger, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticatorService._
import org.joda.time.DateTime
import play.api.http.HeaderNames
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.{ Cookies, RequestHeader, Result, Session }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
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
 * @param lastUsedDateTime The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout The duration an authenticator can be idle before it timed out.
 * @param fingerprint Maybe a fingerprint of the user.
 */
case class SessionAuthenticator(
  loginInfo: LoginInfo,
  lastUsedDateTime: DateTime,
  expirationDateTime: DateTime,
  idleTimeout: Option[FiniteDuration],
  fingerprint: Option[String])
  extends Authenticator with ExpirableAuthenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  override type Value = Session
}

/**
 * The companion object of the authenticator.
 */
object SessionAuthenticator extends Logger {

  /**
   * Converts the SessionAuthenticator to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[SessionAuthenticator]

  /**
   * Serializes the authenticator.
   *
   * @param authenticator The authenticator to serialize.
   * @param settings The authenticator settings.
   * @return The serialized authenticator.
   */
  def serialize(authenticator: SessionAuthenticator)(settings: SessionAuthenticatorSettings) = {
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
   * @param settings The authenticator settings.
   * @return Some authenticator on success, otherwise None.
   */
  def unserialize(str: String)(settings: SessionAuthenticatorSettings): Try[SessionAuthenticator] = {
    if (settings.encryptAuthenticator) buildAuthenticator(Crypto.decryptAES(str))
    else buildAuthenticator(Base64.decode(str))
  }

  /**
   * Builds the authenticator from Json.
   *
   * @param str The string representation of the authenticator.
   * @return Some authenticator on success, otherwise None.
   */
  private def buildAuthenticator(str: String): Try[SessionAuthenticator] = {
    Try(Json.parse(str)) match {
      case Success(json) => json.validate[SessionAuthenticator].asEither match {
        case Left(error) => Failure(new AuthenticatorException(InvalidJsonFormat.format(ID, error)))
        case Right(authenticator) => Success(authenticator)
      }
      case Failure(error) => Failure(new AuthenticatorException(JsonParseError.format(ID, str), error))
    }
  }
}

/**
 * The service that handles the session authenticator.
 *
 * @param settings The authenticator settings.
 * @param fingerprintGenerator The fingerprint generator implementation.
 * @param clock The clock implementation.
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class SessionAuthenticatorService(
  settings: SessionAuthenticatorSettings,
  fingerprintGenerator: FingerprintGenerator,
  clock: Clock)(implicit val executionContext: ExecutionContext)
  extends AuthenticatorService[SessionAuthenticator]
  with Logger {

  import SessionAuthenticator._

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo)(implicit request: RequestHeader): Future[SessionAuthenticator] = {
    Future.fromTry(Try {
      val now = clock.now
      SessionAuthenticator(
        loginInfo = loginInfo,
        lastUsedDateTime = now,
        expirationDateTime = now + settings.authenticatorExpiry,
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
   * @param request The request to retrieve the authenticator from.
   * @tparam B The type of the request body.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  override def retrieve[B](implicit request: ExtractableRequest[B]): Future[Option[SessionAuthenticator]] = {
    Future.fromTry(Try {
      if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
    }).map { fingerprint =>
      request.session.get(settings.sessionKey).flatMap { value =>
        unserialize(value)(settings) match {
          case Success(authenticator) if fingerprint.isDefined && authenticator.fingerprint != fingerprint =>
            logger.info(InvalidFingerprint.format(ID, fingerprint, authenticator))
            None
          case Success(authenticator) => Some(authenticator)
          case Failure(error) =>
            logger.info(error.getMessage, error)
            None
        }
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
  override def init(authenticator: SessionAuthenticator)(implicit request: RequestHeader): Future[Session] = {
    Future.successful(request.session + (settings.sessionKey -> serialize(authenticator)(settings)))
  }

  /**
   * Embeds the user session into the result.
   *
   * @param session The session to embed.
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  override def embed(session: Session, result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {
    Future.successful(AuthenticatorResult(result.addingToSession(session.data.toSeq: _*)))
  }

  /**
   * Overrides the user session in request.
   *
   * @param session The session to embed.
   * @param request The request header.
   * @return The manipulated request header.
   */
  override def embed(session: Session, request: RequestHeader): RequestHeader = {
    val sessionCookie = Session.encodeAsCookie(session)
    val cookies = Cookies.mergeCookieHeader(request.headers.get(HeaderNames.COOKIE).getOrElse(""), Seq(sessionCookie))
    val additional = Seq(HeaderNames.COOKIE -> cookies)
    request.copy(headers = request.headers.replace(additional: _*))
  }

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  override def touch(authenticator: SessionAuthenticator): Either[SessionAuthenticator, SessionAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) {
      Left(authenticator.copy(lastUsedDateTime = clock.now))
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
  override def update(authenticator: SessionAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    Future.fromTry(Try {
      AuthenticatorResult(result.addingToSession(settings.sessionKey -> serialize(authenticator)(settings)))
    }.recover {
      case e => throw new AuthenticatorUpdateException(UpdateError.format(ID, authenticator), e)
    })
  }

  /**
   * Renews an authenticator.
   *
   * The old authenticator needn't be revoked because we use a stateless approach here. So only
   * one authenticator can be bound to a user session. This method doesn't embed the the authenticator
   * into the result. This must be done manually if needed or use the other renew method otherwise.
   *
   * @param authenticator The authenticator to renew.
   * @param request The request header.
   * @return The serialized expression of the authenticator.
   */
  override def renew(authenticator: SessionAuthenticator)(
    implicit request: RequestHeader): Future[Session] = {

    create(authenticator.loginInfo).flatMap(init).recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Renews an authenticator and replaces the authenticator in session with a new one.
   *
   * The old authenticator needn't be revoked because we use a stateless approach here. So only
   * one authenticator can be bound to a user session.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  override def renew(authenticator: SessionAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    renew(authenticator).flatMap(v => embed(v, result)).recover {
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
  override def discard(authenticator: SessionAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    Future.fromTry(Try {
      AuthenticatorResult(result.removingFromSession(settings.sessionKey))
    }.recover {
      case e => throw new AuthenticatorDiscardingException(DiscardError.format(ID, authenticator), e)
    })
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
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The duration an authenticator expires after it was created.
 */
case class SessionAuthenticatorSettings(
  sessionKey: String = "authenticator",
  encryptAuthenticator: Boolean = true,
  useFingerprinting: Boolean = true,
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12 hours)
