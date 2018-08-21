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
package com.mohiva.play.silhouette.password

import com.mohiva.play.silhouette.api.util.{ PasswordHasher, PasswordInfo }
import Argon2PasswordHasher._
import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2Helper

class Argon2PasswordHasher(maxMilliSecs: Long = 1000, memory: Int = 65536, parallelism: Int = 1) extends PasswordHasher {
  /**
   * Gets the ID of the hasher.
   *
   * @return The ID of the hasher.
   */
  override def id: String = ID

  private val argon2 = Argon2Factory.create()
  private val iterations: Int = Argon2Helper.findIterations(argon2, maxMilliSecs, memory, parallelism)
  private val hasherPrefix = prefixPattern(argon2.hash(iterations, memory, parallelism, ""))

  /**
   * Hashes a password.
   *
   * @param plainPassword The password to hash.
   * @return A PasswordInfo containing the hashed password and optional salt.
   */
  override def hash(plainPassword: String): PasswordInfo = PasswordInfo(
    hasher = id,
    password = argon2.hash(iterations, memory, parallelism, plainPassword)
  )

  /**
   * Checks whether a supplied password matches the hashed one.
   *
   * @param passwordInfo     The password retrieved from the backing store.
   * @param suppliedPassword The password supplied by the user trying to log in.
   * @return True if the password matches, false otherwise.
   */
  override def matches(passwordInfo: PasswordInfo, suppliedPassword: String): Boolean = argon2.verify(passwordInfo.password, suppliedPassword)

  /**
   * Indicates if a password info hashed with this hasher is deprecated.
   *
   * A password can be deprecated if some internal state of a hasher has changed.
   *
   * @param passwordInfo The password info to check the deprecation status for.
   * @return True if the given password info is deprecated, false otherwise. If a hasher isn't
   *         suitable for the given password, this method should return None.
   */
  override def isDeprecated(passwordInfo: PasswordInfo): Option[Boolean] = Option(isSuitable(passwordInfo)).collect {
    case true =>
      // Is deprecated if the hasher prefix has changed
      prefixPattern(passwordInfo.password) != hasherPrefix
  }
}

/**
 * The companion object.
 */
object Argon2PasswordHasher {
  /**
   * The ID of the hasher.
   */
  val ID = "argon2"

  /**
   * The pattern to extract the argon params from the password string.
   */
  def prefixPattern(hash: String) = hash.split("\\$").slice(1, 4).mkString(",")
}
