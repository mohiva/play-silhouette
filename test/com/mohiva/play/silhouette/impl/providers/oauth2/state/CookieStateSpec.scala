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
package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.util.{ Base64, Clock, IDGenerator }
import com.mohiva.play.silhouette.impl.exceptions.StateException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.CookieStateProvider._
import org.joda.time.DateTime
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{ Cookie, Results }
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.oauth2.state.CookieState]] class.
 */
class CookieStateSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `isExpired` method of the state" should {
    "return true if the state is expired" in new Context {
      state.copy(expirationDate = DateTime.now.minusHours(1)).isExpired must beTrue
    }

    "return false if the state isn't expired" in new Context {
      state.copy(expirationDate = DateTime.now.plusHours(1)).isExpired must beFalse
    }
  }

  "The `serialize` method of the state" should {
    "serialize the JSON as base64 encoded string" in new Context {
      val dateTime = new DateTime(2014, 8, 8, 0, 0, 0)
      val decoded = Base64.decode(state.copy(expirationDate = dateTime).serialize)

      decoded must /("expirationDate" -> dateTime.getMillis)
      decoded must /("value" -> "value")
    }
  }

  "The `build` method of the provider" should {
    "return a new state" in new Context {
      val dateTime = new DateTime(2014, 8, 8, 0, 0, 0)
      val value = "value"

      clock.now returns dateTime
      idGenerator.generate returns Future.successful(value)

      val s = await(provider.build())

      s.expirationDate must be equalTo dateTime.plusSeconds(settings.expirationTime)
      s.value must be equalTo value
    }
  }

  "The `validate` method of the provider" should {
    "throw an StateException if client state doesn't exists" in new Context {
      implicit val req = FakeRequest(GET, s"?$State=${state.serialize}")

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(ClientStateDoesNotExists.format("test", ""))
      }
    }

    "throw an StateException if provider state doesn't exists" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, "/").withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(ProviderStateDoesNotExists.format("test", ""))
      }
    }

    "throw an StateException if client state contains invalid json" in new WithApplication with Context {
      val invalidState = Base64.encode("{")

      implicit val req = FakeRequest(GET, s"?$State=${state.serialize}").withCookies(Cookie(settings.cookieName, invalidState))

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(InvalidStateFormat.format("test", ""))
      }
    }

    "throw an StateException if client state contains valid json but invalid state" in new WithApplication with Context {
      val invalidState = Base64.encode("{ \"test\": \"test\" }")

      implicit val req = FakeRequest(GET, s"?$State=${state.serialize}").withCookies(Cookie(settings.cookieName, invalidState))

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(InvalidStateFormat.format("test", ""))
      }
    }

    "throw an StateException if provider state contains invalid json" in new WithApplication with Context {
      val invalidState = Base64.encode("{")

      implicit val req = FakeRequest(GET, s"?$State=$invalidState").withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(InvalidStateFormat.format("test", ""))
      }
    }

    "throw an StateException if provider state contains valid json but invalid state" in new WithApplication with Context {
      val invalidState = Base64.encode("{ \"test\": \"test\" }")

      implicit val req = FakeRequest(GET, s"?$State=$invalidState").withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(InvalidStateFormat.format("test", ""))
      }
    }

    "throw an StateException if client and provider state are not equal" in new WithApplication with Context {
      val clientState = state.copy(value = "clientState")
      val providerState = state.copy(value = "providerState")

      implicit val req = FakeRequest(GET, s"?$State=${providerState.serialize}").withCookies(Cookie(settings.cookieName, clientState.serialize))

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(StateIsNotEqual.format("test"))
      }
    }

    "throw an StateException if state is expired" in new WithApplication with Context {
      val expiredState = state.copy(expirationDate = DateTime.now.minusHours(1))

      implicit val req = FakeRequest(GET, s"?$State=${expiredState.serialize}").withCookies(Cookie(settings.cookieName, expiredState.serialize))

      await(provider.validate("test")) must throwA[StateException].like {
        case e => e.getMessage must startWith(StateIsExpired.format("test"))
      }
    }

    "return the state if it's valid" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, s"?$State=${state.serialize}").withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate("test")) must be equalTo state
    }
  }

  "The `publish` method of the provider" should {
    "add the state to the cookie" in new Context {
      implicit val req = FakeRequest(GET, "/")
      val result = Future.successful(provider.publish(Results.Status(200), state))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo state.serialize
        c.maxAge must beSome(settings.expirationTime)
        c.path must be equalTo settings.cookiePath
        c.domain must be equalTo settings.cookieDomain
        c.secure must be equalTo settings.secureCookie
      }
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The ID generator implementation.
     */
    lazy val idGenerator: IDGenerator = mock[IDGenerator]

    /**
     * The clock implementation.
     */
    lazy val clock: Clock = mock[Clock]

    /**
     * The settings.
     */
    lazy val settings = CookieStateSettings(
      cookieName = "OAuth2State",
      cookiePath = "/",
      cookieDomain = None,
      secureCookie = true,
      httpOnlyCookie = true,
      expirationTime = 5 * 60
    )

    /**
     * The provider implementation to test.
     */
    lazy val provider = new CookieStateProvider(settings, idGenerator, clock)

    /**
     * A state to test.
     */
    lazy val state = spy(new CookieState(
      expirationDate = DateTime.now.plusMinutes(settings.expirationTime),
      value = "value"
    ))
  }
}
