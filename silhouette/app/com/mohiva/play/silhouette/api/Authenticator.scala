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
package com.mohiva.play.silhouette.api

/**
 * An authenticator tracks an authenticated user.
 */
trait Authenticator {

  /**
   * The Type of the generated value an authenticator will be serialized to.
   */
  type Value

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
   * A flag which indicates that an operation on an authenticator was processed and
   * therefore not updated automatically.
   *
   * Due the fact that the update method gets called on every subsequent request to update the
   * authenticator related data in the backing store and in the result, it isn't possible to
   * discard or renew the authenticator simultaneously. This is because the "update" method would
   * override the result created by the "renew" or "discard" method, because it will be executed
   * as last in the chain.
   *
   * As example:
   * If we discard the session in a Silhouette action then it will be removed from session. But
   * at the end the update method will embed the session again, because it gets called with the
   * result of the action.
   */
  private[silhouette] var skipUpdate = false
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
