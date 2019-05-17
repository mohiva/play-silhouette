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

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.providers.TotpProvider._
import com.mohiva.play.silhouette.impl.providers.TOTPProviderSpec
import com.mohiva.play.silhouette.impl.providers.totp.GoogleTotpProvider._
import play.api.test.{ FakeRequest, WithApplication }

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.totp.GoogleTotpProvider#GoogleTOTPProvider]] class.
 */
class GoogleTOTPProviderSpec extends TOTPProviderSpec {

  "The `authenticate` method" should {

    "throw ProviderException if request doesn't contains required parameters" in new WithApplication with Context {
      implicit val req = FakeRequest()

      val loginInfo = new LoginInfo(provider.id, credentials.identifier)

      await(provider.authenticate()) must throwA[ProviderException].like {
        case e => e.getMessage must beEqualTo(IncorrectRequest.format(provider.id, requiredParams.mkString(",")))
      }
    }

    "throw ProviderException if verification code is not a number" in new WithApplication with Context {
      implicit val req = FakeRequest(
        GET, "?" + sharedKeyParam + "=" + testSharedKey +
        "&" + verificationCodeParam + "=" + testWrongVerificationCode)

      val loginInfo = new LoginInfo(provider.id, credentials.identifier)

      await(provider.authenticate()) must throwA[ProviderException].like {
        case e =>
          e.getMessage must beEqualTo(VerificationCodeNotNumber.format(provider.id))
      }
    }
  }

  "The `generateKeyHolder` method" should {

    "return correct key TotpKeyHolder" in new WithApplication with Context {
      val keyHolder = provider.generateKeyHolder(credentials.identifier)
      keyHolder.sharedKey must not be empty
      keyHolder.qrUrl must not be empty
      keyHolder.scratchCodes must not be empty
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
