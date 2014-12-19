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

import com.mohiva.silhouette.{Authenticator, LoginInfo}
import com.mohiva.silhouette.exceptions.AuthenticationException
import com.mohiva.silhouette.services.AuthenticatorService
import AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Base64, FingerprintGenerator }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticatorService._
import com.mohiva.silhouette.util.Clock
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator]].
 */
class SessionAuthenticatorSpec extends PlaySpecification with Mockito {

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

      clock.now returns new DateTime
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true

      await(service.create(loginInfo)).fingerprint must beSome("test")
    }

    "return a non fingerprinted authenticator" in new Context {
      implicit val request = FakeRequest()

      clock.now returns new DateTime
      settings.useFingerprinting returns false

      await(service.create(loginInfo)).fingerprint must beNone
    }

    "return an authenticator with the current date as lastUsedDate" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      clock.now returns now

      await(service.create(loginInfo)).lastUsedDate must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      clock.now returns now

      await(service.create(loginInfo)).expirationDate must be equalTo now.plusMinutes(12 * 60)
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 * 60 * 60
      val now = new DateTime

      clock.now returns now
      settings.authenticatorExpiry returns sixHours

      await(service.create(loginInfo)).expirationDate must be equalTo now.plusSeconds(sixHours)
    }

    "throws an Authentication exception if an error occurred during creation" in new Context {
      implicit val request = FakeRequest()

      clock.now throws new RuntimeException("Could not get date")

      await(service.create(loginInfo)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(CreateError.format(ID, ""))
      }
    }
  }

  "The `retrieve` method of the service" should {
    "return None if no authenticator exists in session" in new Context {
      implicit val request = FakeRequest()

      await(service.retrieve) must beNone
    }

    "return None if session contains invalid json" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> Base64.encode("{"))

      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns false

      await(service.retrieve) must beNone
    }

    "return None if session contains valid json but invalid authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> Base64.encode("{ \"test\": \"test\" }"))

      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns false

      await(service.retrieve) must beNone
    }

    "return None if authenticator fingerprint doesn't match current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "false"
      settings.useFingerprinting returns true
      settings.encryptAuthenticator returns false
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest().withSession(settings.sessionKey -> Base64.encode(Json.toJson(authenticator)))

      await(service.retrieve) must beNone
    }

    "return authenticator if authenticator fingerprint matches current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true
      settings.encryptAuthenticator returns false
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest().withSession(settings.sessionKey -> Base64.encode(Json.toJson(authenticator)))

      await(service.retrieve) must beSome(authenticator)
    }

    "return authenticator if fingerprinting is disabled" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> Base64.encode(Json.toJson(authenticator)))

      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns false

      await(service.retrieve) must beSome(authenticator)
    }

    "decrypt authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest()
        .withSession(settings.sessionKey -> Crypto.encryptAES(Json.toJson(authenticator).toString()))

      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns true

      await(service.retrieve) must beSome(authenticator)
    }

    "throws an Authentication exception if an error occurred during retrieval" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> Base64.encode(Json.toJson(authenticator)))

      fingerprintGenerator.generate throws new RuntimeException("Could not generate ID")
      settings.useFingerprinting returns true
      settings.encryptAuthenticator returns false

      await(service.retrieve) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(RetrieveError.format(ID, ""))
      }
    }
  }

  "The result `init` method of the service" should {
    "return the response with an unencrypted authenticator stored in the session" in new WithApplication with Context {
      settings.encryptAuthenticator returns false

      implicit val request = FakeRequest()
      val data = Base64.encode(Json.toJson(authenticator))
      val result = service.init(authenticator, Future.successful(Results.Status(200)))

      session(result).get(settings.sessionKey) should beSome(data)
    }

    "return the response with an encrypted authenticator stored in the session" in new WithApplication with Context {
      settings.encryptAuthenticator returns true

      implicit val request = FakeRequest()
      val result = service.init(authenticator, Future.successful(Results.Status(200)))

      session(result).get(settings.sessionKey) should beSome(Crypto.encryptAES(Json.toJson(authenticator).toString()))
    }

    "throws an Authentication exception if error an occurred during initialization" in new Context {
      implicit val request = spy(FakeRequest())
      val okResult = Future.successful(Results.Status(200))

      request.session throws new RuntimeException("Cannot get session")

      await(service.init(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(InitError.format(ID, ""))
      }
    }
  }

  "The request `init` method of the service" should {
    "return the request with an unencrypted authenticator stored in the session" in new WithApplication with Context {
      settings.encryptAuthenticator returns false

      val data = Base64.encode(Json.toJson(authenticator))
      val request = await(service.init(authenticator, FakeRequest()))

      request.session.get(settings.sessionKey) should beSome(data)
    }

    "return the request with an encrypted authenticator stored in the session" in new WithApplication with Context {
      settings.encryptAuthenticator returns true

      val request = await(service.init(authenticator, FakeRequest()))

      request.session.get(settings.sessionKey) should beSome(Crypto.encryptAES(Json.toJson(authenticator).toString()))
    }

    "override an existing session" in new WithApplication with Context {
      settings.encryptAuthenticator returns false

      val data = Base64.encode(Json.toJson(authenticator))
      val request = await(service.init(authenticator, FakeRequest().withSession(settings.sessionKey -> "test")))

      request.session.get(settings.sessionKey) should beSome(data)
    }

    "keep non authenticator related session data" in new WithApplication with Context {
      settings.encryptAuthenticator returns false

      val data = Base64.encode(Json.toJson(authenticator))
      val request = await(service.init(authenticator, FakeRequest().withSession("test" -> "test")))

      request.session.get(settings.sessionKey) should beSome(data)
      request.session.get("test") should beSome("test")
    }

    "throws an Authentication exception if error an occurred during initialization" in new Context {
      implicit val request = spy(FakeRequest())

      request.session throws new RuntimeException("Cannot get session")

      await(service.init(authenticator, request)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(InitError.format(ID, ""))
      }
    }
  }

  "The `touch` method of the service" should {
    "update the last used date if idle timeout is defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns Some(1)
      clock.now returns DateTime.now

      service.touch(authenticator) must beLeft[SessionAuthenticator].like {
        case a =>
          a.lastUsedDate must be equalTo clock.now
      }
    }

    "do not update the last used date if idle timeout is not defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns None
      clock.now returns DateTime.now

      service.touch(authenticator) must beRight[SessionAuthenticator].like {
        case a =>
          a.lastUsedDate must be equalTo authenticator.lastUsedDate
      }
    }
  }

  "The `update` method of the service" should {
    "update the session with an unencrypted authenticator" in new WithApplication with Context {
      settings.encryptAuthenticator returns false

      implicit val request = FakeRequest()
      val data = Base64.encode(Json.toJson(authenticator))
      val result = service.update(authenticator, Future.successful(Results.Ok))

      status(result) must be equalTo OK
      session(result).get(settings.sessionKey) should beSome(data)
    }

    "update the session with an encrypted authenticator" in new WithApplication with Context {
      settings.encryptAuthenticator returns true

      implicit val request = FakeRequest()
      val data = Crypto.encryptAES(Json.toJson(authenticator).toString())
      val result = service.update(authenticator, Future.successful(Results.Ok))

      status(result) must be equalTo OK
      session(result).get(settings.sessionKey) should beSome(data)
    }

    "throws an Authentication exception if an error occurred during update" in new Context {
      implicit val request = spy(FakeRequest())

      request.session throws new RuntimeException("Cannot get session")

      await(service.update(authenticator, Future.successful(Results.Ok))) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(UpdateError.format(ID, ""))
      }
    }
  }

  "The `renew` method of the service" should {
    "renew the session with an an unencrypted authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = DateTime.now
      val data = Base64.encode(Json.toJson(authenticator.copy(
        lastUsedDate = now,
        expirationDate = now.plusSeconds(settings.authenticatorExpiry)
      )))

      settings.encryptAuthenticator returns false
      settings.useFingerprinting returns false
      clock.now returns now

      val result = service.renew(authenticator, Future.successful(Results.Ok))

      session(result).get(settings.sessionKey) should beSome(data)
    }

    "renew the session with an encrypted authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = DateTime.now
      val data = Crypto.encryptAES(Json.toJson(authenticator.copy(
        lastUsedDate = now,
        expirationDate = now.plusSeconds(settings.authenticatorExpiry)
      )).toString())

      settings.encryptAuthenticator returns true
      settings.useFingerprinting returns false
      clock.now returns now

      val result = service.renew(authenticator, Future.successful(Results.Ok))

      session(result).get(settings.sessionKey) should beSome(data)
    }

    "throws an Authentication exception if an error occurred during renewal" in new Context {
      implicit val request = spy(FakeRequest())
      val now = DateTime.now
      val okResult = (a: Authenticator) => Future.successful(Results.Status(200))

      request.session throws new RuntimeException("Cannot get session")
      settings.useFingerprinting returns false
      clock.now returns now

      await(service.renew(authenticator, Future.successful(Results.Ok))) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(RenewError.format(ID, ""))
      }
    }
  }

  "The `discard` method of the service" should {
    "discard the authenticator from session" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val result = service.discard(authenticator, Future.successful(Results.Status(200).withSession(
        settings.sessionKey -> "test"
      )))

      session(result).get(settings.sessionKey) should beNone
    }

    "throws an Authentication exception if an error occurred during discarding" in new WithApplication with Context {
      implicit val request = spy(FakeRequest())

      request.session throws new RuntimeException("Cannot get session")

      await(service.discard(authenticator, Future.successful(Results.Status(200).withSession(
        settings.sessionKey -> "test"
      )))) must throwA[AuthenticationException].like {
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
     * The ID generator implementation.
     */
    lazy val fingerprintGenerator: FingerprintGenerator = mock[FingerprintGenerator]

    /**
     * The clock implementation.
     */
    lazy val clock: Clock = mock[Clock]

    /**
     * The settings.
     */
    lazy val settings = spy(SessionAuthenticatorSettings(
      sessionKey = "authenticator",
      encryptAuthenticator = true,
      useFingerprinting = true,
      authenticatorIdleTimeout = Some(30 * 60),
      authenticatorExpiry = 12 * 60 * 60
    ))

    /**
     * The cache service instance to test.
     */
    lazy val service = new SessionAuthenticatorService(settings, fingerprintGenerator, clock)

    /**
     * The login info.
     */
    lazy val loginInfo = LoginInfo("test", "1")

    /**
     * An authenticator.
     */
    lazy val authenticator = spy(new SessionAuthenticator(
      loginInfo = LoginInfo("test", "1"),
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60),
      idleTimeout = settings.authenticatorIdleTimeout,
      fingerprint = None
    ))
  }
}
