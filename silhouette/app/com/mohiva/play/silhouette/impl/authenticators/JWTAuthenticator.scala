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

import com.atlassian.jwt.SigningAlgorithm
import com.atlassian.jwt.core.writer.{ JsonSmartJwtJsonBuilder, NimbusJwtWriterFactory }
import com.mohiva.play.silhouette._
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService }
import com.mohiva.play.silhouette.api.util.{ Base64, Clock, IDGenerator }
import com.mohiva.play.silhouette.api.{ Logger, LoginInfo, StorableAuthenticator }
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator._
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticatorService._
import com.mohiva.play.silhouette.impl.daos.AuthenticatorDAO
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import org.joda.time.DateTime
import play.api.libs.Crypto
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc.{ RequestHeader, Result }

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * An authenticator that uses a header based approach with the help of a JWT. It works by
 * using a JWT to transport the authenticator data inside a user defined header. It can
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
 * @param customClaims Custom claims to embed into the token.
 */
case class JWTAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDate: DateTime,
  expirationDate: DateTime,
  idleTimeout: Option[Int],
  customClaims: Option[JsObject] = None)
  extends StorableAuthenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  type Value = String

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
 * The companion object.
 */
object JWTAuthenticator {

  /**
   * Serializes the authenticator.
   *
   * @param authenticator The authenticator to serialize.
   * @param settings The authenticator settings.
   * @return The serialized authenticator.
   */
  def serialize(authenticator: JWTAuthenticator)(settings: JWTAuthenticatorSettings): String = {
    val subject = Json.toJson(authenticator.loginInfo).toString()
    val jwtBuilder = new JsonSmartJwtJsonBuilder()
      .jwtId(authenticator.id)
      .issuer(settings.issuerClaim)
      .subject(if (settings.encryptSubject) Crypto.encryptAES(subject) else Base64.encode(subject))
      .issuedAt(authenticator.lastUsedDate.getMillis / 1000)
      .expirationTime(authenticator.expirationDate.getMillis / 1000)

    authenticator.customClaims.foreach { data =>
      serializeCustomClaims(data).foreach {
        case (key, value) =>
          if (ReservedClaims.contains(key)) {
            throw new AuthenticatorException(OverrideReservedClaim.format(ID, key, ReservedClaims.mkString(", ")))
          }
          jwtBuilder.claim(key, value)
      }
    }

    new NimbusJwtWriterFactory()
      .macSigningWriter(SigningAlgorithm.HS256, settings.sharedSecret)
      .jsonToJwt(jwtBuilder.build())
  }

