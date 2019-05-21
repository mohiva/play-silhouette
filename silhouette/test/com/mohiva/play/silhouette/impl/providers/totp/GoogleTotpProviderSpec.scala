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
package com.mohiva.play.silhouette.impl.providers.totp

import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.TotpProviderSpec
import play.api.test.{ FakeRequest, WithApplication }

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.totp.GoogleTotpProvider#GoogleTOTPProvider]] class.
 */
class GoogleTotpProviderSpec extends TotpProviderSpec {
  "The `authenticate` method" should {
    "return None when the verification code is null or empty" in new WithApplication with Context {
      implicit val req = FakeRequest()
      await(provider.authenticate(testSharedKey, null)) should be(None)
      await(provider.authenticate(testSharedKey, "")) should be(None)
    }

    "return None when the sharedKey is null" in new WithApplication with Context {
      implicit val req = FakeRequest()
      await(provider.authenticate(null, testVerificationCode)) should be(None)
    }

    "return None when the verification code isn't a number" in new WithApplication with Context {
      await(provider.authenticate(testSharedKey, testWrongVerificationCode)) should be(None)
    }
  }

  "The `createCredentials` method" should {
    "return the correct TotpCredentials shared key" in new WithApplication with Context {
      val result = provider.createCredentials(credentials.identifier)
      result.totpInfo.sharedKey must not be empty
      result.totpInfo.scratchCodes must not be empty
      result.qrUrl must not be empty
    }
  }

  /**
   * The context.
   */
  trait Context extends BaseContext {
    /**
     * The test credentials.
     */
    lazy val credentials = Credentials("apollonia.vanova@watchmen.com", "s3cr3t")

    /**
     * The test shared key.
     */
    lazy val testSharedKey = "qwerty123"

    /**
     * The test verification code.
     */
    lazy val testVerificationCode = "123456"

    /**
     * The test wrong verification code.
     */
    lazy val testWrongVerificationCode = "q123456"

    /**
     * The provider to test.
     */
    lazy val provider = new GoogleTotpProvider()
  }
}
