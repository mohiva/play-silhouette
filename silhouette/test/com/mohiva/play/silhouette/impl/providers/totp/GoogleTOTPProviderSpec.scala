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
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordInfo}
import com.mohiva.play.silhouette.impl.providers.TOTPProviderSpec
import play.api.test.{FakeRequest, WithApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.totp.GoogleTOTPProvider#GoogleTOTPProvider]] class.
 */
class GoogleTOTPProviderSpec extends TOTPProviderSpec {

  "The `authenticate` method" should {

    "return None if no auth info could be found for the given credentials" in new WithApplication with Context {
      val loginInfo = new LoginInfo(provider.id, credentials.identifier)

      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(None)

      await(provider.authenticate()) must beNone
    }
  }

  /**
   * The context.
   */
  trait Context extends BaseContext {

    implicit val req = FakeRequest()

    /**
     * The test credentials.
     */
    lazy val credentials = Credentials("apollonia.vanova@watchmen.com", "s3cr3t")

    /**
     * The provider to test.
     */
    lazy val provider = new GoogleTOTPProvider(authInfoRepository)
  }
}
