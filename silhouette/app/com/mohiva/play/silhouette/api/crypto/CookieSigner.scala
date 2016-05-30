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
package com.mohiva.play.silhouette.api.crypto

import scala.util.Try

/**
 * Specifies a strategy how cookies should be signed with a MAC.
 */
trait CookieSigner {

  /**
   * Signs (MAC) the given data using the given secret key.
   *
   * @param data The data to sign.
   * @return A message authentication code.
   */
  def sign(data: String): String

  /**
   * Extracts a message that was signed by [[CookieSigner.sign]].
   *
   * @param message The signed message to extract.
   * @return The verified raw data, or an error if the message isn't valid.
   */
  def extract(message: String): Try[String]
}
