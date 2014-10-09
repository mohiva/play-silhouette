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

import com.atlassian.jwt.SigningAlgorithm
import com.atlassian.jwt.core.writer.{ JsonSmartJwtJsonBuilder, NimbusJwtWriterFactory }
import com.mohiva.play.silhouette.contrib.authenticators.JWTAuthenticatorService._
import com.mohiva.play.silhouette.contrib.daos.AuthenticatorDAO
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import com.mohiva.play.silhouette.core.services.AuthenticatorService
import com.mohiva.play.silhouette.core.services.AuthenticatorService._
import com.mohiva.play.silhouette.core.utils.{ Base64, Clock, IDGenerator }
import com.mohiva.play.silhouette.core.{ Authenticator, Logger, LoginInfo }
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import org.joda.time.DateTime
import play.api.libs.Crypto
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.{ RequestHeader, Result }
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * An authenticator that uses a header based based approach with the help of a JWT. It works
 * by using a JWT to transport the authenticator data inside a user defined header. It can
 * be stateless with the disadvantages that the JWT can't be invalidated.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property. If this feature is activated then a new token will be generated on every update.
 * Make sure your application can handle this case.
 *
 * @see http://self-issued.info/docs/draft-ietf-oauth-json-web-token.html#Claims
 * @see https://developer.atlassian.com/static/connect/docs/concepts/understanding-jwt.html
 *
 * @param id The authenticator ID.
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDate The last used timestamp.
 * @param expirationDate The expiration time.
 * @param idleTimeout The time in seconds an authenticator can be idle before it timed out.
 */
case class JWTAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDate: DateTime,
  expirationDate: DateTime,
  idleTimeout: Option[Int])
    extends Authenticator {

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
  private def isTimedOut = idleTimeout.isDefined && lastUsedDate.plusMinutes(idleTimeout.get).isBeforeNow
}

/**
 * The service that handles the JWT authenticator.
 *
 * If the authenticator DAO is deactivated then a stateless approach will be used. But note
 * that you will loose the possibility to invalidate a JWT.
 *
 * @param settings The authenticator settings.
 * @param dao The DAO to store the authenticator. Set it to None to use a stateless approach.
 * @param idGenerator The ID generator used to create the authenticator ID.
 * @param clock The clock implementation.
 */
