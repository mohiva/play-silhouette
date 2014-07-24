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
package com.mohiva.play.silhouette.contrib.authenticators

import scala.concurrent.Future
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.{ WithApplication, FakeRequest, PlaySpecification }
import com.mohiva.play.silhouette.core.utils.{ FingerprintGenerator, Clock }
import com.mohiva.play.silhouette.core.{ Identity, LoginInfo }

/**
 * Test case for the [[com.mohiva.play.silhouette.contrib.authenticators.SessionAuthenticator]].
 */
class SessionAuthenticatorSpec extends PlaySpecification with Mockito {

  "The `isValid` method of the authenticator" should {
    "return false if the authenticator is expired" in new Context {
      authenticator.copy(expirationDate = DateTime.now.minusHours(1)).isValid must beFalse
    }

    "return false if the authenticator is timed out" in new Context {
      authenticator.copy(
        lastUsedDate = DateTime.now.minusMinutes(settings.authenticatorIdleTimeout + 1)
      ).isValid must beFalse
    }

    "return true if the authenticator is valid" in new Context {
      authenticator.copy(
        lastUsedDate = DateTime.now.minusMinutes(settings.authenticatorIdleTimeout - 1),
        expirationDate = DateTime.now.plusSeconds(5)
      ).isValid must beTrue
    }
  }

  "The `create` method of the service" should {
    "return a fingerprinted authenticator" in new Context {
      clock.now returns new DateTime
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true
      implicit val request = FakeRequest()

      await(service.create(identity)).fingerprint must beSome("test")
    }

    "return a non fingerprinted authenticator" in new Context {
      clock.now returns new DateTime
      settings.useFingerprinting returns false

      implicit val request = FakeRequest()

      await(service.create(identity)).fingerprint must beNone
    }

    "return an authenticator with the current date as lastUsedDate" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      clock.now returns now

      await(service.create(identity)).lastUsedDate must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      clock.now returns now

      await(service.create(identity)).expirationDate must be equalTo now.plusMinutes(12 * 60)
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 * 60 * 60
      val now = new DateTime

      clock.now returns now
      settings.authenticatorExpiry returns sixHours

      await(service.create(identity)).expirationDate must be equalTo now.plusSeconds(sixHours)
    }
  }

  "The `retrieve` method of the service" should {
    "return None if no authenticator exists in session" in new Context {
      implicit val request = FakeRequest()

      await(service.retrieve) must beNone
    }

    "return None if session contains invalid json" in new WithApplication with Context {
      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns false

      implicit val request = FakeRequest().withSession(settings.sessionKey -> "{")

      await(service.retrieve) must beNone
    }

    "return None if session contains valid json but invalid authenticator" in new WithApplication with Context {
      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns false

      implicit val request = FakeRequest().withSession(settings.sessionKey -> "{ \"test\": \"test\" }")

      await(service.retrieve) must beNone
    }

    "return None if authenticator fingerprint doesn't match current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "false"
      settings.useFingerprinting returns true
      settings.encryptAuthenticator returns false
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest()
        .withSession(settings.sessionKey -> Json.toJson(authenticator).toString())

      await(service.retrieve) must beNone
    }

    "return authenticator if authenticator fingerprint matches current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true
      settings.encryptAuthenticator returns false
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest()
        .withSession(settings.sessionKey -> Json.toJson(authenticator).toString())

      await(service.retrieve) must beSome(authenticator)
    }

    "return authenticator if fingerprinting is disabled" in new WithApplication with Context {
      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns false

      implicit val request = FakeRequest()
        .withSession(settings.sessionKey -> Json.toJson(authenticator).toString())

      await(service.retrieve) must beSome(authenticator)
    }

    "decrypt authenticator" in new WithApplication with Context {
      settings.useFingerprinting returns false
      settings.encryptAuthenticator returns true

      implicit val request = FakeRequest()
        .withSession(settings.sessionKey -> Crypto.encryptAES(Json.toJson(authenticator).toString()))

      await(service.retrieve) must beSome(authenticator)
    }
  }

  "The `init` method of the service" should {
    "return the response with an unencrypted authenticator stored in the session" in new Context {
      settings.encryptAuthenticator returns false
      implicit val request = FakeRequest()
      val result = service.init(authenticator, Future.successful(Results.Status(200)))

      session(result).get(settings.sessionKey) should beSome(Json.toJson(authenticator).toString())
    }

    "return the response with an encrypted authenticator stored in the session" in new Context {
      settings.encryptAuthenticator returns true
      implicit val request = FakeRequest()
      val result = service.init(authenticator, Future.successful(Results.Status(200)))

      session(result).get(settings.sessionKey) should beSome(Crypto.encryptAES(Json.toJson(authenticator).toString()))
    }
  }

  "The `update` method of the service" should {
    "update the session with an unencrypted authenticator" in new Context {
      implicit val request = FakeRequest()
      val now = DateTime.now().plusHours(1)

      settings.encryptAuthenticator returns false
      clock.now returns now

      // In this case the result must be 0 because the authenticator gets always updated and therefore
      // it doesn't never equal the passed authenticator
      val result = service.update(authenticator, a => Future.successful(Results.Status(if (a == authenticator) 1 else 0)))
      val sessionVal = Json.toJson(authenticator.copy(lastUsedDate = clock.now)).toString()

      status(result) must be equalTo 0
      session(result).get(settings.sessionKey) should beSome(sessionVal)
    }

    "update the session with an encrypted authenticator" in new Context {
      implicit val request = FakeRequest()
      val now = DateTime.now().plusHours(1)

      settings.encryptAuthenticator returns true
      clock.now returns now

      // In this case the result must be 0 because the authenticator gets always updated and therefore
      // it doesn't never equal the passed authenticator
      val result = service.update(authenticator, a => Future.successful(Results.Status(if (a == authenticator) 1 else 0)))
      val sessionVal = Crypto.encryptAES(Json.toJson(authenticator.copy(lastUsedDate = clock.now)).toString())

      status(result) must be equalTo 0
      session(result).get(settings.sessionKey) should beSome(sessionVal)
    }
  }

  "The `discard` method of the service" should {
    "discard the the authenticator from session" in new Context {
      implicit val request = FakeRequest()
      val result = service.discard(authenticator, Future.successful(Results.Status(200).withSession(
        settings.sessionKey -> "test"
      )))

      session(result).get(settings.sessionKey) should beNone
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
      authenticatorIdleTimeout = 30 * 60,
      authenticatorExpiry = 12 * 60 * 60
    ))

    /**
     * The cache service instance to test.
     */
    lazy val service = new SessionAuthenticatorService(settings, fingerprintGenerator, clock)

    /**
     * An identity.
     */
    lazy val identity = new Identity {
      val loginInfo = LoginInfo("test", "1")
    }

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
