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

/**
 * Crypter interface.
 *
 * This trait provides a generic encryption/decryption interface for the core, for which a concrete
 * implementation can be provided in userland.
 *
 * It's not guaranteed that the concrete implementations are compatible to each other. This means that
 * they cannot act as drop-in replacements.
 */
trait Crypter {

  /**
   * Encrypts a string.
   *
   * @param value The plain text to encrypt.
   * @return The encrypted string.
   */
  def encrypt(value: String): String

  /**
   * Decrypts a string.
   *
   * @param value The value to decrypt.
   * @return The plain text string.
   */
  def decrypt(value: String): String
}
