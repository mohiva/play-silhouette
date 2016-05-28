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
package com.mohiva.play.silhouette.impl.providers.oauth2.state

import java.net.URLEncoder
import java.util.regex.Pattern

import com.mohiva.play.silhouette.api.util.{ Base64, Clock, IDGenerator }
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.CookieState._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.CookieStateProvider._
import org.joda.time.DateTime
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ Cookie, Results }
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.oauth2.state.CookieState]] class.
 */
class CookieStateSpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

  "The `isExpired` method of the state" should {
    "return true if the state is expired" in new Context {
      state.copy(expirationDate = DateTime.now.minusHours(1)).isExpired must beTrue
    }

    "return false if the state isn't expired" in new Context {
      state.copy(expirationDate = DateTime.now.plusHours(1)).isExpired must beFalse
    }
  }

  "The `unserialize` method of the state" should {
    "throw a OAuth2StateException if a state contains invalid json" in new WithApplication with Context {
      val value = "invalid"
      val msg = Pattern.quote(InvalidJson.format(value))

      unserialize(cookieSigner.sign(Base64.encode(value))) must beFailedTry.withThrowable[OAuth2StateException](msg)
    }

    "throw an OAuth2StateException if a state contains valid json but invalid state" in new WithApplication with Context {
      val value = "{ \"test\": \"test\" }"
      val msg = "^" + Pattern.quote(InvalidStateFormat.format("")) + ".*"

      unserialize(cookieSigner.sign(Base64.encode(value))) must beFailedTry.withThrowable[OAuth2StateException](msg)
    }

    "throw an OAuth2StateException if a state is badly signed" in new WithApplication with Context {
      val value = "invalid"
      val msg = Pattern.quote(InvalidCookieSignature)

      unserialize(value) must beFailedTry.withThrowable[OAuth2StateException](msg)
    }
  }

  "The `serialize/unserialize` method of the state" should {
    "serialize/unserialize a state" in new WithApplication with Context {
      val serialized = serialize(state)

      unserialize(serialized) must beSuccessfulTry.withValue(state)
    }
  }

  "The `build` method of the provider" should {
    "return a new state" in new Context {
      implicit val req = FakeRequest()
      val dateTime = new DateTime(2014, 8, 8, 0, 0, 0)
      val value = "value"

      clock.now returns dateTime
      idGenerator.generate returns Future.successful(value)

      val s = await(provider.build)

      s.expirationDate must be equalTo dateTime.plusSeconds(settings.expirationTime.toSeconds.toInt)
      s.value must be equalTo value
    }
  }

  "The `validate` method of the provider" should {
    "throw an OAuth2StateException if client state doesn't exists" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, s"?$State=${URLEncoder.encode(state.serialize, "UTF-8")}")

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(ClientStateDoesNotExists.format(""))
      }
    }

    "throw an OAuth2StateException if provider state doesn't exists" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, "/").withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(ProviderStateDoesNotExists.format(""))
      }
    }

    "throw an OAuth2StateException if client state contains invalid json" in new WithApplication with Context {
      val invalidState = cookieSigner.sign(Base64.encode("{"))

      implicit val req = FakeRequest(GET, s"?$State=${URLEncoder.encode(state.serialize, "UTF-8")}")
        .withCookies(Cookie(settings.cookieName, invalidState))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(InvalidJson.format(""))
      }
    }

    "throw an OAuth2StateException if client state contains valid json but invalid state" in new WithApplication with Context {
      val invalidState = cookieSigner.sign(Base64.encode("{ \"test\": \"test\" }"))

      implicit val req = FakeRequest(GET, s"?$State=${URLEncoder.encode(state.serialize, "UTF-8")}")
        .withCookies(Cookie(settings.cookieName, invalidState))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(InvalidStateFormat.format(""))
      }
    }

    "throw an OAuth2StateException if client state is badly signed" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, s"?$State=${URLEncoder.encode(state.serialize, "UTF-8")}")
        .withCookies(Cookie(settings.cookieName, "invalid"))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(InvalidCookieSignature)
      }
    }

    "throw an OAuth2StateException if provider state contains invalid json" in new WithApplication with Context {
      val invalidState = URLEncoder.encode(cookieSigner.sign(Base64.encode("{")), "UTF-8")

      implicit val req = FakeRequest(GET, s"?$State=$invalidState").withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(InvalidJson.format(""))
      }
    }

    "throw an OAuth2StateException if provider state contains valid json but invalid state" in new WithApplication with Context {
      val invalidState = URLEncoder.encode(cookieSigner.sign(Base64.encode("{ \"test\": \"test\" }")), "UTF-8")

      implicit val req = FakeRequest(GET, s"?$State=$invalidState").withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(InvalidStateFormat.format(""))
      }
    }

    "throw an OAuth2StateException if client and provider state are not equal" in new WithApplication with Context {
      val clientState = state.copy(value = "clientState").serialize
      val providerState = URLEncoder.encode(state.copy(value = "providerState").serialize, "UTF-8")

      implicit val req = FakeRequest(GET, s"?$State=$providerState").withCookies(Cookie(settings.cookieName, clientState))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(StateIsNotEqual.format())
      }
    }

    "throw an OAuth2StateException if state is expired" in new WithApplication with Context {
      val expiredState = state.copy(expirationDate = DateTime.now.minusHours(1)).serialize

      implicit val req = FakeRequest(GET, s"?$State=${URLEncoder.encode(expiredState, "UTF-8")}")
        .withCookies(Cookie(settings.cookieName, expiredState))

      await(provider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(StateIsExpired.format())
      }
    }

    "return the state if it's valid" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, s"?$State=${URLEncoder.encode(state.serialize, "UTF-8")}")
        .withCookies(Cookie(settings.cookieName, state.serialize))

      await(provider.validate) must be equalTo state
    }
  }

  "The `publish` method of the provider" should {
    "add the state to the cookie" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, "/")
      val result = Future.successful(provider.publish(Results.Ok, state))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo state.serialize
        // https://github.com/mohiva/play-silhouette/issues/273
        c.maxAge must beSome[Int].which(_ <= settings.expirationTime.toSeconds.toInt)
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
      expirationTime = 5 minutes
    )

    /**
     * The provider implementation to test.
     */
    lazy val provider = new CookieStateProvider(settings, idGenerator, clock)

    /**
     * A state to test.
     */
    lazy val state = spy(new CookieState(
      expirationDate = DateTime.now.plusSeconds(settings.expirationTime.toSeconds.toInt),
      value = "value"
    ))
  }
}
