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
package com.mohiva.play.silhouette.api.services

import play.api.mvc.{ Cookie, Results }
import play.api.test.{ WithApplication, PlaySpecification }

/**
 * Test case for the [[com.mohiva.play.silhouette.api.services.AuthenticatorResult]] class.
 */
class AuthenticatorResultSpec extends PlaySpecification {

  "The `copy` method" should {
    "return new a new instance of an authenticator result" in {
      val result = Results.Ok
      val authenticatorResult = AuthenticatorResult(result)

      authenticatorResult.copy(result.header, result.body, result.connection) must beAnInstanceOf[AuthenticatorResult]
    }
  }

  "The `withSession` method" should {
    "return new a new instance of an authenticator result" in new WithApplication {
      val result = Results.Ok
      val authenticatorResult = AuthenticatorResult(result)

      authenticatorResult.withSession("name" -> "value") must beAnInstanceOf[AuthenticatorResult]
    }
  }

  "The `withCookies` method" should {
    "return new a new instance of an authenticator result" in new WithApplication {
      val result = Results.Ok
      val authenticatorResult = AuthenticatorResult(result)

      authenticatorResult.withCookies(Cookie("name", "value")) must beAnInstanceOf[AuthenticatorResult]
    }
  }

  "The `withHeaders` method" should {
    "return new a new instance of an authenticator result" in new WithApplication {
      val result = Results.Ok
      val authenticatorResult = AuthenticatorResult(result)

      authenticatorResult.withHeaders("name" -> "value") must beAnInstanceOf[AuthenticatorResult]
    }
  }
}
