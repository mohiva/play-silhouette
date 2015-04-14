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
package com.mohiva.play.silhouette.impl.authenticators

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.Results
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticator]].
 */
class DummyAuthenticatorSpec extends PlaySpecification with Mockito {

  "The `isValid` method of the authenticator" should {
    "return true" in new Context {
      authenticator.isValid must beTrue
    }
  }

  "The `create` method of the service" should {
    "return an authenticator containing the given login info" in new Context {
      implicit val request = FakeRequest()

      await(service.create(loginInfo)).loginInfo must be equalTo loginInfo
    }
  }

  "The `retrieve` method of the service" should {
    "return None" in new Context {
      implicit val request = FakeRequest()

      await(service.retrieve) must beNone
    }
  }

  "The `init` method of the service" should {
    "return unit" in new Context {
      implicit val request = FakeRequest()

      await(service.init(authenticator)) must be equalTo (())
    }
  }

  "The result `embed` method of the service" should {
    "return the original response" in new Context {
      implicit val request = FakeRequest()
      val result = AuthenticatorResult(Results.Ok)

      await(service.embed((), result)) must be equalTo result
    }
  }

  "The request `embed` method of the service" should {
    "return the original request" in new Context {
      val request = FakeRequest()

      service.embed((), request) must be equalTo request
    }
  }

  "The `touch` method of the service" should {
    "not update the authenticator" in new WithApplication with Context {
      service.touch(authenticator) must beRight[DummyAuthenticator].like {
        case a =>
          a.loginInfo must be equalTo loginInfo
      }
    }
  }

  "The `update` method of the service" should {
    "return the original result" in new Context {
      implicit val request = FakeRequest()
      val result = AuthenticatorResult(Results.Ok)

      await(service.update(authenticator, result)) must be equalTo result
    }
  }

  "The `renew` method of the service" should {
    "return the original result" in new Context {
      implicit val request = FakeRequest()
      val result = AuthenticatorResult(Results.Ok)

      await(service.renew(authenticator, result)) must be equalTo result
    }
  }

  "The `discard` method of the service" should {
    "return the original result" in new Context {
      implicit val request = FakeRequest()
      val result = AuthenticatorResult(Results.Ok)

      await(service.discard(authenticator, result)) must be equalTo result
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The authenticator service instance to test.
     */
    lazy val service = new DummyAuthenticatorService()

    /**
     * The login info.
     */
    lazy val loginInfo = LoginInfo("test", "1")

    /**
     * An authenticator.
     */
    lazy val authenticator = new DummyAuthenticator(
      loginInfo = LoginInfo("test", "1")
    )
  }
}
