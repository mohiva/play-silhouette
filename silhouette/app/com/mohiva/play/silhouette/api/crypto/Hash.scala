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

import java.security.MessageDigest

/**
 * Hash helper.
 */
object Hash {

  /**
   * Creates a SHA1 hash from the given string.
   *
   * @param str The string to create a hash from.
   * @return The SHA1 hash of the string.
   */
  def sha1(str: String): String = sha1(str.getBytes("UTF-8"))

  /**
   * Creates a SHA1 hash from the given byte array.
   *
   * @param bytes The bytes to create a hash from.
   * @return The SHA1 hash of the bytes.
   */
  def sha1(bytes: Array[Byte]): String = {
    MessageDigest.getInstance("SHA-1").digest(bytes).map("%02x".format(_)).mkString
  }

  /**
   * Creates a SHA2 hash from the given string.
   *
   * @param str The string to create a hash from.
   * @return The SHA2 hash of the string.
   */
  def sha2(str: String): String = sha2(str.getBytes("UTF-8"))

  /**
   * Creates a SHA2 hash from the given byte array.
   *
   * @param bytes The bytes to create a hash from.
   * @return The SHA2 hash of the bytes.
   */
  def sha2(bytes: Array[Byte]): String = {
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString
  }
}
