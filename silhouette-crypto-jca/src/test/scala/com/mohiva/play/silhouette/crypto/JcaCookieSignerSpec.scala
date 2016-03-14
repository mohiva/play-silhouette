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

import java.util.regex.Pattern

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import JcaCookieSigner._
import com.mohiva.play.silhouette.api.exceptions.CryptoException

/**
 * Test case for the [[JcaCookieSigner]] class.
 */
class JcaCookieSignerSpec extends Specification {

  "The `sign` method" should {
    "return a signed message in the form [VERSION]-[SIGNATURE]-[DATA]" in new Context {
      val data = "some-data"

      signer.sign(data) must beMatching(s"1-[^-]+-$data")
    }
  }

  "The `extract` method" should {
    "throw a `CryptoException` if the message format is invalid" in new Context {
      val msg = "^" + Pattern.quote(InvalidMessageFormat) + ".*"

      signer.extract("invalid") must beFailedTry.withThrowable[CryptoException](msg)
    }

    "throw a `CryptoException` if the version is unknown" in new Context {
      val msg = "^" + Pattern.quote(UnknownVersion.format(2)) + ".*"

      signer.extract("2-signature-data") must beFailedTry.withThrowable[CryptoException](msg)
    }

    "throw a `CryptoException` if the signature is invalid" in new Context {
      val msg = "^" + Pattern.quote(BadSignature) + ".*"

      signer.extract("1-signature-data") must beFailedTry.withThrowable[CryptoException](msg)
    }

    "extract a previously signed message" in new Context {
      val data = "some-data"
      val message = signer.sign(data)

      signer.extract(message) must beSuccessfulTry.withValue(data)
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
    val settings = new JcaCookieSignerSettings(key)

    /**
     * The cookie signer to test.
     */
    val signer = new JcaCookieSigner(settings)
  }
}
