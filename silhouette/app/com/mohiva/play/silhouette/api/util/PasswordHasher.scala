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
package com.mohiva.play.silhouette.api.util

import com.mohiva.play.silhouette.api.AuthInfo

/**
 * The password details.
 *
 * @param hasher The ID of the hasher used to hash this password.
 * @param password The hashed password.
 * @param salt The optional salt used when hashing.
 */
case class PasswordInfo(
  hasher: String,
  password: String,
  salt: Option[String] = None
) extends AuthInfo

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

  /**
   * Checks if a password hasher is suitable for the given password info.
   *
   * @param passwordInfo The password info to check the hasher is suitable for.
   * @return True if the hasher is suitable for the given password info, false otherwise.
   */
  def isSuitable(passwordInfo: PasswordInfo): Boolean = passwordInfo.hasher == id

  /**
   * Indicates if a password info hashed with this hasher is deprecated.
   *
   * A password can be deprecated if some internal state of a hasher has changed.
   *
   * @param passwordInfo The password info to check the deprecation status for.
   * @return True if the given password info is deprecated, false otherwise. If a hasher isn't
   *         suitable for the given password, this method should return None.
   */
  def isDeprecated(passwordInfo: PasswordInfo): Option[Boolean]
}

/**
 * Defines the password hashers used by the application.
 *
 * Sometimes it's needed to change the password hashing algorithm, because of a better algorithm or some
 * similar case. But the passwords stored in the backing store cannot easily be converted with another
 * algorithm because they're hashed and cannot be decrypted back to plain text. For such case Silhouette
 * supports the change of hashing algorithms on the fly. So if a user successfully authenticates after
 * the application has changed the hashing algorithm, the provider hashes the entered plain-text password
 * again with the new algorithm and overrides the auth info in the backing store with the new hash.
 *
 * The password hasher registry defines the current password hasher which is able to hash all new passwords
 * and also match the passwords store in the backing store for this algorithm. And also a list of deprecated
 * hashers, which should match passwords that are stored in the baking store but which are different to the
 * current hasher.
 *
 * @param current The current password hasher used by the application.
 * @param deprecated The deprecated list of password hashers.
 */
case class PasswordHasherRegistry(current: PasswordHasher, deprecated: Seq[PasswordHasher] = Seq()) {

  /**
   * Returns the complete list of supported password hashers.
   *
   * @return The complete list of supported password hashers.
   */
  def all = current +: deprecated

  /**
   * Finds the password hasher suitable for the given password info.
   *
   * First it checks if the current hasher is suitable for the given password hasher. As next it checks
   * if a deprecated password hasher is suitable for the given password info. If non of the registered
   * password hasher is suitable for the given password info, this method returns `None`.
   *
   * @param passwordInfo The password info to return a suitable password hasher for.
   * @return Maybe a suitable password hasher, otherwise None.
   */
  def find(passwordInfo: PasswordInfo): Option[PasswordHasher] = all.find(_.isSuitable(passwordInfo))

  /**
   * Indicates if a hasher is in the list of deprecated hashers.
   *
   * @param hasher The hasher to check the deprecation status for.
   * @return True if the given hasher is deprecated, false otherwise.
   */
  def isDeprecated(hasher: PasswordHasher): Boolean = deprecated.contains(hasher)
}
