/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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

import akka.util.Crypt
import play.api.test.{ FakeRequest, PlaySpecification }

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.util.DefaultFingerprintGenerator]] class.
 */
class DefaultFingerprintGeneratorSpec extends PlaySpecification {

  "The generator" should {
    "return fingerprint including the `User-Agent` header" in {
      val userAgent = "test-user-agent"
      val generator = new DefaultFingerprintGenerator()
      implicit val request = FakeRequest().withHeaders(USER_AGENT -> userAgent)

      generator.generate must be equalTo Crypt.sha1(userAgent + "::")
    }

    "return fingerprint including the `Accept-Language` header" in {
      val acceptLanguage = "test-accept-language"
      val generator = new DefaultFingerprintGenerator()
      implicit val request = FakeRequest().withHeaders(ACCEPT_LANGUAGE -> acceptLanguage)

      generator.generate must be equalTo Crypt.sha1(":" + acceptLanguage + ":")
    }

    "return fingerprint including the remote address" in {
      val generator = new DefaultFingerprintGenerator(true)
      implicit val request = FakeRequest()

      generator.generate must be equalTo Crypt.sha1("::127.0.0.1")
    }

    "return fingerprint including all values" in {
      val userAgent = "test-user-agent"
      val acceptLanguage = "test-accept-language"
      val generator = new DefaultFingerprintGenerator(true)
      implicit val request = FakeRequest().withHeaders(USER_AGENT -> userAgent, ACCEPT_LANGUAGE -> acceptLanguage)

      generator.generate must be equalTo Crypt.sha1(userAgent + ":" + acceptLanguage + ":127.0.0.1")
    }
  }
}
