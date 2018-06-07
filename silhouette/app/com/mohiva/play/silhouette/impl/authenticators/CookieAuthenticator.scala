/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.crypto.{ AuthenticatorEncoder, Signer }
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService }
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorService._
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.request.{ Cell, RequestAttrKey }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
 * An authenticator that uses a stateful as well as stateless, cookie based approach.
 *
 * It works either by storing an ID in a cookie to track the authenticated user and a server side backing
 * store that maps the ID to an authenticator instance or by a stateless approach that stores the authenticator
 * in a serialized form directly into the cookie. The stateless approach could also be named “server side session”.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property.
 *
 * With this authenticator it's possible to implement "Remember Me" functionality. This can be
 * achieved by updating the `expirationDateTime`, `idleTimeout` or `cookieMaxAge` properties of
 * this authenticator after it was created and before it gets initialized.
 *
 * Note: If deploying to multiple nodes the backing store will need to synchronize.
 *
 * @param id                 The authenticator ID.
 * @param loginInfo          The linked login info for an identity.
 * @param lastUsedDateTime   The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout        The duration an authenticator can be idle before it timed out.
 * @param cookieMaxAge       The duration a cookie expires. `None` for a transient cookie.
 * @param fingerprint        Maybe a fingerprint of the user.
 */
case class CookieAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDateTime: DateTime,
  expirationDateTime: DateTime,
  idleTimeout: Option[FiniteDuration],
  cookieMaxAge: Option[FiniteDuration],
  fingerprint: Option[String]
) extends StorableAuthenticator with ExpirableAuthenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  override type Value = Cookie
}

/**
 * The companion object of the authenticator.
 */
object CookieAuthenticator extends Logger {
  import com.mohiva.play.silhouette.api.util.JsonFormats._
  import play.api.libs.json.JodaReads._
  import play.api.libs.json.JodaWrites._

  /**
   * Converts the CookieAuthenticator to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[CookieAuthenticator]

  /**
   * Serializes the authenticator.
   *
   * @param authenticator        The authenticator to serialize.
   * @param signer               The signer implementation.
   * @param authenticatorEncoder The authenticator encoder.
   * @return The serialized authenticator.
   */
  def serialize(
    authenticator: CookieAuthenticator,
    signer: Signer,
    authenticatorEncoder: AuthenticatorEncoder) = {

    signer.sign(authenticatorEncoder.encode(Json.toJson(authenticator).toString()))
  }

  /**
   * Unserializes the authenticator.
   *
   * @param str                  The string representation of the authenticator.
   * @param signer               The signer implementation.
   * @param authenticatorEncoder The authenticator encoder.
   * @return Some authenticator on success, otherwise None.
   */
  def unserialize(
    str: String,
    signer: Signer,
    authenticatorEncoder: AuthenticatorEncoder): Try[CookieAuthenticator] = {

    signer.extract(str) match {
      case Success(data) => buildAuthenticator(authenticatorEncoder.decode(data))
      case Failure(e)    => Failure(new AuthenticatorException(InvalidCookieSignature.format(ID), e))
    }
  }

  /**
   * Builds the authenticator from Json.
   *
   * @param str The string representation of the authenticator.
   * @return An authenticator on success, otherwise a failure.
   */
  private def buildAuthenticator(str: String): Try[CookieAuthenticator] = {
    Try(Json.parse(str)) match {
      case Success(json) => json.validate[CookieAuthenticator].asEither match {
        case Left(error)          => Failure(new AuthenticatorException(InvalidJsonFormat.format(ID, error)))
        case Right(authenticator) => Success(authenticator)
      }
      case Failure(error) => Failure(new AuthenticatorException(InvalidJson.format(ID, str), error))
    }
  }
}

/**
 * The service that handles the cookie authenticator.
 *
 * @param settings             The cookie settings.
 * @param repository           The repository to persist the authenticator. Set it to None to use a stateless approach.
 * @param signer               The signer implementation.
 * @param cookieHeaderEncoding Logic for encoding and decoding `Cookie` and `Set-Cookie` headers.
 * @param authenticatorEncoder The authenticator encoder.
 * @param fingerprintGenerator The fingerprint generator implementation.
 * @param idGenerator          The ID generator used to create the authenticator ID.
 * @param clock                The clock implementation.
 * @param executionContext     The execution context to handle the asynchronous operations.
 */
