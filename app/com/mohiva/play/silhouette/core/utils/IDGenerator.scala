/**
 * Copyright 2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 * Copyright 2014 Christian Kaps (christian.kaps at mohiva dot com)
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
 *
 * This file contains source code from the Secure Social project:
 * http://securesocial.ws/
 */
package com.mohiva.play.silhouette.core.utils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.Codecs
import java.security.SecureRandom

/**
 * An generator which creates an ID.
 */
trait IDGenerator {

  /**
   * Generates an ID.
   *
   * Generating secure ID's can block the application, while the system waits for resources. Therefore we
   * return a future so that the application doesn't get blocked while waiting for the generated ID.
   *
   * @return The generated ID.
   */
  def generate: Future[String]
}

/**
 * An generator which uses SecureRandom to generate cryptographically strong ID's.
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
