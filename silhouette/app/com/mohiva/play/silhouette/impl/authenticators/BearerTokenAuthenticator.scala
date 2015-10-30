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
import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService }
import com.mohiva.play.silhouette.api.util.{ Clock, IDGenerator }
import com.mohiva.play.silhouette.api.{ ExpirableAuthenticator, Logger, LoginInfo, StorableAuthenticator }
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticatorService._
import org.joda.time.DateTime
import play.api.mvc.{ RequestHeader, Result }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.Try

/**
 * An authenticator that uses a header based approach with the help of a bearer token. It
 * works by transporting a token in a user defined header to track the authenticated user
 * and a server side backing store that maps the token to an authenticator instance.
 *
 * The authenticator can use sliding window expiration. This means that the authenticator times
 * out after a certain time if it wasn't used. This can be controlled with the [[idleTimeout]]
 * property.
 *
 * Note: If deploying to multiple nodes the backing store will need to synchronize.
 *
 * @param id The authenticator ID.
 * @param loginInfo The linked login info for an identity.
 * @param lastUsedDateTime The last used date/time.
 * @param expirationDateTime The expiration date/time.
 * @param idleTimeout The duration an authenticator can be idle before it timed out.
 */
case class BearerTokenAuthenticator(
  id: String,
  loginInfo: LoginInfo,
  lastUsedDateTime: DateTime,
  expirationDateTime: DateTime,
  idleTimeout: Option[FiniteDuration])
  extends StorableAuthenticator with ExpirableAuthenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  override type Value = String
}

/**
 * The service that handles the bearer token authenticator.
 *
 * @param settings The authenticator settings.
 * @param repository The repository to persist the authenticator.
 * @param idGenerator The ID generator used to create the authenticator ID.
 * @param clock The clock implementation.
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class BearerTokenAuthenticatorService(
  settings: BearerTokenAuthenticatorSettings,
  repository: AuthenticatorRepository[BearerTokenAuthenticator],
  idGenerator: IDGenerator,
  clock: Clock)(implicit val executionContext: ExecutionContext)
  extends AuthenticatorService[BearerTokenAuthenticator]
  with Logger {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo)(implicit request: RequestHeader): Future[BearerTokenAuthenticator] = {
    idGenerator.generate.map { id =>
      val now = clock.now
      BearerTokenAuthenticator(
        id = id,
        loginInfo = loginInfo,
        lastUsedDateTime = now,
        expirationDateTime = now + settings.authenticatorExpiry,
        idleTimeout = settings.authenticatorIdleTimeout)
    }.recover {
      case e => throw new AuthenticatorCreationException(CreateError.format(ID, loginInfo), e)
    }
  }

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  override def retrieve(implicit request: RequestHeader): Future[Option[BearerTokenAuthenticator]] = {
    Future.from(Try(request.headers.get(settings.headerName))).flatMap {
      case Some(token) => repository.find(token)
      case None => Future.successful(None)
    }.recover {
      case e => throw new AuthenticatorRetrievalException(RetrieveError.format(ID), e)
    }
  }

  /**
   * Creates a new bearer token for the given authenticator and return it. The authenticator will also be
   * stored in the backing store.
   *
   * @param authenticator The authenticator instance.
   * @param request The request header.
   * @return The serialized authenticator value.
   */
  override def init(authenticator: BearerTokenAuthenticator)(implicit request: RequestHeader): Future[String] = {
    repository.add(authenticator).map { a =>
      a.id
    }.recover {
      case e => throw new AuthenticatorInitializationException(InitError.format(ID, authenticator), e)
    }
  }

  /**
   * Adds a header with the token as value to the result.
   *
   * @param token The token to embed.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  override def embed(token: String, result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {
    Future.successful(AuthenticatorResult(result.withHeaders(settings.headerName -> token)))
  }

  /**
   * Adds a header with the token as value to the request.
   *
   * @param token The token to embed.
   * @param request The request header.
   * @return The manipulated request header.
   */
  override def embed(token: String, request: RequestHeader): RequestHeader = {
    val additional = Seq(settings.headerName -> token)
    request.copy(headers = request.headers.replace(additional: _*))
  }

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  override def touch(authenticator: BearerTokenAuthenticator): Either[BearerTokenAuthenticator, BearerTokenAuthenticator] = {
    if (authenticator.idleTimeout.isDefined) {
      Left(authenticator.copy(lastUsedDateTime = clock.now))
    } else {
      Right(authenticator)
    }
  }

  /**
   * Updates the authenticator with the new last used date in the backing store.
   *
   * We needn't embed the token in the response here because the token itself will not be changed.
   * Only the authenticator in the backing store will be changed.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  override def update(authenticator: BearerTokenAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    repository.update(authenticator).map { a =>
      AuthenticatorResult(result)
    }.recover {
      case e => throw new AuthenticatorUpdateException(UpdateError.format(ID, authenticator), e)
    }
  }

  /**
   * Renews an authenticator.
   *
   * After that it isn't possible to use a bearer token which was bound to this authenticator. This
   * method doesn't embed the the authenticator into the result. This must be done manually if needed
   * or use the other renew method otherwise.
   *
   * @param authenticator The authenticator to renew.
   * @param request The request header.
   * @return The serialized expression of the authenticator.
   */
  override def renew(authenticator: BearerTokenAuthenticator)(
    implicit request: RequestHeader): Future[String] = {

    repository.remove(authenticator.id).flatMap { _ =>
      create(authenticator.loginInfo).flatMap(init)
    }.recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Renews an authenticator and replaces the bearer token header with a new one.
   *
   * The old authenticator will be revoked. After that it isn't possible to use a bearer token which was
   * bound to this authenticator.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  override def renew(authenticator: BearerTokenAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    renew(authenticator).flatMap(v => embed(v, result)).recover {
      case e => throw new AuthenticatorRenewalException(RenewError.format(ID, authenticator), e)
    }
  }

  /**
   * Removes the authenticator from cache.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  override def discard(authenticator: BearerTokenAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    repository.remove(authenticator.id).map { _ =>
      AuthenticatorResult(result)
    }.recover {
      case e => throw new AuthenticatorDiscardingException(DiscardError.format(ID, authenticator), e)
    }
  }
}

/**
 * The companion object of the authenticator service.
 */
object BearerTokenAuthenticatorService {

  /**
   * The ID of the authenticator.
   */
  val ID = "bearer-token-authenticator"
}

/**
 * The settings for the bearer token authenticator.
 *
 * @param headerName The name of the header in which the token will be transferred.
 * @param authenticatorIdleTimeout The duration an authenticator can be idle before it timed out.
 * @param authenticatorExpiry The duration an authenticator expires after it was created.
 */
case class BearerTokenAuthenticatorSettings(
  headerName: String = "X-Auth-Token",
  authenticatorIdleTimeout: Option[FiniteDuration] = None,
  authenticatorExpiry: FiniteDuration = 12 hours)
