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
package com.mohiva.play.silhouette.impl.util

import com.mohiva.play.silhouette.api.util.PasswordInfo
import play.api.test.PlaySpecification

/**
 * Test case for the [[BCryptPasswordHasher]] class.
 */
class BCryptPasswordHasherSpec extends PlaySpecification {

  "The `hash` method" should {
    "hash a password" in {
      val password = "my_S3cr3t_p@sswQrd"
      val hasher = new BCryptPasswordHasher
      val info = hasher.hash(password)

      info must beAnInstanceOf[PasswordInfo]
      info.hasher must be equalTo BCryptPasswordHasher.ID
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
}
