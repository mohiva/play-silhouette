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

import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService }
import com.mohiva.play.silhouette.api.{ Authenticator, LoginInfo }
import play.api.mvc.{ RequestHeader, Result }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * An authenticator that can be used if a client doesn't need an authenticator to
 * track a user. This can be useful for request providers, because authentication
 * may occur here on every request to a protected resource.
 *
 * @param loginInfo The linked login info for an identity.
 */
case class DummyAuthenticator(loginInfo: LoginInfo) extends Authenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  override type Value = Unit

  /**
   * The type of the settings an authenticator can handle.
   */
  override type Settings = Unit

  /**
   * Authenticator is always valid.
   *
   * @return True because it's always valid.
   */
  override def isValid = true
}

/**
 * The service that handles the dummy token authenticator.
 *
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class DummyAuthenticatorService(implicit val executionContext: ExecutionContext)
  extends AuthenticatorService[DummyAuthenticator] {

  /**
   * The type of this class.
   */
  override type Self = DummyAuthenticatorService

  /**
   * Gets the authenticator settings.
   *
   * @return The authenticator settings.
   */
  override def settings = ()

  /**
   * Gets an authenticator service initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the authenticator service initialized with new settings.
   */
  override def withSettings(f: Unit => Unit) = new DummyAuthenticatorService()

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  override def create(loginInfo: LoginInfo)(implicit request: RequestHeader): Future[DummyAuthenticator] = {
    Future.successful(DummyAuthenticator(loginInfo))
  }

  /**
   * Retrieves the authenticator from request.
   *
   * Doesn't need to return an authenticator here, because this method will not be called if
   * a request provider grants access. If the authentication with a request provider has failed,
   * then this method must return None to not grant access to the resource.
   *
   * @param request The request header.
   * @return Always None because .
   */
  override def retrieve(implicit request: RequestHeader): Future[Option[DummyAuthenticator]] = {
    Future.successful(None)
  }

  /**
   * Returns noting because this authenticator doesn't have a serialized representation.
   *
   * @param authenticator The authenticator instance.
   * @param request The request header.
   * @return The serialized authenticator value.
   */
  override def init(authenticator: DummyAuthenticator)(implicit request: RequestHeader): Future[Unit] = {
    Future.successful(())
  }

  /**
   * Returns the original result, because we needn't add the authenticator to the result.
   *
   * @param value The authenticator value to embed.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  override def embed(value: Unit, result: Result)(implicit request: RequestHeader): Future[AuthenticatorResult] = {
    Future.successful(AuthenticatorResult(result))
  }

  /**
   * Returns the original request, because we needn't add the authenticator to the request.
   *
   * @param value The authenticator value to embed.
   * @param request The request header.
   * @return The manipulated request header.
   */
  override def embed(value: Unit, request: RequestHeader): RequestHeader = request

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  override def touch(authenticator: DummyAuthenticator): Either[DummyAuthenticator, DummyAuthenticator] = {
    Right(authenticator)
  }

  /**
   * Returns the original request, because we needn't update the authenticator in the result.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  override def update(authenticator: DummyAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    Future.successful(AuthenticatorResult(result))
  }

  /**
   * Returns noting because this authenticator doesn't have a serialized representation.
   *
   * @param authenticator The authenticator to renew.
   * @param request The request header.
   * @return The serialized expression of the authenticator.
   */
  override def renew(authenticator: DummyAuthenticator)(implicit request: RequestHeader): Future[Unit] = {
    Future.successful(())
  }

  /**
   * Returns the original request, because we needn't renew the authenticator in the result.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  override def renew(authenticator: DummyAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    Future.successful(AuthenticatorResult(result))
  }

  /**
   * Returns the original request, because we needn't discard the authenticator in the result.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  override def discard(authenticator: DummyAuthenticator, result: Result)(
    implicit request: RequestHeader): Future[AuthenticatorResult] = {

    Future.successful(AuthenticatorResult(result))
  }
}

/**
 * The companion object of the authenticator service.
 */
object DummyAuthenticatorService {

  /**
   * The ID of the authenticator.
   */
  val ID = "dummy-authenticator"
}
