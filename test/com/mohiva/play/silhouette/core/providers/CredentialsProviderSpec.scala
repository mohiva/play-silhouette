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
package com.mohiva.play.silhouette.core.providers

import scala.concurrent.Future
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.{ PlaySpecification, WithApplication }
import com.mohiva.play.silhouette.core.{ AuthenticationException, AccessDeniedException, LoginInfo }
import com.mohiva.play.silhouette.core.services.AuthInfoService
import com.mohiva.play.silhouette.core.utils.PasswordHasher
import CredentialsProvider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.CredentialsProvider]] class.
 */
class CredentialsProviderSpec extends PlaySpecification with Mockito {

  "The `authenticate` method" should {
    "return failure if no auth info could be found for the given credentials" in new WithApplication with Context {
      val loginInfo = new LoginInfo(provider.id, credentials.identifier)
      val pattern = UnknownCredentials.format(provider.id).replaceAll("\\[.*\\]", "^.*")

      authInfoService.retrieve[PasswordInfo](loginInfo) returns Future.successful(None)

      await(provider.authenticate(credentials)) must beFailedTry.withThrowable[AccessDeniedException](pattern)
    }

    "return failure if passwords does not match" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("foo", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      val pattern = InvalidPassword.format(provider.id).replaceAll("\\[.*\\]", "^.*")

      fooHasher.matches(passwordInfo, credentials.password) returns false
      authInfoService.retrieve[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must beFailedTry.withThrowable[AccessDeniedException](pattern)
    }

    "return failure if unsupported hasher is stored" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("unknown", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      val pattern = UnsupportedHasher.format(provider.id, "unknown", "foo, bar").replaceAll("\\[.*\\]", "^.*")

      authInfoService.retrieve[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must beFailedTry.withThrowable[AuthenticationException](pattern)
    }

    "return success if passwords does match" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("foo", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      val pattern = InvalidPassword.format(provider.id).replaceAll("\\[.*\\]", "^.*")

      fooHasher.matches(passwordInfo, credentials.password) returns true
      authInfoService.retrieve[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must beSuccessfulTry.like {
        case result =>
          result must be equalTo loginInfo
      }
    }

    "re-hash password with new hasher" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("bar", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      val pattern = InvalidPassword.format(provider.id).replaceAll("\\[.*\\]", "^.*")

      fooHasher.hash(credentials.password) returns passwordInfo
      barHasher.matches(passwordInfo, credentials.password) returns true
      authInfoService.retrieve[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must beSuccessfulTry.like {
        case result =>
          result must be equalTo loginInfo
          there was one(authInfoService).save(loginInfo, passwordInfo)
      }
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The test credentials.
     */
    lazy val credentials = Credentials("apollonia.vanova@watchmen.com", "s3cr3t")

    /**
     * The default password hasher.
     */
    lazy val fooHasher = {
      val h = mock[PasswordHasher]
      h.id returns "foo"
      h
    }

    /**
     * An optional password hasher.
     */
    lazy val barHasher = {
      val h = mock[PasswordHasher]
      h.id returns "bar"
      h
    }

    /**
     * The auth info service mock.
     */
    lazy val authInfoService = mock[AuthInfoService]

    /**
     * The provider to test.
     */
    lazy val provider = new CredentialsProvider(authInfoService, fooHasher, List(fooHasher, barHasher))
  }
}
