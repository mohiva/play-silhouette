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
package com.mohiva.play.silhouette.api.util

import com.mohiva.play.silhouette.api.services.AuthInfo

/**
 * The password details.
 *
 * @param hasher The ID of the hasher used to hash this password.
 * @param password The hashed password.
 * @param salt The optional salt used when hashing.
 */
case class PasswordInfo(hasher: String, password: String, salt: Option[String] = None) extends AuthInfo

/**
 * A trait that defines the password hasher interface.
 */
trait PasswordHasher {

  /**
   * Gets the ID of the hasher.
   *
   * @return The ID of the hasher.
   */
  def id: String

  /**
   * Hashes a password.
   *
   * @param plainPassword The password to hash.
   * @return A PasswordInfo containing the hashed password and optional salt.
   */
  def hash(plainPassword: String): PasswordInfo

  /**
   * Checks whether a supplied password matches the hashed one.
   *
   * @param passwordInfo The password retrieved from the backing store.
   * @param suppliedPassword The password supplied by the user trying to log in.
   * @return True if the password matches, false otherwise.
   */
  def matches(passwordInfo: PasswordInfo, suppliedPassword: String): Boolean
}
