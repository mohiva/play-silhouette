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
 * Test case for the [[BCryptPasswordHasher]] class.
 */
class BCryptPasswordHasherSpec extends Specification {

  "The `hash` method" should {
    "hash a password" in new Context {
      val passwordInfo: PasswordInfo = hasher.hash(password)

      passwordInfo.hasher must be equalTo BCryptPasswordHasher.ID
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
      val passwordInfo = PasswordInfo("bcrypt", "$2a$10$PBwXy.iQz9n4QOdbgEV7Ve2aYsvXeAvyT0rzhoZKwaDH/3j3tUSW.")

      hasher.matches(passwordInfo, password) must beTrue
    }

    "return false if a password doesn't match a previous hashed password" in new Context {
      val passwordInfo = hasher.hash(password)

      hasher.matches(passwordInfo, "not-equal") must beFalse
    }
  }

  "The `isSuitable` method" should {
    "return true if the hasher is suitable for the given password info" in new Context {
      val passwordInfo = hasher.hash(password)

      hasher.isSuitable(passwordInfo) must beTrue
    }

    "return true if the hasher is suitable when given a password info with different log rounds" in new Context {
      val currentHasher = new BCryptPasswordHasher(5)

      val storedHasher = new BCryptPasswordHasher(10)
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
      val currentHasher = new BCryptPasswordHasher(5)

      val storedHasher = new BCryptPasswordHasher(10)
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
    lazy val hasher = new BCryptPasswordHasher(10)
  }
}
