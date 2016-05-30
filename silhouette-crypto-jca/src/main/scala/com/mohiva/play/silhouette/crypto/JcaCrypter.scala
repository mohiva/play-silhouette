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
package com.mohiva.play.silhouette.crypto

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.{ IvParameterSpec, SecretKeySpec }

import com.mohiva.play.silhouette.api.crypto.Crypter
import com.mohiva.play.silhouette.api.exceptions.CryptoException
import com.mohiva.play.silhouette.crypto.JcaCrypter._

/**
 * Crypter implementation based on JCA (Java Cryptography Architecture).
 *
 * The algorithm used in this implementation is `AES/CTR/NoPadding`. Beware that CTR is
 * [[https://en.wikipedia.org/wiki/Malleability_%28cryptography%29 malleable]], which might
 * be abused for various attacks if messages are not properly
 * [[https://en.wikipedia.org/wiki/Malleability_%28cryptography%29 authenticated]].
 *
 * @param settings The settings instance.
 */
class JcaCrypter(settings: JcaCrypterSettings) extends Crypter {

  /**
   * Encrypts a string.
   *
   * @param value The plain text to encrypt.
   * @return The encrypted string.
   */
  override def encrypt(value: String): String = {
    val keySpec = secretKeyWithSha256(settings.key, "AES")
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    val encryptedValue = cipher.doFinal(value.getBytes("UTF-8"))
    val version = 1
    Option(cipher.getIV) match {
      case Some(iv) => s"$version-${Base64.getEncoder.encodeToString(iv ++ encryptedValue)}"
      case None     => throw new CryptoException(UnderlyingIVBug)
    }
  }

  /**
   * Decrypts a string.
   *
   * @param value The value to decrypt.
   * @return The plain text string.
   */
  override def decrypt(value: String): String = {
    value.split("-", 2) match {
      case Array(version, data) if version == "1" => decryptVersion1(data, settings.key)
      case Array(version, _)                      => throw new CryptoException(UnknownVersion.format(version))
      case v                                      => throw new CryptoException(UnexpectedFormat)
    }
  }

  /**
   * Generates the SecretKeySpec, given the private key and the algorithm.
   */
  private def secretKeyWithSha256(privateKey: String, algorithm: String) = {
    val messageDigest = MessageDigest.getInstance("SHA-256")
    messageDigest.update(privateKey.getBytes("UTF-8"))
    // max allowed length in bits / (8 bits to a byte)
    val maxAllowedKeyLength = Cipher.getMaxAllowedKeyLength(algorithm) / 8
    val raw = messageDigest.digest().slice(0, maxAllowedKeyLength)
    new SecretKeySpec(raw, algorithm)
  }

  /**
   * V1 decryption algorithm (AES/CTR/NoPadding - IV present).
   */
  private def decryptVersion1(value: String, privateKey: String): String = {
    val data = Base64.getDecoder.decode(value)
    val keySpec = secretKeyWithSha256(privateKey, "AES")
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    val blockSize = cipher.getBlockSize
    val iv = data.slice(0, blockSize)
    val payload = data.slice(blockSize, data.size)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv))
    new String(cipher.doFinal(payload), "UTF-8")
  }
}

/**
 * The companion object.
 */
object JcaCrypter {

  val UnderlyingIVBug = "[Silhouette][JcaCrypter] Cannot get IV! There must be a bug in your underlying JCE " +
    "implementation; The AES/CTR/NoPadding transformation should always provide an IV"
  val UnexpectedFormat = "[Silhouette][JcaCrypter] Unexpected format; expected [VERSION]-[ENCRYPTED STRING]"
  val UnknownVersion = "[Silhouette][JcaCrypter] Unknown version: %s"
}

/**
 * The settings for the JCA crypter.
 *
 * @param key The encryption key.
 */
case class JcaCrypterSettings(key: String)
