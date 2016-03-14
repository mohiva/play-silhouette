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

import javax.inject.Inject

/**
 * Specifies encoding/decoding of authenticator data.
 */
trait AuthenticatorEncoder {

  /**
   * Encodes a string.
   *
   * @param data The data to encode.
   * @return The encoded data.
   */
  def encode(data: String): String

  /**
   * Decodes a string.
   *
   * @param data The data to decode.
   * @return The decoded data.
   */
  def decode(data: String): String
}

/**
 * Authenticator encoder implementation based on Base64.
 */
class Base64AuthenticatorEncoder extends AuthenticatorEncoder {
  override def encode(data: String): String = Base64.encode(data)
  override def decode(data: String): String = Base64.decode(data)
}

/**
 * Authenticator encoder implementation based on the [[Crypter]].
 *
 * @param crypter The crypter instance to use for the encoder.
 */
class CrypterAuthenticatorEncoder @Inject() (crypter: Crypter) extends AuthenticatorEncoder {
  override def encode(data: String): String = crypter.encrypt(data)
  override def decode(data: String): String = crypter.decrypt(data)
}
