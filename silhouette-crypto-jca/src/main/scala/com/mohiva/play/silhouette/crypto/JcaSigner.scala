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

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.mohiva.play.silhouette.api.crypto.Signer
import com.mohiva.play.silhouette.api.exceptions.CryptoException
import org.apache.commons.codec.binary.Hex
import JcaSigner._
import scala.util.{ Failure, Success, Try }

/**
 * Signer implementation based on JCA (Java Cryptography Architecture).
 *
 * This signer signs the data with the specified key. If the signature verification fails, the signer
 * does not try to decode the data in any way in order to prevent various types of attacks.
 *
 * @param settings The settings instance.
 */
class JcaSigner(settings: JcaSignerSettings) extends Signer {

  /**
   * Signs (MAC) the given data using the given secret key.
   *
   * @param data The data to sign.
   * @return A message authentication code.
   */
  override def sign(data: String): String = {
    val message = settings.pepper + data + settings.pepper
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(settings.key.getBytes("UTF-8"), "HmacSHA1"))
    val signature = Hex.encodeHexString(mac.doFinal(message.getBytes("UTF-8")))
    val version = 1
    s"$version-$signature-$data"
  }

  /**
   * Extracts a message that was signed by the `sign` method.
   *
   * @param message The signed message to extract.
   * @return The verified raw data, or an error if the message isn't valid.
   */
  override def extract(message: String): Try[String] = {
    for {
      (_, actualSignature, actualData) <- fragment(message)
      (_, expectedSignature, _) <- fragment(sign(actualData))
    } yield {
      if (constantTimeEquals(expectedSignature, actualSignature)) {
        actualData
      } else {
        throw new CryptoException(BadSignature)
      }
    }
  }

  /**
   * Fragments the message into its parts.
   *
   * @param message The message to fragment.
   * @return The message parts.
   */
  private def fragment(message: String): Try[(String, String, String)] = {
    message.split("-", 3) match {
      case Array(version, signature, data) if version == "1" => Success((version, signature, data))
      case Array(version, _, _) => Failure(new CryptoException(UnknownVersion.format(version)))
      case _ => Failure(new CryptoException(InvalidMessageFormat))
    }
  }

  /**
   * Constant time equals method.
   *
   * Given a length that both Strings are equal to, this method will always run in constant time. This prevents
   * timing attacks.
   */
  private def constantTimeEquals(a: String, b: String): Boolean = {
    if (a.length != b.length) {
      false
    } else {
      var equal = 0
      for (i <- 0 until a.length) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }
  }
}

/**
 * The companion object.
 */
object JcaSigner {

  val BadSignature = "[Silhouette][JcaSigner] Bad signature"
  val UnknownVersion = "[Silhouette][JcaSigner] Unknown version: %s"
  val InvalidMessageFormat = "[Silhouette][JcaSigner] Invalid message format; Expected [VERSION]-[SIGNATURE]-[DATA]"
}

/**
 * The settings for the JCA signer.
 *
 * @param key    Key for signing.
 * @param pepper Constant prepended and appended to the data before signing. When using one key for multiple purposes,
 *               using a specific pepper reduces some risks arising from this.
 */
case class JcaSignerSettings(key: String, pepper: String = "-mohiva-silhouette-signer-")
