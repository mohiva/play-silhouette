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
package com.mohiva.play.silhouette.impl.authenticators

import com.mohiva.play.silhouette.api.exceptions.AuthenticationException
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Clock, FingerprintGenerator, IDGenerator }
import com.mohiva.play.silhouette.api.{ Authenticator, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorService._
import com.mohiva.play.silhouette.impl.daos.AuthenticatorDAO
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{ Cookie, Results }
import play.api.test.{ FakeRequest, PlaySpecification }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator]].
 */
class CookieAuthenticatorSpec extends PlaySpecification with Mockito {

  "The `isValid` method of the authenticator" should {
    "return false if the authenticator is expired" in new Context {
      authenticator.copy(expirationDate = DateTime.now.minusHours(1)).isValid must beFalse
    }

    "return false if the authenticator is timed out" in new Context {
      authenticator.copy(
        lastUsedDate = DateTime.now.minusMinutes(settings.authenticatorIdleTimeout.get + 1)
      ).isValid must beFalse
    }

    "return true if the authenticator is valid" in new Context {
      authenticator.copy(
        lastUsedDate = DateTime.now.minusMinutes(settings.authenticatorIdleTimeout.get - 1),
        expirationDate = DateTime.now.plusSeconds(5)
      ).isValid must beTrue
    }
  }

  "The `create` method of the service" should {
    "return a fingerprinted authenticator" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns new DateTime
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true

      await(service.create(loginInfo)).fingerprint must beSome("test")
    }

    "return a non fingerprinted authenticator" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns new DateTime
      settings.useFingerprinting returns false

