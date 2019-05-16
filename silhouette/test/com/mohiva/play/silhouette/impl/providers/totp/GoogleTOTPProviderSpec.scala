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
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordInfo}
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.TOTPProvider._
import com.mohiva.play.silhouette.impl.providers.totp.GoogleTOTPProvider._
import com.mohiva.play.silhouette.impl.providers.{TOTPInfo, TOTPProviderSpec}
import play.api.test.{FakeRequest, WithApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.totp.GoogleTOTPProvider#GoogleTOTPProvider]] class.
 */
class GoogleTOTPProviderSpec extends TOTPProviderSpec {

  "The `authenticate` method" should {

    "throw ProviderException if request doesn't contains required parameters" in new WithApplication with Context {
      implicit val req = FakeRequest()

      val loginInfo = new LoginInfo(provider.id, credentials.identifier)

      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(None)

      await(provider.authenticate()) must throwA[ProviderException].like {
        case e => e.getMessage must beEqualTo(IncorrectRequest.format(provider.id, requiredParams.mkString(",")))
      }
    }

    "throw IdentityNotFoundException if no auth info could be found for the given credentials" in new WithApplication with Context {
      implicit val req = FakeRequest(
        GET, "?" + providerKeyParam + "=" + credentials.identifier +
        "&" + verificationCodeParam + "=" + testVerificationCode)

      val loginInfo = new LoginInfo(provider.id, credentials.identifier)

      authInfoRepository.find[TOTPInfo](loginInfo) returns Future.successful(None)

      await(provider.authenticate()) must throwA[IdentityNotFoundException].like {
        case e =>
          e.getMessage must beEqualTo(TOTPInfoNotFound.format(provider.id, credentials.identifier))
      }
    }

    "throw ProviderException if verification code is not a number" in new WithApplication with Context {
      implicit val req = FakeRequest(
        GET, "?" + providerKeyParam + "=" + credentials.identifier +
        "&" + verificationCodeParam + "=" + testWrongVerificationCode)

      val loginInfo = new LoginInfo(provider.id, credentials.identifier)

      authInfoRepository.find[TOTPInfo](loginInfo) returns Future.successful(Some(TOTPInfo(testSharedKey)))

      await(provider.authenticate()) must throwA[ProviderException].like {
        case e =>
          e.getMessage must beEqualTo(VerificationCodeNotNumber.format(provider.id))
      }
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
    lazy val provider = new GoogleTOTPProvider(authInfoRepository)
  }
}
