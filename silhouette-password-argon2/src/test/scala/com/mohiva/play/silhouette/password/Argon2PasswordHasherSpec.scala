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

import com.mohiva.play.silhouette.api.util.PasswordInfo
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

/**
 * Test case for the [[Argon2PasswordHasher]] class.
 */
class Argon2PasswordHasherSpec extends Specification {

  "The `hash` method" should {
    "hash a password" in new Context {
      val passwordInfo: PasswordInfo = hasher.hash(password)

      passwordInfo.hasher must be equalTo Argon2PasswordHasher.ID
      passwordInfo.password must not be equalTo(password)
      passwordInfo.salt must beNone
    }
  }

  "The `matches` method" should {
    "return true if a password matches a previous hashed password" in new Context {
      val passwordInfo = hasher.hash(password)

      hasher.matches(passwordInfo, password) must beTrue
    }

    "return true if a password matches a previous hardcoded password" in new Context {
      val passwordInfo = PasswordInfo("argon2", "$argon2i$v=19$m=65536,t=10,p=1$flHQVtRgYQ3MC+i8KbMbug$jW2TOQctiXf8LXoCT3oVMii50sccv+9PpGY9jIdV+R4")

      hasher.matches(passwordInfo, password) must beTrue
    }

    "return false if a password doesn't match a previous hashed password" in new Context {
      val passwordInfo = hasher.hash(password)

      hasher.matches(passwordInfo, "not-equal") must beFalse
    }

    "accurately match passwords greater than 72 characters" in new Context {
      val passwordInfo = hasher.hash("a" * 80)

      hasher.matches(passwordInfo, "a" * 80) must beTrue
      hasher.matches(passwordInfo, "a" * 79) must beFalse
    }
  }

  "The `isSuitable` method" should {
    "return true if the hasher is suitable for the given password info" in new Context {
      val passwordInfo = hasher.hash(password)

      hasher.isSuitable(passwordInfo) must beTrue
    }

    "return true if the hasher is suitable when given a password info with different log rounds" in new Context {
      val currentHasher = new Argon2PasswordHasher(maxMilliSecs = 500)

      val storedHasher = new Argon2PasswordHasher()
      val passwordInfo = storedHasher.hash(password)

      currentHasher.isSuitable(passwordInfo) must beTrue
    }

    "return false if the hasher isn't suitable for the given password info" in new Context {
      val passwordInfo = PasswordInfo("scrypt", "")

      hasher.isSuitable(passwordInfo) must beFalse
    }
  }

  "The `isDeprecated` method" should {
    "return None if the hasher isn't suitable for the given password info" in new Context {
      val passwordInfo = PasswordInfo("scrypt", "")

      hasher.isDeprecated(passwordInfo) must beNone
    }

    "return Some(true) if the stored log rounds are not equal the hasher log rounds" in new Context {
      val currentHasher = new Argon2PasswordHasher(maxMilliSecs = 500)

      val storedHasher = new Argon2PasswordHasher()
      val passwordInfo = storedHasher.hash(password)

      currentHasher.isDeprecated(passwordInfo) must beSome(true)
    }

    "return Some(false) if the stored log rounds are equal the hasher log rounds" in new Context {
      val passwordInfo = hasher.hash(password)

      hasher.isDeprecated(passwordInfo) must beSome(false)
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * A plain text password.
     */
    lazy val password = "my_S3cr3t_p@sswQrd"

    /**
     * The hasher to test.
     */
    lazy val hasher = new Argon2PasswordHasher(parallelism = 2)
  }
}
