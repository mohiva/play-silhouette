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
package com.mohiva.play.silhouette.password

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import org.mockito.Matchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

/**
 * Test case for the [[BCryptPasswordHasher]] class.
 */
class BCryptPasswordHasherSpec extends Specification with Mockito {

  "The `hash` method" should {
    "hash a password" in {
      val password = "my_S3cr3t_p@sswQrd"
      val logRounds = 10
      val hasher = new BCryptPasswordHasher(logRounds)
      val info = hasher.hash(password)

      info must beAnInstanceOf[PasswordInfo]
      info.hasher must be equalTo BCryptPasswordHasher.ID + logRounds
      info.password must not be equalTo(password)
      info.salt must beNone
    }
  }

  "The `matches` method" should {
    "return true if a password matches a previous hashed password" in {
      val password = "my_S3cr3t_p@sswQrd"
      val hasher = new BCryptPasswordHasher
      val info = hasher.hash(password)

      hasher.matches(info, password) must beTrue
    }

    "return false if a password doesn't match a previous hashed password" in {
      val password = "my_S3cr3t_p@sswQrd"
      val hasher = new BCryptPasswordHasher
      val info = hasher.hash(password)

      hasher.matches(info, "not-equal") must beFalse
    }
  }

  "The `authenticate` method of CredentialsProvider" should {
    "re-hash password with new hasher" in {
      val credentials = Credentials("apollonia.vanova@watchmen.com", "s3cr3t")
      val authInfoRepository = mock[AuthInfoRepository]
      val oldHasher = new BCryptPasswordHasher(5)
      val newHasher = new BCryptPasswordHasher(7)
      val provider = new CredentialsProvider(authInfoRepository, newHasher, List(newHasher, oldHasher))
      val loginInfo = LoginInfo(provider.id, credentials.identifier)
      val passwordInfo = oldHasher.hash(credentials.password)

      authInfoRepository.find[PasswordInfo](loginInfo) returns Future.successful(Some(passwordInfo))
      authInfoRepository.update[PasswordInfo](Matchers.eq(loginInfo), Matchers.anyObject()) returns Future.successful(passwordInfo)

      Await.result(provider.authenticate(credentials), 5.seconds) must be equalTo loginInfo
      there was one(authInfoRepository).update(Matchers.eq(loginInfo), Matchers.anyObject())
    }
  }
}
