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
package com.mohiva.play.silhouette.impl.providers.oauth1.secrets

import java.util.regex.Pattern

import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.exceptions.OAuth1TokenSecretException
import com.mohiva.play.silhouette.impl.providers.OAuth1Info
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.CookieSecret._
import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.CookieSecretProvider._
import org.joda.time.DateTime
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.Crypto
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ Cookie, Results }
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.oauth1.secrets.CookieSecret]] class.
 */
class CookieSecretSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `isExpired` method of the secret" should {
    "return true if the secret is expired" in new Context {
      secret.copy(expirationDate = DateTime.now.minusHours(1)).isExpired must beTrue
    }

    "return false if the secret isn't expired" in new Context {
      secret.copy(expirationDate = DateTime.now.plusHours(1)).isExpired must beFalse
    }
  }

  "The `unserialize` method of the secret" should {
    "throw an OAuth1TokenSecretException if a secret contains invalid json" in new WithApplication with Context {
      val value = "invalid"
      val msg = Pattern.quote(InvalidJson.format("test", value))

      unserialize(cookieSigner.sign(Crypto.encryptAES(value)), "test") must beFailedTry.withThrowable[OAuth1TokenSecretException](msg)
    }

    "throw an OAuth1TokenSecretException if a secret contains valid json but invalid secret" in new WithApplication with Context {
      val value = "{ \"test\": \"test\" }"
      val msg = "^" + Pattern.quote(InvalidSecretFormat.format("test", "")) + ".*"

      unserialize(cookieSigner.sign(Crypto.encryptAES(value)), "test") must beFailedTry.withThrowable[OAuth1TokenSecretException](msg)
    }

    "throw an OAuth1TokenSecretException if a secret is badly signed" in new WithApplication with Context {
      val value = "invalid"
      val msg = Pattern.quote(InvalidCookieSignature.format("test"))

      unserialize(value, "test") must beFailedTry.withThrowable[OAuth1TokenSecretException](msg)
    }
  }

  "The `serialize/unserialize` method of the secret" should {
    "serialize/unserialize a secret" in new WithApplication with Context {
      val serialized = serialize(secret)

      unserialize(serialized, "test") must beSuccessfulTry.withValue(secret)
    }
  }

  "The `build` method of the provider" should {
    "return a new secret" in new WithApplication with Context {
      implicit val req = FakeRequest()
      val dateTime = new DateTime(2014, 8, 8, 0, 0, 0)

      clock.now returns dateTime

      val s = await(provider.build(oAuthInfo))

      s.expirationDate must be equalTo dateTime.plusSeconds(settings.expirationTime)
      s.value must be equalTo oAuthInfo.secret
    }
  }

  "The `retrieve` method of the provider" should {
    "throw an OAuth1TokenSecretException if client secret doesn't exists" in new Context {
      implicit val req = FakeRequest()

      await(provider.retrieve("test")) must throwA[OAuth1TokenSecretException].like {
        case e => e.getMessage must startWith(ClientSecretDoesNotExists.format("test", ""))
      }
    }

    "throw an OAuth1TokenSecretException if client secret contains invalid json" in new WithApplication with Context {
      val invalidSecret = cookieSigner.sign(Crypto.encryptAES("{"))

      implicit val req = FakeRequest().withCookies(Cookie(settings.cookieName, invalidSecret))

      await(provider.retrieve("test")) must throwA[OAuth1TokenSecretException].like {
        case e => e.getMessage must startWith(InvalidJson.format("test", ""))
      }
    }

    "throw an OAuth1TokenSecretException if client secret contains valid json but invalid secret" in new WithApplication with Context {
      val invalidSecret = cookieSigner.sign(Crypto.encryptAES("{ \"test\": \"test\" }"))

      implicit val req = FakeRequest().withCookies(Cookie(settings.cookieName, invalidSecret))

      await(provider.retrieve("test")) must throwA[OAuth1TokenSecretException].like {
        case e => e.getMessage must startWith(InvalidSecretFormat.format("test", ""))
      }
    }

    "throw an OAuth1TokenSecretException if secret is expired" in new WithApplication with Context {
      val expiredSecret = secret.copy(expirationDate = DateTime.now.minusHours(1))

      implicit val req = FakeRequest().withCookies(Cookie(settings.cookieName, expiredSecret.serialize))

      await(provider.retrieve("test")) must throwA[OAuth1TokenSecretException].like {
        case e => e.getMessage must startWith(SecretIsExpired.format("test"))
      }
    }

    "throw an OAuth1TokenSecretException if client secret is badly signed" in new WithApplication with Context {
      implicit val req = FakeRequest().withCookies(Cookie(settings.cookieName, "invalid"))

      await(provider.retrieve("test")) must throwA[OAuth1TokenSecretException].like {
        case e => e.getMessage must startWith(InvalidCookieSignature.format("test"))
      }
    }

    "return the secret if it's valid" in new WithApplication with Context {
      implicit val req = FakeRequest().withCookies(Cookie(settings.cookieName, secret.serialize))

      await(provider.retrieve("test")) must be equalTo secret
    }
  }

  "The `publish` method of the provider" should {
    "add the secret to the cookie" in new WithApplication with Context {
      implicit val req = FakeRequest(GET, "/")
      val result = Future.successful(provider.publish(Results.Status(200), secret))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        unserialize(c.value, "test").get must be equalTo secret
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
     * The clock implementation.
     */
    lazy val clock: Clock = mock[Clock]

    /**
     * The settings.
     */
    lazy val settings = CookieSecretSettings(
      cookieName = "OAuth1TokenSecret",
      cookiePath = "/",
      cookieDomain = None,
      secureCookie = true,
      httpOnlyCookie = true,
      expirationTime = 5 * 60
    )

    /**
     * The provider implementation to test.
     */
    lazy val provider = new CookieSecretProvider(settings, clock)

    /**
     * An OAuth1 info.
     */
    lazy val oAuthInfo = OAuth1Info("my.token", "my.secret")

    /**
     * A secret to test.
     */
    lazy val secret = spy(new CookieSecret(
      expirationDate = DateTime.now.plusMinutes(settings.expirationTime),
      value = "value"
    ))
  }
}
