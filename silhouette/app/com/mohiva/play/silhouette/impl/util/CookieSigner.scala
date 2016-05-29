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
package com.mohiva.play.silhouette.impl.util

import com.mohiva.play.silhouette.api.exceptions.CryptoException
import com.mohiva.play.silhouette.impl.util.CookieSigner._
import play.api.libs.Crypto

import scala.util.{ Failure, Success, Try }

/**
 * Cookie signer implementation based on the Play cryptography functionality.
 *
 * This cookie signer signs the data with the specified key or with the Play application secret. If the
 * signature verification fails, the signer does not try to decode the cookie data in any way in order
 * to prevent various types of attacks.
 *
 * @param keyOption Key for signing. When None is supplied, application.secret is used. Note that using
 *                  application.secret is discouraged (because it might cause using one key for multiple purposes) and
 *                  should be used when required by backward compatibility.
 * @param pepper Constant prepended and appended to the data before signing. When using one key for multiple purposes,
 *               using a specific pepper reduces some risks arising from this.
 */
class CookieSigner(keyOption: Option[Array[Byte]], pepper: String = "-mohiva-silhouette-cookie-signer-") {

  /**
   * Signs (MAC) the given data using the given secret key.
   *
   * @param data The data to sign.
   * @return A message authentication code.
   */
  def sign(data: String): String = {
    val message = pepper + data + pepper
    val signature = keyOption match {
      case None => Crypto.sign(message)
      case Some(key) => Crypto.sign(message, key)
    }

    val version = 1
    s"$version-$signature-$data"
  }

  /**
   * Extracts a message that was signed by [[CookieSigner.sign]].
   *
   * @param message The signed message to extract.
   * @return The verified raw data, or an error if the message isn't valid.
   */
  def extract(message: String): Try[String] = {
    for {
      (_, actualSignature, actualData) <- fragment(message)
      (_, expectedSignature, _) <- fragment(sign(actualData))
    } yield {
      if (Crypto.constantTimeEquals(expectedSignature, actualSignature)) {
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
}

/**
 * The companion object.
 */
object CookieSigner {

  val BadSignature = "[Silhouette][CookieSigner] Bad signature"
  val UnknownVersion = "[Silhouette][CookieSigner] Unknown version: %s"
  val InvalidMessageFormat = "[Silhouette][CookieSigner] Invalid message format; Expected [VERSION]-[SIGNATURE]-[DATA]"
}
