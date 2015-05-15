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

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{ Credentials, PasswordHasher, PasswordInfo }
import com.mohiva.play.silhouette.impl.exceptions.{ IdentityNotFoundException, InvalidPasswordException }
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider._
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.{ PlaySpecification, WithApplication }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.CredentialsProvider]] class.
 */
class CredentialsProviderSpec extends PlaySpecification with Mockito {

  "The `authenticate` method" should {
    "throw IdentityNotFoundException if no auth info could be found for the given credentials" in new WithApplication with Context {
      val loginInfo = new LoginInfo(provider.id, credentials.identifier)

      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(None)

      await(provider.authenticate(credentials)) must throwA[IdentityNotFoundException].like {
        case e => e.getMessage must beEqualTo(UnknownCredentials.format(provider.id))
      }
    }

    "throw InvalidPasswordException if passwords does not match" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("foo", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)

      fooHasher.matches(passwordInfo, credentials.password) returns false
      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must throwA[InvalidPasswordException].like {
        case e => e.getMessage must beEqualTo(InvalidPassword.format(provider.id))
      }
    }

    "throw ConfigurationException if unsupported hasher is stored" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("unknown", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)

      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must throwA[ConfigurationException].like {
        case e => e.getMessage must beEqualTo(UnsupportedHasher.format(provider.id, "unknown", "foo, bar"))
      }
    }

    "return login info if passwords does match" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("foo", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)

      fooHasher.matches(passwordInfo, credentials.password) returns true
      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must be equalTo loginInfo
    }

    "re-hash password with new hasher" in new WithApplication with Context {
      val passwordInfo = PasswordInfo("bar", "hashed(s3cr3t)")
      val loginInfo = LoginInfo(provider.id, credentials.identifier)

      fooHasher.hash(credentials.password) returns passwordInfo
      barHasher.matches(passwordInfo, credentials.password) returns true
      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))

      await(provider.authenticate(credentials)) must be equalTo loginInfo
      there was one(authInfoRepository).update(loginInfo, passwordInfo)
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
     * The auth info repository mock.
     */
    lazy val authInfoRepository = mock[AuthInfoRepository]

    /**
     * The provider to test.
     */
    lazy val provider = new CredentialsProvider(authInfoRepository, fooHasher, List(fooHasher, barHasher))
  }
}
