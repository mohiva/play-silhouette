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

import java.util.regex.Pattern

import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.crypto.{ Base64AuthenticatorEncoder, Signer }
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Clock, FingerprintGenerator, IDGenerator }
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator._
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticatorService._
import org.joda.time.DateTime
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.MatchResult
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{ Cookie, DefaultCookieHeaderEncoding, Results }
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator]].
 */
class CookieAuthenticatorSpec extends PlaySpecification with Mockito with NoLanguageFeatures {

  "The `isValid` method of the authenticator" should {
    "return false if the authenticator is expired" in new Context {
      authenticator.copy(expirationDateTime = DateTime.now - 1.hour).isValid must beFalse
    }

    "return false if the authenticator is timed out" in new Context {
      authenticator.copy(
        lastUsedDateTime = DateTime.now - (settings.authenticatorIdleTimeout.get + 1.second)
      ).isValid must beFalse
    }

    "return true if the authenticator is valid" in new Context {
      authenticator.copy(
        lastUsedDateTime = DateTime.now - (settings.authenticatorIdleTimeout.get - 10.seconds),
        expirationDateTime = DateTime.now + 5.seconds
      ).isValid must beTrue
    }
  }

  "The `serialize` method of the authenticator" should {
    "sign the cookie" in new WithApplication with Context {
      serialize(authenticator, signer, authenticatorEncoder)

      there was one(signer).sign(any)
    }

    "encode the cookie" in new WithApplication with Context {
      serialize(authenticator, signer, authenticatorEncoder)

      there was one(authenticatorEncoder).encode(any)
    }
  }

  "The `unserialize` method of the authenticator" should {
    "throw an AuthenticatorException if the given value can't be parsed as Json" in new WithApplication with Context {
      val value = "invalid"
      val msg = Pattern.quote(InvalidJson.format(ID, value))

      unserialize(authenticatorEncoder.encode(value), signer, authenticatorEncoder) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }

    "throw an AuthenticatorException if the given value is in the wrong Json format" in new WithApplication with Context {
      val value = "{}"
      val msg = "^" + Pattern.quote(InvalidJsonFormat.format(ID, "")) + ".*"

      unserialize(authenticatorEncoder.encode(value), signer, authenticatorEncoder) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }

    "throw an AuthenticatorException if the cookie signer declines the authenticator" in new WithApplication with Context {
      val value = "value"
      val msg = "^" + Pattern.quote(InvalidCookieSignature.format(ID, "")) + ".*"

      signer.extract(any) returns Failure(new Exception("invalid"))

      unserialize(authenticatorEncoder.encode(value), signer, authenticatorEncoder) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }
  }

  "The `serialize/unserialize` method of the authenticator" should {
    "serialize/unserialize an authenticator" in new WithApplication with Context {
      val value = serialize(authenticator, signer, authenticatorEncoder)

      unserialize(value, signer, authenticatorEncoder) must beSuccessfulTry.withValue(authenticator)
    }
  }

  "The `create` method of the service" should {
    "return a fingerprinted authenticator" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns new DateTime
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true

      await(service(Some(repository)).create(loginInfo)).fingerprint must beSome("test")
    }

    "return a non fingerprinted authenticator" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns new DateTime
      settings.useFingerprinting returns false

