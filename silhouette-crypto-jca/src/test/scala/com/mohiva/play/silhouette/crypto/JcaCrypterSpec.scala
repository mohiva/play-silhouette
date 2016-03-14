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

import com.mohiva.play.silhouette.api.exceptions.CryptoException
import com.mohiva.play.silhouette.crypto.JcaCrypter._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

/**
 * Test case for the [[JcaCrypter]] class.
 */
class JcaCrypterSpec extends Specification {

  "The `decrypt` method" should {
    "throw a CryptoException if the format is unexpected" in new Context {
      encoder.decrypt("data") must throwA[CryptoException].like {
        case e =>
          e.getMessage must be equalTo UnexpectedFormat
      }
    }

    "throw a CryptoException if the version is unknown" in new Context {
      encoder.decrypt("2-data") must throwA[CryptoException].like {
        case e =>
          e.getMessage must be equalTo UnknownVersion.format(2)
      }
    }
  }

  "The crypter" should {
    "encrypt/decrypt a string" in new Context {
      val text = "Silhouette rocks"
      encoder.decrypt(encoder.encrypt(text)) must be equalTo text
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The encryption key.
     */
    val key = "s3cr3t_k3y"

    /**
     * The settings instance.
     */
    val settings = new JcaCrypterSettings(key)

    /**
     * The crypter to test.
     */
    val encoder = new JcaCrypter(settings)
  }
}
