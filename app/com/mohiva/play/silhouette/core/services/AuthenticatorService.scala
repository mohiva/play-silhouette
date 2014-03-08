/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.core.services

import scala.concurrent.Future
import play.api.mvc.{ SimpleResult, RequestHeader }
import com.mohiva.play.silhouette.core.{ Identity, Authenticator }

/**
 * The authenticator store is in charge of persisting authenticators for the Silhouette module.
 *
 * @tparam T The type of the authenticator this service is responsible.
 */
trait AuthenticatorService[T <: Authenticator] {

  /**
   * Creates a new authenticator ID for the specified identity.
   *
   * @param identity The identity for which the ID should be created.
   * @return An authenticator.
   */
  def create[I <: Identity](identity: I): Future[Option[T]]

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  def retrieve(implicit request: RequestHeader): Future[Option[T]]

  /**
   * Updates an existing authenticator.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator or None if the authenticator couldn't be updated.
   */
  def update(authenticator: T): Future[Option[T]]

  /**
   * Manipulates the response and sends authenticator specific data to the client.
   *
   * @param authenticator The authenticator instance.
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  def send(authenticator: T, result: SimpleResult): SimpleResult = result

  /**
   * Manipulates the response and removes authenticator specific data before sending it to the client.
   *
   * @param result The result to manipulate.
   * @return The manipulated result.
   */
  def discard(result: SimpleResult): SimpleResult = result
}
