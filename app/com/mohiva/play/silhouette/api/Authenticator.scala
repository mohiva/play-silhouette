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
package com.mohiva.play.silhouette.api

import com.mohiva.play.silhouette.api.Authenticator.Discard
import com.mohiva.play.silhouette.api.Authenticator.Renew
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Result

import scala.concurrent.Future

/**
 * An authenticator tracks an authenticated user.
 */
trait Authenticator {

  /**
   * Gets the linked login info for an identity.
   *
   * @return The linked login info for an identity.
   */
  def loginInfo: LoginInfo

  /**
   * Checks if the authenticator isn't expired and isn't timed out.
   *
   * @return True if the authenticator isn't expired and isn't timed out.
   */
  def isValid: Boolean

  /**
   * Discards an authenticator.
   *
   * @param result The result to wrap into the [[com.mohiva.play.silhouette.api.Authenticator.Discard]] result.
   * @return A [[com.mohiva.play.silhouette.api.Authenticator.Discard]] result.
   */
  def discard(result: Result): Result = new Discard(result)

  /**
   * Discards an authenticator.
   *
   * @param result The result to wrap into the [[com.mohiva.play.silhouette.api.Authenticator.Discard]] result.
   * @return A [[com.mohiva.play.silhouette.api.Authenticator.Discard]] result.
   */
  def discard(result: Future[Result]): Future[Result] = result.map(r => discard(r))

  /**
   * Renews an authenticator.
   *
   * @param result The result to wrap into the [[com.mohiva.play.silhouette.api.Authenticator.Renew]] result.
   * @return A [[com.mohiva.play.silhouette.api.Authenticator.Renew]] result.
   */
  def renew(result: Result): Result = new Renew(result)

  /**
   * Renews an authenticator.
   *
   * @param result The result to wrap into the [[com.mohiva.play.silhouette.api.Authenticator.Renew]] result.
   * @return A [[com.mohiva.play.silhouette.api.Authenticator.Renew]] result.
   */
  def renew(result: Future[Result]): Future[Result] = result.map(r => renew(r))
}

/**
 * The companion object.
 */
object Authenticator {

  /**
   * A marker result which indicates that an authenticator should be discarded.
   *
   * @param result The wrapped result.
   */
  class Discard(result: Result) extends Result(result.header, result.body, result.connection)

  /**
   * A marker result which indicates that an authenticator should be renewed.
   *
   * @param result The wrapped result.
   */
  class Renew(result: Result) extends Result(result.header, result.body, result.connection)
}

/**
 * An authenticator which can be stored in a backing store.
 */
trait StorableAuthenticator extends Authenticator {

  /**
   * Gets the ID to reference the authenticator in the backing store.
   *
   * @return The ID to reference the authenticator in the backing store.
   */
  def id: String
}
