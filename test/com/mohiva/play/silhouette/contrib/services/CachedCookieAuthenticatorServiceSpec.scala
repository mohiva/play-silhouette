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
package com.mohiva.play.silhouette.contrib.services

import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import scala.concurrent.Future
import play.api.mvc.{ Results, Cookie }
import play.api.test.{ FakeRequest, PlaySpecification }
import com.mohiva.play.silhouette.core.{ Identity, Authenticator, LoginInfo }
import com.mohiva.play.silhouette.core.utils.{ Clock, IDGenerator, CacheLayer }

/**
 * Test case for the [[com.mohiva.play.silhouette.contrib.services.CachedCookieAuthenticatorService]] class.
 */
class CachedCookieAuthenticatorServiceSpec extends PlaySpecification with Mockito {

  "The create method" should {
    "save the authenticator in cache" in new Context {
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns new DateTime
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(Some(authenticator))

      await(service.create(identity))

      there was one(cacheLayer).set(any, any, any)
    }

    "return an authenticator with the generated ID" in new Context {
      val id = "test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns new DateTime
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).get.id must be equalTo id
    }

    "return an authenticator with the current date as lastUsedDate" in new Context {
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).get.lastUsedDate must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).get.expirationDate must be equalTo now.plusMinutes(12 * 60)
    }

    "return an authenticator which expires in 6 hours" in new Context {
      val sixHours = 6 * 60 * 60
      val now = new DateTime

      settings.authenticatorExpiry returns sixHours
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).get.expirationDate must be equalTo now.plusSeconds(sixHours)
    }

    "return None if something went wrong" in new Context {
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns new DateTime
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(None)

      await(service.create(identity)) must beNone
    }
  }

  "The retrieve method" should {
    "return None if no authenticator cookie exists" in new Context {
      implicit val request = FakeRequest()

      await(service.retrieve) must beNone
    }

    "return None if no is stored for the cookie" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      cacheLayer.get[CachedCookieAuthenticator](authenticator.id) returns Future.successful(None)

      await(service.retrieve) must beNone
    }

    "return None if an expired authenticator is stored for the cookie" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      cacheLayer.get[CachedCookieAuthenticator](authenticator.id) returns Future.successful(Some(authenticator.copy(
        expirationDate = DateTime.now.minusMinutes(1)
      )))

      await(service.retrieve) must beNone
    }

    "return None if a timed out authenticator is stored for the cookie" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      cacheLayer.get[CachedCookieAuthenticator](authenticator.id) returns Future.successful(Some(authenticator.copy(
        lastUsedDate = DateTime.now.minusMinutes(settings.cookieIdleTimeout + 1)
      )))

      await(service.retrieve) must beNone
    }

    "delete authenticator if an invalid authenticator is stored for the cookie" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      cacheLayer.get[CachedCookieAuthenticator](authenticator.id) returns Future.successful(Some(authenticator.copy(
        expirationDate = DateTime.now.minusMinutes(1)
      )))

      await(service.retrieve) must beNone
      there was one(cacheLayer).remove(authenticator.id)
    }

    "return authenticator if a a valid authenticator is stored for the cookie" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      cacheLayer.get[CachedCookieAuthenticator](authenticator.id) returns Future.successful(Some(authenticator))

      await(service.retrieve) must beSome(authenticator)
    }
  }

  "The update method" should {
    "update the authenticator in cache" in new Context {
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(Some(authenticator))

      await(service.update(authenticator))

      there was one(cacheLayer).set(authenticator.id, authenticator.copy(lastUsedDate = clock.now), 0)
    }

    "return the given authenticator if all going well" in new Context {
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(Some(authenticator))

      await(service.update(authenticator)) must beSome(authenticator)
    }

    "return None if something went wrong" in new Context {
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(None)

      await(service.update(authenticator)) must beNone
    }
  }

  "The send method" should {
    "return the response with a cookie" in new Context {
      val result = Future.successful(service.send(authenticator, Results.Status(200)))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo authenticator.id
        c.maxAge must be equalTo settings.cookieAbsoluteTimeout
        c.path must be equalTo settings.cookiePath
        c.domain must be equalTo settings.cookieDomain
        c.secure must be equalTo settings.secureCookie
        c.httpOnly must be equalTo settings.httpOnlyCookie
      }
    }
  }

  "The discard method" should {
    "discard the the cookie from response" in new Context {
      val result = Future.successful(service.discard(Results.Status(200).withCookies(
        Cookie(
          name = settings.cookieName,
          value = authenticator.id,
          maxAge = settings.cookieAbsoluteTimeout,
          path = settings.cookiePath,
          domain = settings.cookieDomain,
          secure = settings.secureCookie,
          httpOnly = settings.httpOnlyCookie
        )
      )))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo ""
        c.maxAge must beSome[Int].which(_ < 0)
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
     * The cache layer implementation.
     */
    lazy val cacheLayer: CacheLayer = mock[CacheLayer]

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
    lazy val settings: CachedCookieAuthenticatorSettings = {
      val s = mock[CachedCookieAuthenticatorSettings]
      s.cookieName returns "id"
      s.cookiePath returns "/"
      s.cookieDomain returns None
      s.secureCookie returns true
      s.httpOnlyCookie returns true
      s.cookieIdleTimeout returns 30 * 60
      s.cookieAbsoluteTimeout returns Some(12 * 60 * 60)
      s.authenticatorExpiry returns 12 * 60 * 60
      s
    }

    /**
     * The cache service instance to test.
     */
    lazy val service = new CachedCookieAuthenticatorService(settings, cacheLayer, idGenerator, clock)

    /**
     * An identity.
     */
    lazy val identity = new Identity {
      val loginInfo = LoginInfo("test", "1")
    }

    /**
     * An authenticator.
     */
    lazy val authenticator = new CachedCookieAuthenticator(
      id = "test-id",
      loginInfo = LoginInfo("test", "1"),
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60),
      cookieIdleTimeout = settings.cookieIdleTimeout
    )
  }
}