class JWTAuthenticatorService(
  settings: JWTAuthenticatorSettings,
  dao: Option[AuthenticatorDAO[JWTAuthenticator]],
  idGenerator: IDGenerator,
  clock: Clock)
    extends AuthenticatorService[JWTAuthenticator] with Logger {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create(loginInfo: LoginInfo)(implicit request: RequestHeader) = {
    idGenerator.generate.map { id =>
      val now = clock.now
      JWTAuthenticator(
        id = id,
        loginInfo = loginInfo,
        lastUsedDate = now,
        expirationDate = now.plusSeconds(settings.authenticatorExpiry),
        idleTimeout = settings.authenticatorIdleTimeout
      )
    }.recover {
      case e => throw new AuthenticationException(CreateError.format(ID, loginInfo), e)
    }
  }

  /**
   * Retrieves the authenticator from request.
   *
   * If a backing store is defined, then the authenticator will be validated against it.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  def retrieve(implicit request: RequestHeader) = {
    Future.fromTry(Try(request.headers.get(settings.headerName))).flatMap {
      case Some(token) => unserialize(token) match {
        case Success(authenticator) => dao.fold(Future.successful(Option(authenticator)))(_.find(authenticator.id))
        case Failure(e) =>
          logger.info(e.getMessage, e)
          Future.successful(None)
      }
      case None => Future.successful(None)
    }.recover {
      case e => throw new AuthenticationException(RetrieveError.format(ID), e)
    }
  }

  /**
   * Creates a new JWT for the given authenticator and adds a header with the token as value to the result.
   * If a backing store is defined, then the authenticator will be stored in it.
   *
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  def init(authenticator: JWTAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    dao.fold(Future.successful(authenticator))(_.save(authenticator)).flatMap { a =>
      result.map(_.withHeaders(settings.headerName -> serialize(a)))
    }.recover {
      case e => throw new AuthenticationException(InitError.format(ID, authenticator), e)
    }
  }

  /**
   * Updates the authenticator based on the following settings.
   *
   * If idle timeout is disabled, then we needn't update the token. This prevents the creation of a new
   * token on every request. The token will only embedded into the result, if an update on the authenticator
   * occurred.
   *
   * @param authenticator The authenticator to update.
   * @param result A function which gets the updated authenticator and returns the original result.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def update(authenticator: JWTAuthenticator, result: JWTAuthenticator => Future[Result])(implicit request: RequestHeader) = {
    (authenticator.idleTimeout match {
      // Idle timeout is enabled, so we must update the token
      case Some(timeout) =>
        val a = authenticator.copy(lastUsedDate = clock.now)
        dao.fold(Future.successful(a))(_.save(a)).flatMap { a =>
          result(a).map(_.withHeaders(settings.headerName -> serialize(a)))
        }
      // Idle timeout is disabled, so we needn't update the token
      case None => result(authenticator)
    }).recover {
      case e => throw new AuthenticationException(UpdateError.format(ID, authenticator), e)
    }
  }

  /**
   * Replaces the JWT header with a new one. If a backing store is defined, the old authenticator will
   * be revoked. After that it isn't possible to use a JWT which was bound to this authenticator.
   *
   * @param authenticator The authenticator to update.
   * @param result A function which gets the updated authenticator and returns the original result.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def renew(authenticator: JWTAuthenticator, result: JWTAuthenticator => Future[Result])(implicit request: RequestHeader) = {
    dao.fold(Future.successful(()))(_.remove(authenticator.id)).flatMap { _ =>
      create(authenticator.loginInfo).flatMap { a =>
        init(a, result(a))
      }
    }.recover {
      case e => throw new AuthenticationException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Removes the authenticator from backing store.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def discard(authenticator: JWTAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    dao.fold(Future.successful(()))(_.remove(authenticator.id)).flatMap { _ =>
      result
    }.recover {
      case e => throw new AuthenticationException(DiscardError.format(ID, authenticator), e)
    }
  }

  /**
   * Serializes the authenticator.
   *
   * @param authenticator The authenticator to serialize.
   * @return The serialized authenticator.
   */
  def serialize(authenticator: JWTAuthenticator): String = {
    val subject = Json.toJson(authenticator.loginInfo).toString()
    val jwtBuilder = new JsonSmartJwtJsonBuilder()
      .jwtId(authenticator.id)
      .issuer(settings.issuerClaim)
      .subject(if (settings.encryptSubject) Crypto.encryptAES(subject) else Base64.encode(subject))
      .issuedAt(authenticator.lastUsedDate.getMillis / 1000)
      .expirationTime(authenticator.expirationDate.getMillis / 1000)

    new NimbusJwtWriterFactory()
      .macSigningWriter(SigningAlgorithm.HS256, settings.sharedSecret)
      .jsonToJwt(jwtBuilder.build())
  }

  /**
   * Unserializes the authenticator.
   *
   * @param str The string representation of the authenticator.
   * @return An authenticator on success, otherwise a failure.
   */
  def unserialize(str: String): Try[JWTAuthenticator] = {
    Try {
      val verifier = new MACVerifier(settings.sharedSecret)
      val jwsObject = JWSObject.parse(str)
      if (!jwsObject.verify(verifier)) {
        throw new IllegalArgumentException("Fraudulent JWT token: " + str)
      }

      JWTClaimsSet.parse(jwsObject.getPayload.toJSONObject)
    }.flatMap { c =>
      val subject = if (settings.encryptSubject) Crypto.decryptAES(c.getSubject) else Base64.decode(c.getSubject)
      buildLoginInfo(subject).map { loginInfo =>
        JWTAuthenticator(
          id = c.getJWTID,
          loginInfo = loginInfo,
          lastUsedDate = new DateTime(c.getIssueTime),
          expirationDate = new DateTime(c.getExpirationTime),
          idleTimeout = settings.authenticatorIdleTimeout
        )
      }
    }.recover {
      case e => throw new AuthenticationException(InvalidJWTToken.format(ID, str), e)
    }
  }

  /**
   * Builds the login info from Json.
   *
   * @param str The string representation of the login info.
   * @return The login info on success, otherwise a failure.
   */
  private def buildLoginInfo(str: String): Try[LoginInfo] = {
    Try(Json.parse(str)) match {
      case Success(json) =>
        // We needn't check here if the given Json is a valid LoginInfo object, because the
        // token will be signed and therefore the login info can't be manipulated. So if we
        // serialize an authenticator into a JWT, then this JWT is always the same authenticator
        // after deserialization
        Success(json.as[LoginInfo])
      case Failure(error) =>
        // This error can occur if an authenticator was serialized with the setting encryptSubject=true
        // and deserialized with the setting encryptSubject=false
        Failure(new AuthenticationException(JsonParseError.format(ID, str), error))
    }
  }
}

/**
 * The companion object of the authenticator service.
 */
object JWTAuthenticatorService {

  /**
   * The ID of the authenticator.
   */
  val ID = "jwt-authenticator"

  /**
   * The error messages.
   */
  val InvalidJWTToken = "[Silhouette][%s] Error on parsing JWT token: %s"
  val JsonParseError = "[Silhouette][%s] Cannot parse Json: %s"
  val InvalidJsonFormat = "[Silhouette][%s] Invalid Json format: %s"
}

/**
 * The settings for the JWT authenticator.
 *
 * @param headerName The name of the header in which the token will be transfered.
 * @param issuerClaim The issuer claim identifies the principal that issued the JWT.
 * @param encryptSubject Indicates if the subject should be encrypted in JWT.
 * @param authenticatorIdleTimeout The time in seconds an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The expiry of the authenticator in minutes.
 * @param sharedSecret The shared secret to sign the JWT.
 */
case class JWTAuthenticatorSettings(
  headerName: String = "X-Auth-Token",
  issuerClaim: String = "play-silhouette",
  encryptSubject: Boolean = true,
  authenticatorIdleTimeout: Option[Int] = None, // This feature is disabled by default to prevent the generation of a new JWT on every request
  authenticatorExpiry: Int = 12 * 60 * 60,
  sharedSecret: String)
