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
package com.mohiva.play.silhouette.impl.authenticators

import com.mohiva.silhouette.{Authenticator, LoginInfo}
import com.mohiva.silhouette.services.AuthenticatorService
import play.api.mvc.{ RequestHeader, Result }

import scala.concurrent.Future

/**
 * An authenticator that can be used if a client doesn't need an authenticator to
 * track a user. This can be useful for request providers, because authentication
 * may occur here on every request to a protected resource.
 *
 * @param loginInfo The linked login info for an identity.
 */
case class DummyAuthenticator(loginInfo: LoginInfo) extends Authenticator {

  /**
   * Authenticator is always valid.
   *
   * @return True because it's always valid.
   */
  def isValid = true
}

/**
 * The service that handles the dummy token authenticator.
 */
class DummyAuthenticatorService extends AuthenticatorService[DummyAuthenticator] {

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create(loginInfo: LoginInfo)(implicit request: RequestHeader) = {
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
  def retrieve(implicit request: RequestHeader) = Future.successful(None)

  /**
   * Returns the original result, because we needn't add the authenticator to the result.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def init(authenticator: DummyAuthenticator, result: Future[Result])(implicit request: RequestHeader) = {
    result
  }

  /**
   * Returns the original request, because we needn't add the authenticator to the request.
   *
   * @param authenticator The authenticator instance.
   * @param request The request header.
   * @return The manipulated request header.
   */
  def init(authenticator: DummyAuthenticator, request: RequestHeader) = Future.successful(request)

  /**
   * @inheritdoc
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  protected[silhouette] def touch(authenticator: DummyAuthenticator) = Right(authenticator)

  /**
   * Returns the original request, because we needn't update the authenticator in the result.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  protected[silhouette] def update(
    authenticator: DummyAuthenticator,
    result: Future[Result])(implicit request: RequestHeader) = {

    result
  }

  /**
   * Returns the original request, because we needn't renew the authenticator in the result.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  protected[silhouette] def renew(
    authenticator: DummyAuthenticator,
    result: Future[Result])(implicit request: RequestHeader) = {

    result
  }

  /**
   * Returns the original request, because we needn't discard the authenticator in the result.
   *
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  protected[silhouette] def discard(
    authenticator: DummyAuthenticator,
    result: Future[Result])(implicit request: RequestHeader) = {

    result
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
