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
package com.mohiva.play.silhouette.contrib.utils

import play.api.libs.Codecs
import java.security.SecureRandom
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.core.utils.IDGenerator

/**
 * A generator which uses SecureRandom to generate cryptographically strong IDs.
 *
 * @param idSizeInBytes The size of the ID length in bytes.
 */
class SecureRandomIDGenerator(idSizeInBytes: Int = 128) extends IDGenerator {

  /**
   * Generates a new ID using SecureRandom.
   *
   * @return The generated ID.
   */
  def generate: Future[String] = {
    val randomValue = new Array[Byte](idSizeInBytes)
    Future(SecureRandomIDGenerator.random.nextBytes(randomValue)).map { _ =>
      Codecs.toHexString(randomValue)
    }
  }
}

/**
 * The companion object.
 */
object SecureRandomIDGenerator {

  /**
   * A cryptographically strong random number generator (RNG).
   *
   * There is a cost of getting a secure random instance for its initial seeding, so it's recommended you use
   * a singleton style so you only create one for all of your usage going forward.
   *
   * On Linux systems SecureRandom uses /dev/random and it can block waiting for sufficient entropy to build up.
   */
  val random = new SecureRandom()
}
