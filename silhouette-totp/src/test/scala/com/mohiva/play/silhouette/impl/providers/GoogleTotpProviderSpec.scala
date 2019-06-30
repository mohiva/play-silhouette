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
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.util.{ Credentials, PasswordInfo }
import com.warrenstrange.googleauth.GoogleAuthenticator
import org.specs2.mock.Mockito
import play.api.test.WithApplication

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.GoogleTotpProvider#GoogleTOTPProvider]] class.
 */
class GoogleTotpProviderSpec extends PasswordProviderSpec with Mockito {
  "The `authenticate` with verification code method" should {
    "return None when the sharedKey is null or empty" in new WithApplication with Context {
      await(provider.authenticate(null.asInstanceOf[String], testVerificationCode)) should be(None)
      await(provider.authenticate("", testVerificationCode)) should be(None)
    }

    "return None when the verification code is null or empty" in new WithApplication with Context {
      await(provider.authenticate(testSharedKey, null)) should be(None)
      await(provider.authenticate(testSharedKey, "")) should be(None)
    }

    "return None when the verification code isn't a number" in new WithApplication with Context {
      await(provider.authenticate(testSharedKey, testWrongVerificationCode)) should be(None)
    }

    "return valid `Some(TotpInfo)` when the verification code is correct" in new WithApplication with Context {
      val googleAuthenticator = new GoogleAuthenticator()
      val validVerificationCode = googleAuthenticator.getTotpPassword(testSharedKey)
      await(provider.authenticate(testSharedKey, validVerificationCode.toString)) should not be empty
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

  "The `authenticate` with verification code method" should {
    "throw NullPointerException when the input totpInfo is null" in new WithApplication with Context {
      await(provider.authenticate(null.asInstanceOf[GoogleTotpInfo], testWrongVerificationCode)) must throwA[NullPointerException]
    }

    "return throw NullPointerException when the plain scratch code is null" in new WithApplication with Context {
      val result = provider.createCredentials(credentials.identifier)
      await(provider.authenticate(result.totpInfo, null.asInstanceOf[String])) must throwA[NullPointerException]
    }

    "return None when the plain scratch code is empty" in new WithApplication with Context {
      val result = provider.createCredentials(credentials.identifier)
      await(provider.authenticate(result.totpInfo, "")) should be(None)
    }

    "return Some(PasswordInfo,TotpInfo) when the plain scratch code is valid" in new WithApplication with Context {
      fooHasher.hash(any()) returns testPasswordInfo
      barHasher.matches(testPasswordInfo, testScratchCode) returns true
      val result = provider.createCredentials(credentials.identifier)
      await(provider.authenticate(result.totpInfo, testScratchCode)) should not be empty
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
     * The test scratch code.
     */
    lazy val testScratchCode = "somecode"

    /**
     * The test password info.
     */
    lazy val testPasswordInfo = PasswordInfo("bar", "hashed(somecode)")

    /**
     * The provider to test.
     */
    lazy val provider = new GoogleTotpProvider(passwordHasherRegistry)
  }
}