class CookieAuthenticatorService(
  settings: CookieAuthenticatorSettings,
  repository: Option[AuthenticatorRepository[CookieAuthenticator]],
  signer: Signer,
  cookieHeaderEncoding: CookieHeaderEncoding,
  authenticatorEncoder: AuthenticatorEncoder,
  fingerprintGenerator: FingerprintGenerator,
  idGenerator: IDGenerator,
  clock: Clock
)(
  implicit
  val executionContext: ExecutionContext
) extends AuthenticatorService[CookieAuthenticator] with Logger {

  import CookieAuthenticator._

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request   The request header.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo)(implicit request: RequestHeader): Future[CookieAuthenticator] = {
    idGenerator.generate.map { id =>
      val now = clock.now
      CookieAuthenticator(
        id = id,
        loginInfo = loginInfo,
        lastUsedDateTime = now,
        expirationDateTime = now + settings.authenticatorExpiry,
        idleTimeout = settings.authenticatorIdleTimeout,
        cookieMaxAge = settings.cookieMaxAge,
        fingerprint = if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
      )
    }.recover {
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
  override def retrieve[B](implicit request: ExtractableRequest[B]): Future[Option[CookieAuthenticator]] = {
    Future.fromTry(Try {
      if (settings.useFingerprinting) Some(fingerprintGenerator.generate) else None
    }).flatMap { fingerprint =>
      request.cookies.get(settings.cookieName) match {
        case Some(cookie) =>
          (repository match {
            case Some(d) => d.find(cookie.value)
            case None => unserialize(cookie.value, signer, authenticatorEncoder) match {
              case Success(authenticator) => Future.successful(Some(authenticator))
              case Failure(error) =>
                logger.info(error.getMessage, error)
                Future.successful(None)
            }
          }).map {
            case Some(a) if fingerprint.isDefined && a.fingerprint != fingerprint =>
              logger.info(InvalidFingerprint.format(ID, fingerprint, a))
              None
            case v => v
          }
        case None => Future.successful(None)
      }
    }.recover {
      case e => throw new AuthenticatorRetrievalException(RetrieveError.format(ID), e)
    }
  }

  /**
   * Creates a new cookie for the given authenticator and return it.
   *
   * If the stateful approach will be used the the authenticator will also be
   * stored in the backing store.
   *
   * @param authenticator The authenticator instance.
   * @param request       The request header.
   * @return The serialized authenticator value.
   */
  override def init(authenticator: CookieAuthenticator)(implicit request: RequestHeader): Future[Cookie] = {
    (repository match {
      case Some(d) => d.add(authenticator).map(_.id)
      case None    => Future.successful(serialize(authenticator, signer, authenticatorEncoder))
    }).map { value =>
      Cookie(
        name = settings.cookieName,
        value = value,
        // The maxAge` must be used from the authenticator, because it might be changed by the user
        // to implement "Remember Me" functionality
        maxAge = authenticator.cookieMaxAge.map(_.toSeconds.toInt),
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie,
        sameSite = settings.sameSite
      )
    }.recover {
      case e => throw new AuthenticatorInitializationException(InitError.format(ID, authenticator), e)
    }
  }

  /**
   * Embeds the cookie into the result.
   *
   * @param cookie  The cookie to embed.
   * @param result  The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  override def embed(cookie: Cookie, result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {
    Future.successful(AuthenticatorResult(result.withCookies(cookie)))
  }

  /**
   * Embeds the cookie into the request.
   *
   * @param cookie  The cookie to embed.
   * @param request The request header.
   * @return The manipulated request header.
   */
  override def embed(cookie: Cookie, request: RequestHeader): RequestHeader = {
    val filteredCookies = request.cookies.filter(_.name != cookie.name).toSeq
    val combinedCookies = filteredCookies :+ cookie
    val cookies = Cookies(combinedCookies)

    request.withAttrs(request.attrs + RequestAttrKey.Cookies.bindValue(Cell(cookies)))
  }

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  override def touch(authenticator: CookieAuthenticator): Either[CookieAuthenticator, CookieAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) {
      Left(authenticator.copy(lastUsedDateTime = clock.now))
    } else {
      Right(authenticator)
    }
  }

  /**
   * Updates the authenticator with the new last used date.
   *
   * If the stateless approach will be used then we update the cookie on the client. With the stateful approach
   * we needn't embed the cookie in the response here because the cookie itself will not be changed. Only the
   * authenticator in the backing store will be changed.
   *
   * @param authenticator The authenticator to update.
   * @param result        The result to manipulate.
   * @param request       The request header.
   * @return The original or a manipulated result.
   */
  override def update(authenticator: CookieAuthenticator, result: Result)(
    implicit
    request: RequestHeader): Future[AuthenticatorResult] = {

    (repository match {
      case Some(d) => d.update(authenticator).map(_ => AuthenticatorResult(result))
      case None => Future.successful(AuthenticatorResult(result.withCookies(Cookie(
        name = settings.cookieName,
        value = serialize(authenticator, signer, authenticatorEncoder),
        // The maxAge` must be used from the authenticator, because it might be changed by the user
        // to implement "Remember Me" functionality
        maxAge = authenticator.cookieMaxAge.map(_.toSeconds.toInt),
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie,
        sameSite = settings.sameSite
      ))))
    }).recover {
      case e => throw new AuthenticatorUpdateException(UpdateError.format(ID, authenticator), e)
    }
  }

  /**
   * Renews an authenticator.
   *
   * After that it isn't possible to use a cookie which was bound to this authenticator. This method
   * doesn't embed the the authenticator into the result. This must be done manually if needed
   * or use the other renew method otherwise.
   *
   * @param authenticator The authenticator to renew.
   * @param request       The request header.
   * @return The serialized expression of the authenticator.
   */
  override def renew(authenticator: CookieAuthenticator)(implicit request: RequestHeader): Future[Cookie] = {
    (repository match {
      case Some(d) => d.remove(authenticator.id)
      case None    => Future.successful(())
    }).flatMap { _ =>
      create(authenticator.loginInfo).flatMap(init)
    }.recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Renews an authenticator and replaces the authenticator cookie with a new one.
   *
   * If the stateful approach will be used then the old authenticator will be revoked in the backing
   * store. After that it isn't possible to use a cookie which was bound to this authenticator.
   *
   * @param authenticator The authenticator to update.
   * @param result        The result to manipulate.
   * @param request       The request header.
   * @return The original or a manipulated result.
   */
  override def renew(authenticator: CookieAuthenticator, result: Result)(
    implicit
    request: RequestHeader): Future[AuthenticatorResult] = {

    renew(authenticator).flatMap(v => embed(v, result)).recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Discards the cookie.
   *
   * If the stateful approach will be used then the authenticator will also be removed from backing store.
   *
   * @param result  The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  override def discard(authenticator: CookieAuthenticator, result: Result)(
    implicit
    request: RequestHeader): Future[AuthenticatorResult] = {

    (repository match {
      case Some(d) => d.remove(authenticator.id)
      case None    => Future.successful(())
    }).map { _ =>
      AuthenticatorResult(result.discardingCookies(DiscardingCookie(
        name = settings.cookieName,
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie)))
    }.recover {
      case e => throw new AuthenticatorDiscardingException(DiscardError.format(ID, authenticator), e)
    }
  }
}

/**
 * The companion object of the authenticator service.
 */
object CookieAuthenticatorService {

  /**
   * The ID of the authenticator.
   */
  val ID = "cookie-authenticator"

  /**
   * The error messages.
   */
  val InvalidJson = "[Silhouette][%s] Cannot parse invalid Json: %s"
  val InvalidJsonFormat = "[Silhouette][%s] Invalid Json format: %s"
  val InvalidFingerprint = "[Silhouette][%s] Fingerprint %s doesn't match authenticator: %s"
  val InvalidCookieSignature = "[Silhouette][%s] Invalid cookie signature"
}

/**
 * The settings for the cookie authenticator.
 *
 * @param cookieName               The cookie name.
 * @param cookiePath               The cookie path.
 * @param cookieDomain             The cookie domain.
 * @param secureCookie             Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnlyCookie           Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param sameSite                 The SameSite attribute for this cookie (for CSRF protection).
 * @param useFingerprinting        Indicates if a fingerprint of the user should be stored in the authenticator.
 * @param cookieMaxAge             The duration a cookie expires. `None` for a transient cookie.
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry      The duration an authenticator expires after it was created.
 */
case class CookieAuthenticatorSettings(
  cookieName: String = "id",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = true,
  httpOnlyCookie: Boolean = true,
  sameSite: Option[Cookie.SameSite] = Some(Cookie.SameSite.Lax),
  useFingerprinting: Boolean = true,
  cookieMaxAge: Option[FiniteDuration] = None,
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12 hours
)