  /**
   * Unserializes the authenticator.
   *
   * @param str The string representation of the authenticator.
   * @param settings The authenticator settings.
   * @return An authenticator on success, otherwise a failure.
   */
  def unserialize(str: String)(settings: JWTAuthenticatorSettings): Try[JWTAuthenticator] = {
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
        val filteredClaims = c.getAllClaims.asScala.filterNot { case (k, v) => ReservedClaims.contains(k) || v == null }
        val customClaims = unserializeCustomClaims(filteredClaims)
        JWTAuthenticator(
          id = c.getJWTID,
          loginInfo = loginInfo,
          lastUsedDate = new DateTime(c.getIssueTime),
          expirationDate = new DateTime(c.getExpirationTime),
          idleTimeout = settings.authenticatorIdleTimeout,
          customClaims = if (customClaims.keys.isEmpty) None else Some(customClaims)
        )
      }
    }.recover {
      case e => throw new AuthenticatorException(InvalidJWTToken.format(ID, str), e)
    }
  }

  /**
   * Serializes recursively the custom claims.
   *
   * @param claims The custom claims to serialize.
   * @return A map containing custom claims.
   */
  private def serializeCustomClaims(claims: JsObject): java.util.Map[String, Any] = {
    def toJava(value: JsValue): Any = value match {
      case v: JsString => v.value
      case v: JsNumber => v.value
      case v: JsBoolean => v.value
      case v: JsObject => serializeCustomClaims(v)
      case v: JsArray => v.value.map(toJava).asJava
      case v => throw new AuthenticatorException(UnexpectedJsonValue.format(ID, v))
    }

    claims.fieldSet.map { case (name, value) => name -> toJava(value) }.toMap.asJava
  }

  /**
   * Unserializes recursively the custom claims.
   *
   * @param claims The custom claims to deserialize.
   * @return A Json object representing the custom claims.
   */
  private def unserializeCustomClaims(claims: java.util.Map[String, Any]): JsObject = {
    def toJson(value: Any): JsValue = value match {
      case v: java.lang.String => JsString(v)
      case v: java.lang.Number => JsNumber(BigDecimal(v.toString))
      case v: java.lang.Boolean => JsBoolean(v)
      case v: java.util.Map[_, _] => unserializeCustomClaims(v.asInstanceOf[java.util.Map[String, Any]])
      case v: java.util.List[_] => JsArray(v.map(toJson))
      case v => throw new AuthenticatorException(UnexpectedJsonValue.format(ID, v))
    }

    JsObject(claims.map { case (name, value) => name -> toJson(value) }.toSeq)
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
        Failure(new AuthenticatorException(JsonParseError.format(ID, str), error))
    }
  }
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
  val settings: JWTAuthenticatorSettings,
  dao: Option[AuthenticatorDAO[JWTAuthenticator]],
  idGenerator: IDGenerator,
  clock: Clock)
  extends AuthenticatorService[JWTAuthenticator]
  with Logger {

  /**
   * The type of this class.
   */
  type Self = JWTAuthenticatorService

  /**
   * The type of the settings.
   */
  type Settings = JWTAuthenticatorSettings

  /**
   * Gets an authenticator service initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the authenticator service initialized with new settings.
   */
  def withSettings(f: JWTAuthenticatorSettings => JWTAuthenticatorSettings) = {
    new JWTAuthenticatorService(f(settings), dao, idGenerator, clock)
  }

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create(loginInfo: LoginInfo)(implicit request: RequestHeader): Future[JWTAuthenticator] = {
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
      case e => throw new AuthenticatorCreationException(CreateError.format(ID, loginInfo), e)
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
  def retrieve(implicit request: RequestHeader): Future[Option[JWTAuthenticator]] = {
    Future.from(Try(request.headers.get(settings.headerName))).flatMap {
      case Some(token) => unserialize(token)(settings) match {
        case Success(authenticator) => dao.fold(Future.successful(Option(authenticator)))(_.find(authenticator.id))
        case Failure(e) =>
          logger.info(e.getMessage, e)
          Future.successful(None)
      }
      case None => Future.successful(None)
    }.recover {
      case e => throw new AuthenticatorRetrievalException(RetrieveError.format(ID), e)
    }
  }

  /**
   * Creates a new JWT for the given authenticator and return it. If a backing store is defined, then the
   * authenticator will be stored in it.
   *
   * @param authenticator The authenticator instance.
   * @param request The request header.
   * @return The serialized authenticator value.
   */
  def init(authenticator: JWTAuthenticator)(implicit request: RequestHeader): Future[String] = {
    dao.fold(Future.successful(authenticator))(_.add(authenticator)).map { a =>
      serialize(a)(settings)
    }.recover {
      case e => throw new AuthenticatorInitializationException(InitError.format(ID, authenticator), e)
    }
  }

  /**
   * Adds a header with the token as value to the result.
   *
   * @param token The token to embed.
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  def embed(token: String, result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {
    Future.successful(AuthenticatorResult(result.withHeaders(settings.headerName -> token)))
  }

  /**
   * Adds a header with the token as value to the request.
   *
   * @param token The token to embed.
   * @param request The request header.
   * @return The manipulated request header.
   */
  def embed(token: String, request: RequestHeader): RequestHeader = {
    val additional = Seq(settings.headerName -> token)
    request.copy(headers = request.headers.replace(additional: _*))
  }

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  def touch(authenticator: JWTAuthenticator): Either[JWTAuthenticator, JWTAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) {
      Left(authenticator.copy(lastUsedDate = clock.now))
    } else {
      Right(authenticator)
    }
  }

  /**
   * Updates the authenticator and embeds a new token in the result.
   *
   * To prevent the creation of a new token on every request, disable the idle timeout setting and this
   * method will not be executed.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def update(
    authenticator: JWTAuthenticator,
    result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {

    dao.fold(Future.successful(authenticator))(_.update(authenticator)).map { a =>
      AuthenticatorResult(result.withHeaders(settings.headerName -> serialize(a)(settings)))
    }.recover {
      case e => throw new AuthenticatorUpdateException(UpdateError.format(ID, authenticator), e)
    }
  }

  /**
   * Renews an authenticator.
   *
   * After that it isn't possible to use a JWT which was bound to this authenticator. This method
   * doesn't embed the the authenticator into the result. This must be done manually if needed
   * or use the other renew method otherwise.
   *
   * @param authenticator The authenticator to renew.
   * @param request The request header.
   * @return The serialized expression of the authenticator.
   */
  def renew(authenticator: JWTAuthenticator)(implicit request: RequestHeader): Future[String] = {
    dao.fold(Future.successful(()))(_.remove(authenticator.id)).flatMap { _ =>
      create(authenticator.loginInfo).flatMap(init)
    }.recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Renews an authenticator and teplaces the JWT header with a new one.
   *
   * If a backing store is defined, the old authenticator will be revoked. After that it isn't
   * possible to use a JWT which was bound to this authenticator.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  def renew(
    authenticator: JWTAuthenticator,
    result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {

    renew(authenticator).flatMap(v => embed(v, result)).recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Removes the authenticator from backing store.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def discard(
    authenticator: JWTAuthenticator,
    result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {

    dao.fold(Future.successful(()))(_.remove(authenticator.id)).map { _ =>
      AuthenticatorResult(result)
    }.recover {
      case e => throw new AuthenticatorDiscardingException(DiscardError.format(ID, authenticator), e)
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
  val UnexpectedJsonValue = "[Silhouette][%s] Unexpected Json value: %s"
  val OverrideReservedClaim = "[Silhouette][%s] Try to overriding a reserved claim `%s`; list of reserved claims: %s"

  /**
   * The reserved claims used by the authenticator.
   */
  val ReservedClaims = Seq("jti", "iss", "sub", "iat", "exp")
}

/**
 * The settings for the JWT authenticator.
 *
 * @param headerName The name of the header in which the token will be transferred.
 * @param issuerClaim The issuer claim identifies the principal that issued the JWT.
 * @param encryptSubject Indicates if the subject should be encrypted in JWT.
 * @param authenticatorIdleTimeout The time in seconds an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The expiry of the authenticator in seconds.
 * @param sharedSecret The shared secret to sign the JWT.
 */
case class JWTAuthenticatorSettings(
  headerName: String = "X-Auth-Token",
  issuerClaim: String = "play-silhouette",
  encryptSubject: Boolean = true,
  authenticatorIdleTimeout: Option[Int] = None, // This feature is disabled by default to prevent the generation of a new JWT on every request
  authenticatorExpiry: Int = 12 * 60 * 60,
  sharedSecret: String)
