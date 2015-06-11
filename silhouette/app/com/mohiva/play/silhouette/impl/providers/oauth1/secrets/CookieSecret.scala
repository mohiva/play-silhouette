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
package com.mohiva.play.silhouette.impl.providers.oauth1.secrets

import javax.inject.Inject

import com.mohiva.play.silhouette.api.util.{ Clock, ExtractableRequest }
import com.mohiva.play.silhouette.impl.exceptions.OAuth1TokenSecretException
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.CookieSecretProvider._
import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth1TokenSecret, OAuth1TokenSecretProvider }
import org.joda.time.DateTime
import play.api.Play
import play.api.Play.current
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.{ Cookie, Result }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

/**
 * The cookie secret companion object.
 */
object CookieSecret {

  /**
   * Converts the [[CookieSecret]]] to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[CookieSecret]

  /**
   * Returns a serialized value of the secret.
   *
   * @param secret The secret to serialize.
   * @return A serialized value of the secret.
   */
  def serialize(secret: CookieSecret) = Crypto.encryptAES(Json.toJson(secret).toString())

  /**
   * Unserializes the secret.
   *
   * @param str The string representation of the secret.
   * @return Some secret on success, otherwise None.
   */
  def unserialize(str: String): Try[CookieSecret] = {
    Try(Json.parse(Crypto.decryptAES(str))) match {
      case Success(json) => json.validate[CookieSecret].asEither match {
        case Left(error) => Failure(new OAuth1TokenSecretException(InvalidSecretFormat.format(error)))
        case Right(authenticator) => Success(authenticator)
      }
      case Failure(error) => Failure(new OAuth1TokenSecretException(InvalidSecretFormat.format(error)))
    }
  }
}

/**
 * A token secret which gets persisted in a cookie.
 *
 * @param value The token secret.
 * @param expirationDate The expiration time.
 */
case class CookieSecret(value: String, expirationDate: DateTime) extends OAuth1TokenSecret {

  /**
   * Checks if the secret is expired. This is an absolute timeout since the creation of
   * the secret.
   *
   * @return True if the secret is expired, false otherwise.
   */
  override def isExpired = expirationDate.isBeforeNow

  /**
   * Returns a serialized value of the secret.
   *
   * @return A serialized value of the secret.
   */
  override def serialize = CookieSecret.serialize(this)
}

/**
 * Saves the secret in a cookie.
 *
 * @param settings The secret settings.
 * @param clock The clock implementation.
 */
class CookieSecretProvider @Inject() (
  settings: CookieSecretSettings,
  clock: Clock) extends OAuth1TokenSecretProvider {

  /**
   * The type of the secret implementation.
   */
  override type Secret = CookieSecret

  /**
   * Builds the secret from OAuth info.
   *
   * @param info The OAuth info returned from the provider.
   * @param request The current request.
   * @param ec The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The build secret.
   */
  override def build[B](info: OAuth1Info)(
    implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[CookieSecret] = {

    Future.successful(CookieSecret(info.secret, clock.now.plusSeconds(settings.expirationTime.toSeconds.toInt)))
  }

  /**
   * Retrieves the token secret.
   *
   * @param request The current request.
   * @param ec The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return A secret on success, otherwise an failure.
   */
  override def retrieve[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Secret] = {
    request.cookies.get(settings.cookieName) match {
      case Some(cookie) => CookieSecret.unserialize(cookie.value) match {
        case Success(secret) if secret.isExpired => Future.failed(new OAuth1TokenSecretException(SecretIsExpired))
        case Success(secret) => Future.successful(secret)
        case Failure(error) => Future.failed(error)
      }
      case None => Future.failed(new OAuth1TokenSecretException(ClientSecretDoesNotExists.format(settings.cookieName)))
    }
  }

  /**
   * Publishes the secret to the client.
   *
   * @param result The result to send to the client.
   * @param secret The secret to publish.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def publish[B](result: Result, secret: CookieSecret)(implicit request: ExtractableRequest[B]) = {
    result.withCookies(Cookie(name = settings.cookieName,
      value = secret.serialize,
      maxAge = Some(settings.expirationTime.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie))
  }
}

/**
 * The CookieSecretProvider companion object.
 */
object CookieSecretProvider {

  /**
   * The error messages.
   */
  val ClientSecretDoesNotExists = "[Silhouette][CookieSecretProvider] Secret cookie doesn't exists for name: %s"
  val SecretIsExpired = "[Silhouette][CookieSecretProvider] Secret is expired"
  val InvalidSecretFormat = "[Silhouette][CookieSecretProvider] Cannot build token secret because of invalid Json format: %s"
}

/**
 * The settings for the cookie secret.
 *
 * @param cookieName The cookie name.
 * @param cookiePath The cookie path.
 * @param cookieDomain The cookie domain.
 * @param secureCookie Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnlyCookie Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param expirationTime Secret expiration. Defaults to 5 minutes which provides sufficient time to log in, but
 *                       not too much. This is a balance between convenience and security.
 */
case class CookieSecretSettings(
  cookieName: String = "OAuth1TokenSecret",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = Play.isProd, // Default to sending only for HTTPS in production, but not for development and test.
  httpOnlyCookie: Boolean = true,
  expirationTime: Duration = 5 minutes)