      await(service(Some(repository)).create(loginInfo)).fingerprint must beNone
    }

    "return an authenticator with the generated ID" in new Context {
      implicit val request = FakeRequest()
      val id = "test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns new DateTime

      await(service(Some(repository)).create(loginInfo)).id must be equalTo id
    }

    "return an authenticator with the current date as lastUsedDateTime" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(Some(repository)).create(loginInfo)).lastUsedDateTime must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(Some(repository)).create(loginInfo)).expirationDateTime must be equalTo now + 12.hours
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 hours
      val now = new DateTime

      settings.authenticatorExpiry returns sixHours
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(Some(repository)).create(loginInfo)).expirationDateTime must be equalTo now + sixHours
    }

    "throws an AuthenticatorCreationException exception if an error occurred during creation" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.failed(new Exception("Could not generate ID"))

      await(service(Some(repository)).create(loginInfo)) must throwA[AuthenticatorCreationException].like {
        case e =>
          e.getMessage must startWith(CreateError.format(ID, ""))
      }
    }
  }

  "The `retrieve` method of the service" should {
    "return None if no authenticator cookie exists" in new Context {
      implicit val request = FakeRequest()

      await(service(Some(repository)).retrieve) must beNone
    }

    "[stateful] return None if no authenticator for the cookie is stored in backing store" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      repository.find(authenticator.id) returns Future.successful(None)

      await(service(Some(repository)).retrieve) must beNone
    }

    "[stateless] return None if no authenticator could be unserialized from cookie" in new WithApplication with Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticatorEncoder.encode("invalid")))

      await(service(None).retrieve) must beNone
      there was no(repository).find(any)
    }

    "[stateful] return None if authenticator fingerprint doesn't match current fingerprint" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      fingerprintGenerator.generate(any) returns "false"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")
      repository.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service(Some(repository)).retrieve) must beNone
    }

    "[stateless] return None if authenticator fingerprint doesn't match current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "false"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, serialize(authenticator, signer, authenticatorEncoder)))

      await(service(None).retrieve) must beNone
      there was no(repository).find(any)
    }

    "[stateful] return authenticator if authenticator fingerprint matches current fingerprint" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")
      repository.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service(Some(repository)).retrieve) must beSome(authenticator)
    }

    "[stateless] return authenticator if authenticator fingerprint matches current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, serialize(authenticator, signer, authenticatorEncoder)))

      await(service(None).retrieve) must beSome(authenticator)
      there was no(repository).find(any)
    }

    "[stateful] return authenticator if fingerprinting is disabled" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      settings.useFingerprinting returns false
      repository.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service(Some(repository)).retrieve) must beSome(authenticator)
    }

    "[stateless] return authenticator if fingerprinting is disabled" in new WithApplication with Context {
      settings.useFingerprinting returns false

      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, serialize(authenticator, signer, authenticatorEncoder)))

      repository.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service(None).retrieve) must beSome(authenticator)
    }

    "throws an AuthenticatorRetrievalException exception if an error occurred during retrieval" in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(settings.cookieName, authenticator.id))

      fingerprintGenerator.generate(any) throws new RuntimeException("Could not generate fingerprint")
      settings.useFingerprinting returns true
      repository.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service(Some(repository)).retrieve) must throwA[AuthenticatorRetrievalException].like {
        case e =>
          e.getMessage must startWith(RetrieveError.format(ID, ""))
      }
    }
  }

  "The `init` method of the service" should {
    "[stateful] return a cookie with the authenticator ID if the authenticator could be saved in backing store" in new Context {
      repository.add(any) answers { _ => Future.successful(authenticator) }

      implicit val request = FakeRequest()

      await(service(Some(repository)).init(authenticator)) must be equalTo statefulCookie
      there was one(repository).add(any)
    }

    "[stateless] return a cookie with a serialized authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest()

      val cookie = await(service(None).init(authenticator))

      unserialize(cookie.value, signer, authenticatorEncoder) must be equalTo unserialize(statelessCookie.value, signer, authenticatorEncoder)
      there was no(repository).add(any)
    }

    "throws an AuthenticatorInitializationException exception if an error occurred during initialization" in new Context {
      repository.add(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()

      await(service(Some(repository)).init(authenticator)) must throwA[AuthenticatorInitializationException].like {
        case e =>
          e.getMessage must startWith(InitError.format(ID, ""))
      }
    }
  }

  "The result `embed` method of the service" should {
    "return the response with a cookie" in new Context {
      implicit val request = FakeRequest()
      val result = service(Some(repository)).embed(statefulCookie, Results.Ok)

      cookies(result).get(settings.cookieName) should beSome[Cookie].which(
        statefulResponseCookieMatcher(authenticator.id)
      )
    }
  }

  "The request `embed` method of the service" should {
    "return the request with a cookie" in new Context {
      val request = service(Some(repository)).embed(statefulCookie, FakeRequest())

      request.cookies.get(settings.cookieName) should beSome[Cookie].which(requestCookieMatcher(authenticator.id))
    }

    "override an existing cookie" in new Context {
      val request = service(Some(repository)).embed(statefulCookie, FakeRequest().withCookies(Cookie(settings.cookieName, "test")))

      request.cookies.get(settings.cookieName) should beSome[Cookie].which(requestCookieMatcher(authenticator.id))
    }

    "keep non authenticator related cookies" in new Context {
      val request = service(Some(repository)).embed(statefulCookie, FakeRequest().withCookies(Cookie("test", "test")))

      request.cookies.get(settings.cookieName) should beSome[Cookie].which(requestCookieMatcher(authenticator.id))
      request.cookies.get("test") should beSome[Cookie].which { c =>
        c.name must be equalTo "test"
        c.value must be equalTo "test"
      }
    }

    "keep other request parts" in new Context {
      val request = service(Some(repository)).embed(statefulCookie, FakeRequest().withSession("test" -> "test"))

      request.cookies.get(settings.cookieName) should beSome[Cookie].which(requestCookieMatcher(authenticator.id))
      request.session.get("test") should beSome("test")
    }
  }

  "The `touch` method of the service" should {
    "update the last used date if idle timeout is defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns Some(1 second)
      clock.now returns DateTime.now

      service(Some(repository)).touch(authenticator) must beLeft[CookieAuthenticator].like {
        case a =>
          a.lastUsedDateTime must be equalTo clock.now
      }
    }

    "do not update the last used date if idle timeout is not defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns None
      clock.now returns DateTime.now

      service(Some(repository)).touch(authenticator) must beRight[CookieAuthenticator].like {
        case a =>
          a.lastUsedDateTime must be equalTo authenticator.lastUsedDateTime
      }
    }
  }

  "The `update` method of the service" should {
    "[stateful] update the authenticator in backing store" in new Context {
      repository.update(any) answers { _ => Future.successful(authenticator) }

      implicit val request = FakeRequest()

      await(service(Some(repository)).update(authenticator, Results.Ok))

      there was one(repository).update(authenticator)
    }

    "[stateful] return the result if the authenticator could be stored in backing store" in new Context {
      repository.update(any) answers { p => Future.successful(p.asInstanceOf[CookieAuthenticator]) }

      implicit val request = FakeRequest()
      val result = service(Some(repository)).update(authenticator, Results.Ok)

      status(result) must be equalTo OK
    }

    "[stateless] update the cookie for the updated authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val result = service(None).update(authenticator, Results.Ok)

      status(result) must be equalTo OK
      cookies(result).get(settings.cookieName) should beSome[Cookie].which(statelessResponseCookieMatcher(authenticator))
      there was no(repository).update(authenticator)
    }

    "throws an AuthenticatorUpdateException exception if an error occurred during update" in new Context {
      repository.update(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()

      await(service(Some(repository)).update(authenticator, Results.Ok)) must throwA[AuthenticatorUpdateException].like {
        case e =>
          e.getMessage must startWith(UpdateError.format(ID, ""))
      }
    }
  }

  "The `renew` method of the service" should {
    "[stateful] remove the old authenticator from backing store" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      repository.remove(authenticator.id) returns Future.successful(())
      repository.add(any) answers { p => Future.successful(p.asInstanceOf[CookieAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      await(service(Some(repository)).renew(authenticator, Results.Ok))

      there was one(repository).remove(authenticator.id)
    }

    "[stateful] renew the authenticator and return the response with the updated cookie value" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      repository.remove(any) returns Future.successful(())
      repository.add(any) answers { p => Future.successful(p.asInstanceOf[CookieAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service(Some(repository)).renew(authenticator, Results.Ok)

      cookies(result).get(settings.cookieName) should beSome[Cookie].which(statefulResponseCookieMatcher(id))
      there was one(repository).add(any)
    }

    "[stateless] renew the authenticator and return the response with the updated cookie value" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      settings.useFingerprinting returns false
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service(None).renew(authenticator, Results.Ok)

      cookies(result).get(settings.cookieName) should beSome[Cookie].which(statelessResponseCookieMatcher(
        authenticator.copy(id = id, lastUsedDateTime = now, expirationDateTime = now + settings.authenticatorExpiry)
      ))
      there was no(repository).add(any)
    }

    "throws an AuthenticatorRenewalException exception if an error occurred during renewal" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      repository.remove(any) returns Future.successful(())
      repository.add(any) returns Future.failed(new Exception("Cannot store authenticator"))
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      await(service(Some(repository)).renew(authenticator, Results.Ok)) must throwA[AuthenticatorRenewalException].like {
        case e =>
          e.getMessage must startWith(RenewError.format(ID, ""))
      }
    }
  }

  "The `discard` method of the service" should {
    "[stateful] discard the cookie from response and remove it from backing store" in new Context {
      implicit val request = FakeRequest()

      repository.remove(any) returns Future.successful(())

      val result = service(Some(repository)).discard(authenticator, Results.Ok.withCookies(statefulCookie))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo ""
        c.maxAge must beSome(Cookie.DiscardedMaxAge)
        c.path must be equalTo settings.cookiePath
        c.domain must be equalTo settings.cookieDomain
        c.secure must be equalTo settings.secureCookie
      }
      there was one(repository).remove(authenticator.id)
    }

    "[stateless] discard the cookie from response" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val result = service(None).discard(authenticator, Results.Ok.withCookies(statelessCookie))

      cookies(result).get(settings.cookieName) should beSome[Cookie].which { c =>
        c.name must be equalTo settings.cookieName
        c.value must be equalTo ""
        c.maxAge must beSome(Cookie.DiscardedMaxAge)
        c.path must be equalTo settings.cookiePath
        c.domain must be equalTo settings.cookieDomain
        c.secure must be equalTo settings.secureCookie
      }
      there was no(repository).remove(authenticator.id)
    }

    "throws an AuthenticatorDiscardingException exception if an error occurred during discarding" in new Context {
      implicit val request = FakeRequest()
      val okResult = Results.Ok

      repository.remove(any) returns Future.failed(new Exception("Cannot store authenticator"))

      await(service(Some(repository)).discard(authenticator, okResult)) must throwA[AuthenticatorDiscardingException].like {
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
     * The repository implementation.
     */
    lazy val repository = mock[AuthenticatorRepository[CookieAuthenticator]].smart

    /**
     * The ID generator implementation.
     */
    lazy val fingerprintGenerator = mock[FingerprintGenerator].smart

    /**
     * The ID generator implementation.
     */
    lazy val idGenerator = mock[IDGenerator].smart

    /**
     * The signer implementation.
     *
     * The signer returns the same value as passed to the methods. This is enough for testing.
     */
    lazy val signer = {
      val c = mock[Signer].smart
      c.sign(any) answers { p => p.asInstanceOf[String] }
      c.extract(any) answers { p => Success(p.asInstanceOf[String]) }
      c
    }

    /**
     * The authenticator encoder implementation.
     *
     * We use BASE64 here to encode the cookie values. Otherwise an error could occur if we try to store
     * none cookie values in a cookie.
     */
    lazy val authenticatorEncoder = spy(new Base64AuthenticatorEncoder)

    /**
     * The clock implementation.
     */
    lazy val clock = mock[Clock].smart

    /**
     * The settings.
     */
    lazy val settings = spy(CookieAuthenticatorSettings(
      cookieDomain = None,
      cookieMaxAge = Some(12 hours),
      authenticatorIdleTimeout = Some(30 minutes),
      authenticatorExpiry = 12 hours
    ))

    /**
     * The authenticator service instance to test.
     */
    lazy val service = (repository: Option[AuthenticatorRepository[CookieAuthenticator]]) =>
      new CookieAuthenticatorService(
        settings,
        repository,
        signer,
        new DefaultCookieHeaderEncoding(),
        authenticatorEncoder,
        fingerprintGenerator,
        idGenerator,
        clock
      )

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
      lastUsedDateTime = DateTime.now,
      expirationDateTime = DateTime.now + settings.authenticatorExpiry,
      idleTimeout = settings.authenticatorIdleTimeout,
      cookieMaxAge = settings.cookieMaxAge,
      fingerprint = None
    ))

    /**
     * A stateful cookie instance.
     */
    lazy val statefulCookie = Cookie(
      name = settings.cookieName,
      value = authenticator.id,
      maxAge = settings.cookieMaxAge.map(_.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie,
      sameSite = settings.sameSite
    )

    /**
     * A stateless cookie instance.
     */
    lazy val statelessCookie = Cookie(
      name = settings.cookieName,
      value = serialize(authenticator, signer, authenticatorEncoder),
      maxAge = settings.cookieMaxAge.map(_.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie,
      sameSite = settings.sameSite
    )

    /**
     * Matches a stateful response cookie. (Set-Cookie header)
     */
    def statefulResponseCookieMatcher(value: String): Cookie => MatchResult[Any] = { c =>
      c.name must be equalTo settings.cookieName
      c.value must be equalTo value
      // https://github.com/mohiva/play-silhouette/issues/273
      c.maxAge must beSome[Int].which(_ <= settings.cookieMaxAge.get.toSeconds.toInt)
      c.path must be equalTo settings.cookiePath
      c.domain must be equalTo settings.cookieDomain
      c.secure must be equalTo settings.secureCookie
      c.httpOnly must be equalTo settings.httpOnlyCookie
      c.sameSite must be equalTo settings.sameSite
    }

    /**
     * Matches a stateless response cookie. (Set-Cookie header)
     */
    def statelessResponseCookieMatcher(a: CookieAuthenticator): Cookie => MatchResult[Any] = { c =>
      c.name must be equalTo settings.cookieName
      unserialize(c.value, signer, authenticatorEncoder).get must be equalTo a
      // https://github.com/mohiva/play-silhouette/issues/273
      c.maxAge must beSome[Int].which(_ <= settings.cookieMaxAge.get.toSeconds.toInt)
      c.path must be equalTo settings.cookiePath
      c.domain must be equalTo settings.cookieDomain
      c.secure must be equalTo settings.secureCookie
      c.httpOnly must be equalTo settings.httpOnlyCookie
      c.sameSite must be equalTo settings.sameSite
    }

    /**
     * Matches a request cookie. (Cookie header)
     */
    def requestCookieMatcher(value: String): Cookie => MatchResult[Any] = { c =>
      c.name must be equalTo settings.cookieName
      c.value must be equalTo value
    }
  }
}