      await(service.create(loginInfo)).fingerprint must beNone
    }

    "return an authenticator with the generated ID" in new Context {
      implicit val request = FakeRequest()
      val id = "test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns new DateTime

      await(service.create(loginInfo)).id must be equalTo id
    }

    "return an authenticator with the current date as lastUsedDate" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service.create(loginInfo)).lastUsedDate must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service.create(loginInfo)).expirationDate must be equalTo now.plusMinutes(12 * 60)
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 * 60 * 60
      val now = new DateTime

      settings.authenticatorExpiry returns sixHours
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service.create(loginInfo)).expirationDate must be equalTo now.plusSeconds(sixHours)
    }

    "throws an Authentication exception if an error occurred during creation" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.failed(new Exception("Could not generate ID"))

      await(service.create(loginInfo)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(CreateError.format(ID, ""))
      }
    }
  }

  "The `retrieve` method of the service" should {
    "return None if no authenticator cookie exists" in new Context {
      implicit val request = FakeRequest()

      await(service.retrieve) must beNone
    }

    "return None if no authenticator is stored for the cookie" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      dao.find(authenticator.id) returns Future.successful(None)

      await(service.retrieve) must beNone
    }

    "return None if authenticator fingerprint doesn't match current fingerprint" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      fingerprintGenerator.generate(any) returns "false"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")
      dao.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service.retrieve) must beNone
    }

    "return authenticator if authenticator fingerprint matches current fingerprint" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")
      dao.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service.retrieve) must beSome(authenticator)
    }

    "return authenticator if fingerprinting is disabled" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      settings.useFingerprinting returns false
      dao.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service.retrieve) must beSome(authenticator)
    }

    "throws an Authentication exception if an error occurred during retrieval" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      fingerprintGenerator.generate throws new RuntimeException("Could not generate ID")
      settings.useFingerprinting returns true
      dao.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service.retrieve) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(RetrieveError.format(ID, ""))
      }
    }
  }

  "The `init` method of the service" should {
    "return the response with a cookie if authenticator could be saved in backing store" in new Context {
      dao.save(any) returns Future.successful(authenticator)

      implicit val request = FakeRequest()
      val result = service.init(authenticator, Future.successful(Results.Status(200)))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo authenticator.id
        c.maxAge must be equalTo settings.cookieMaxAge
        c.path must be equalTo settings.cookiePath
        c.domain must be equalTo settings.cookieDomain
        c.secure must be equalTo settings.secureCookie
        c.httpOnly must be equalTo settings.httpOnlyCookie
      }
    }

    "throws an Authentication exception if error an occurred during initialization" in new Context {
      dao.save(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()
      val okResult = Future.successful(Results.Status(200))

      await(service.init(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(InitError.format(ID, ""))
      }
    }
  }

  "The `update` method of the service" should {
    "update the authenticator in backing store" in new Context {
      dao.save(any) returns Future.successful(authenticator)
      clock.now returns DateTime.now().plusHours(1)

      implicit val request = FakeRequest()

      await(service.update(authenticator, _ => Future.successful(Results.Status(200))))

      there was one(dao).save(authenticator.copy(lastUsedDate = clock.now))
    }

    "return the result if the authenticator could be stored in backing store" in new Context {
      dao.save(any) answers { p => Future.successful(p.asInstanceOf[CookieAuthenticator]) }
      clock.now returns DateTime.now().plusHours(1)

      // In this case the result must be 0 because the backing store returns the updated
      // authenticator and this returned authenticator doesn't equal the passed authenticator
      // because the lastUsedDate was updated
      implicit val request = FakeRequest()
      val updatedResult = (a: Authenticator) => Future.successful(Results.Status(if (a == authenticator) 1 else 0))
      val result = service.update(authenticator, updatedResult)

      status(result) must be equalTo 0
    }

    "throws an Authentication exception if an error occurred during update" in new Context {
      dao.save(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()
      val okResult = (a: Authenticator) => Future.successful(Results.Status(200))

      await(service.update(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(UpdateError.format(ID, ""))
      }
    }
  }

  "The `renew` method of the service" should {
    "remove the old authenticator from backing store" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      dao.remove(authenticator.id) returns Future.successful(())
      dao.save(any) answers { p => Future.successful(p.asInstanceOf[CookieAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      await(service.renew(authenticator, _ => Future.successful(Results.Status(200))))

      there was one(dao).remove(authenticator.id)
    }

    "renew the authenticator and return the response with the updated cookie value" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      dao.remove(any) returns Future.successful(())
      dao.save(any) answers { p => Future.successful(p.asInstanceOf[CookieAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service.renew(authenticator, _ => Future.successful(Results.Status(200)))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo id
        c.maxAge must be equalTo settings.cookieMaxAge
        c.path must be equalTo settings.cookiePath
        c.domain must be equalTo settings.cookieDomain
        c.secure must be equalTo settings.secureCookie
        c.httpOnly must be equalTo settings.httpOnlyCookie
      }
      there was one(dao).save(any)
    }

    "throws an Authentication exception if an error occurred during renewal" in new Context {
      implicit val request = FakeRequest()
      val okResult = (a: Authenticator) => Future.successful(Results.Status(200))
      val now = new DateTime
      val id = "new-test-id"

      dao.remove(any) returns Future.successful(())
      dao.save(any) returns Future.failed(new Exception("Cannot store authenticator"))
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      await(service.renew(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(RenewError.format(ID, ""))
      }
    }
  }

  "The `discard` method of the service" should {
    "discard the cookie from response" in new Context {
      implicit val request = FakeRequest()

      dao.remove(any) returns Future.successful(())

      val result = service.discard(authenticator, Future.successful(Results.Status(200).withCookies(
        Cookie(
          name = settings.cookieName,
          value = authenticator.id,
          maxAge = settings.cookieMaxAge,
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
      there was one(dao).remove(authenticator.id)
    }

    "throws an Authentication exception if an error occurred during discarding" in new Context {
      implicit val request = FakeRequest()
      val okResult = Future.successful(Results.Status(200))

      dao.remove(any) returns Future.failed(new Exception("Cannot store authenticator"))

      await(service.discard(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(DiscardError.format(ID, ""))
      }
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The backing store implementation.
     */
    lazy val dao: AuthenticatorDAO[CookieAuthenticator] = mock[AuthenticatorDAO[CookieAuthenticator]]

    /**
     * The ID generator implementation.
     */
    lazy val fingerprintGenerator: FingerprintGenerator = mock[FingerprintGenerator]

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
    lazy val settings = spy(CookieAuthenticatorSettings(
      cookieName = "id",
      cookiePath = "/",
      cookieDomain = None,
      secureCookie = true,
      httpOnlyCookie = true,
      useFingerprinting = true,
      cookieMaxAge = Some(12 * 60 * 60),
      authenticatorIdleTimeout = Some(30 * 60),
      authenticatorExpiry = 12 * 60 * 60
    ))

    /**
     * The authenticator service instance to test.
     */
    lazy val service = new CookieAuthenticatorService(settings, dao, fingerprintGenerator, idGenerator, clock)

    /**
     * The login info.
     */
    lazy val loginInfo = LoginInfo("test", "1")

    /**
     * An authenticator.
     */
    lazy val authenticator = spy(new CookieAuthenticator(
      id = "test-id",
      loginInfo = LoginInfo("test", "1"),
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60),
      idleTimeout = settings.authenticatorIdleTimeout,
      fingerprint = None
    ))
  }
}
