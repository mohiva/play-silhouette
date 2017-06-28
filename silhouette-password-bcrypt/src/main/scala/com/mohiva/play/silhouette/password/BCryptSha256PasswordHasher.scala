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
package com.mohiva.play.silhouette.password

import com.mohiva.play.silhouette.api.crypto.Hash
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.password.BCryptSha256PasswordHasher._
import org.mindrot.jbcrypt.BCrypt

/**
 * Implementation of the password hasher based on BCrypt.
 *
 * The designers of bcrypt truncate all passwords at 72 characters which means that `bcrypt(password_with_100_chars) ==
 * bcrypt(password_with_100_chars[:72])`. The original `BCryptPasswordHasher` does not have any special handling and
 * thus is also subject to this hidden password length limit. `BCryptSha256PasswordHasher` fixes this by first hashing
 * the password using sha256. This prevents the password truncation and so should be preferred over the
 * `BCryptPasswordHasher`. The practical ramification of this truncation is pretty marginal as the average user does
 * not have a password greater than 72 characters in length and even being truncated at 72 the compute powered required
 * to brute force bcrypt in any useful amount of time is still astronomical. Nonetheless, we recommend you use
 * `BCryptSha256PasswordHasher` anyway on the principle of "better safe than sorry".
 *
 * @param logRounds The log2 of the number of rounds of hashing to apply.
 * @see [[http://www.mindrot.org/files/jBCrypt/jBCrypt-0.2-doc/BCrypt.html#gensalt(int) gensalt]]
 * @see https://docs.djangoproject.com/en/1.10/topics/auth/passwords/#using-bcrypt-with-django
 * @see https://crypto.stackexchange.com/questions/24993/is-there-a-way-to-use-bcrypt-with-passwords-longer-than-72-bytes-securely
 */
class BCryptSha256PasswordHasher(logRounds: Int = 10) extends BCryptPasswordHasher(logRounds) {

  /**
   * Gets the ID of the hasher.
   *
   * @return The ID of the hasher.
   */
  override def id = ID

  /**
   * Hashes a password.
   *
   * This implementation does not return the salt separately because it is embedded in the hashed password.
   * Other implementations might need to return it so it gets saved in the backing store.
   *
   * @param plainPassword The password to hash.
   * @return A PasswordInfo containing the hashed password.
   */
  override def hash(plainPassword: String) = PasswordInfo(
    hasher = id,
    password = BCrypt.hashpw(Hash.sha2(plainPassword), BCrypt.gensalt(logRounds))
  )

  /**
   * Checks if a password matches the hashed version.
   *
   * @param passwordInfo The password retrieved from the backing store.
   * @param suppliedPassword The password supplied by the user trying to log in.
   * @return True if the password matches, false otherwise.
   */
  override def matches(passwordInfo: PasswordInfo, suppliedPassword: String) = {
    BCrypt.checkpw(Hash.sha2(suppliedPassword), passwordInfo.password)
  }
}

/**
 * The companion object.
 */
object BCryptSha256PasswordHasher {

  /**
   * The ID of the hasher.
   */
  val ID = "bcrypt-sha256"
}
