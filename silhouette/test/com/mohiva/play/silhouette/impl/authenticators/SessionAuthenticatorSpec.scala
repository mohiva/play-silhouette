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
import com.mohiva.play.silhouette.api.crypto.Base64AuthenticatorEncoder
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Clock, FingerprintGenerator }
import com.mohiva.play.silhouette.api.{ Authenticator, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator._
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticatorService._
import org.joda.time.DateTime
import org.specs2.control.NoLanguageFeatures
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator]].
 */
class SessionAuthenticatorSpec extends PlaySpecification with Mockito with NoLanguageFeatures {

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

  "The `unserialize` method of the authenticator" should {
    "throw an AuthenticatorException if the given value can't be parsed as Json" in new WithApplication with Context {
      val value = "invalid"
      val msg = Pattern.quote(JsonParseError.format(ID, value))

      unserialize(authenticatorEncoder.encode(value), authenticatorEncoder) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }

    "throw an AuthenticatorException if the given value is in the wrong Json format" in new WithApplication with Context {
      val value = "{}"
      val msg = "^" + Pattern.quote(InvalidJsonFormat.format(ID, "")) + ".*"

      unserialize(authenticatorEncoder.encode(value), authenticatorEncoder) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }
  }

  "The `serialize/unserialize` method of the authenticator" should {
    "serialize/unserialize an authenticator" in new WithApplication with Context {
      val value = serialize(authenticator, authenticatorEncoder)

      unserialize(value, authenticatorEncoder) must beSuccessfulTry.withValue(authenticator)
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

    "return an authenticator with the current date as lastUsedDateTime" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      clock.now returns now

      await(service.create(loginInfo)).lastUsedDateTime must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      clock.now returns now

      await(service.create(loginInfo)).expirationDateTime must be equalTo now + 12.hours
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 hours
      val now = new DateTime

      clock.now returns now
      settings.authenticatorExpiry returns sixHours

      await(service.create(loginInfo)).expirationDateTime must be equalTo now + sixHours
    }

    "throws an AuthenticatorCreationException exception if an error occurred during creation" in new Context {
      implicit val request = FakeRequest()

      clock.now throws new RuntimeException("Could not get date")

      await(service.create(loginInfo)) must throwA[AuthenticatorCreationException].like {
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
      implicit val request = FakeRequest().withSession(settings.sessionKey -> authenticatorEncoder.encode("{"))

      settings.useFingerprinting returns false

      await(service.retrieve) must beNone
    }

    "return None if session contains valid json but invalid authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> authenticatorEncoder.encode("{ \"test\": \"test\" }"))

      settings.useFingerprinting returns false

      await(service.retrieve) must beNone
    }

    "return None if authenticator fingerprint doesn't match current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "false"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest().withSession(settings.sessionKey -> authenticatorEncoder.encode(Json.toJson(authenticator).toString()))

      await(service.retrieve) must beNone
    }

    "return authenticator if authenticator fingerprint matches current fingerprint" in new WithApplication with Context {
      fingerprintGenerator.generate(any) returns "test"
      settings.useFingerprinting returns true
      authenticator.fingerprint returns Some("test")

      implicit val request = FakeRequest().withSession(settings.sessionKey -> authenticatorEncoder.encode(Json.toJson(authenticator).toString()))

      await(service.retrieve) must beSome(authenticator)
    }

    "return authenticator if fingerprinting is disabled" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> authenticatorEncoder.encode(Json.toJson(authenticator).toString()))

      settings.useFingerprinting returns false

      await(service.retrieve) must beSome(authenticator)
    }

    "decode an authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest()
        .withSession(settings.sessionKey -> authenticatorEncoder.encode(Json.toJson(authenticator).toString()))

      settings.useFingerprinting returns false

      await(service.retrieve) must beSome(authenticator)
    }

    "throws an AuthenticatorRetrievalException exception if an error occurred during retrieval" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> authenticatorEncoder.encode(Json.toJson(authenticator).toString()))

      fingerprintGenerator.generate(any) throws new RuntimeException("Could not generate fingerprint")
      settings.useFingerprinting returns true

      await(service.retrieve) must throwA[AuthenticatorRetrievalException].like {
        case e =>
          e.getMessage must startWith(RetrieveError.format(ID, ""))
      }
    }
  }

  "The `init` method of the service" should {
    "return a session with an encoded authenticator" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val session = await(service.init(authenticator))

      session must be equalTo Session(Map(settings.sessionKey -> data))
    }

    "override existing authenticator from request" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> "existing")
      val session = await(service.init(authenticator))

      unserialize(session.get(settings.sessionKey).get, authenticatorEncoder).get must be equalTo authenticator
    }

    "keep non authenticator related session data" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession("test" -> "test")
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val session = await(service.init(authenticator))

      session.get(settings.sessionKey) should beSome(data)
      session.get("test") should beSome("test")
    }
  }

  "The result `embed` method of the service" should {
    "return the response with the session" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val result = service.embed(Session(Map(settings.sessionKey -> data)), Results.Ok)

      session(result).get(settings.sessionKey) should beSome(data)
    }

    "override existing authenticator from request" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> "existing")
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val result = service.embed(Session(Map(settings.sessionKey -> data)), Results.Ok)

      session(result).get(settings.sessionKey) should beSome(data)
    }

    "keep non authenticator related session data" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession("request-other" -> "keep")
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val result = service.embed(Session(Map(settings.sessionKey -> data)), Results.Ok.addingToSession(
        "result-other" -> "keep"
      ))

      session(result).get(settings.sessionKey) should beSome(data)
      session(result).get("request-other") should beSome("keep")
      session(result).get("result-other") should beSome("keep")
    }
  }

  "The request `embed` method of the service" should {
    "return the request with the session" in new WithApplication with Context {
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val session = Session(Map(settings.sessionKey -> data))
      val request = service.embed(session, FakeRequest())

      request.session.get(settings.sessionKey) should beSome(data)
    }

    "override an existing session" in new WithApplication with Context {
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val session = Session(Map(settings.sessionKey -> data))
      val request = service.embed(session, FakeRequest().withSession(settings.sessionKey -> "test"))

      request.session.get(settings.sessionKey) should beSome(data)
    }
  }

  "The `touch` method of the service" should {
    "update the last used date if idle timeout is defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns Some(1 second)
      clock.now returns DateTime.now

      service.touch(authenticator) must beLeft[SessionAuthenticator].like {
        case a =>
          a.lastUsedDateTime must be equalTo clock.now
      }
    }

    "do not update the last used date if idle timeout is not defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns None
      clock.now returns DateTime.now

      service.touch(authenticator) must beRight[SessionAuthenticator].like {
        case a =>
          a.lastUsedDateTime must be equalTo authenticator.lastUsedDateTime
      }
    }
  }

  "The `update` method of the service" should {
    "update the session" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val data = authenticatorEncoder.encode(Json.toJson(authenticator).toString())
      val result = service.update(authenticator, Results.Ok)

      status(result) must be equalTo OK
      session(result).get(settings.sessionKey) should beSome(data)
    }

    "override existing authenticator from request" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> "existing")
      val result = service.update(authenticator, Results.Ok)

      unserialize(session(result).get(settings.sessionKey).get, authenticatorEncoder).get must be equalTo authenticator
    }

    "non authenticator related session data" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession("request-other" -> "keep")
      val result = service.update(authenticator, Results.Ok.addingToSession(
        "result-other" -> "keep"
      ))

      unserialize(session(result).get(settings.sessionKey).get, authenticatorEncoder).get must be equalTo authenticator
      session(result).get("request-other") should beSome("keep")
      session(result).get("result-other") should beSome("keep")
    }

    "throws an AuthenticatorUpdateException exception if an error occurred during update" in new Context {
      implicit val request = spy(FakeRequest())

      request.session throws new RuntimeException("Cannot get session")

      await(service.update(authenticator, Results.Ok)) must throwA[AuthenticatorUpdateException].like {
        case e =>
          e.getMessage must startWith(UpdateError.format(ID, ""))
      }
    }
  }

  "The `renew` method of the service" should {
    "renew the session" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = DateTime.now
      val data = authenticatorEncoder.encode(Json.toJson(authenticator.copy(
        lastUsedDateTime = now,
        expirationDateTime = now + settings.authenticatorExpiry
      )).toString())

      settings.useFingerprinting returns false
      clock.now returns now

      val result = service.renew(authenticator, Results.Ok)

      session(result).get(settings.sessionKey) should beSome(data)
    }

    "override existing authenticator from request" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession(settings.sessionKey -> "existing")
      val now = DateTime.now

      settings.useFingerprinting returns false
      clock.now returns now

      val result = service.renew(authenticator, Results.Ok)

      unserialize(session(result).get(settings.sessionKey).get, authenticatorEncoder).get must be equalTo authenticator.copy(
        lastUsedDateTime = now,
        expirationDateTime = now + settings.authenticatorExpiry
      )
    }

    "non authenticator related session data" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession("request-other" -> "keep")
      val now = DateTime.now

      settings.useFingerprinting returns false
      clock.now returns now

      val result = service.renew(authenticator, Results.Ok.addingToSession(
        "result-other" -> "keep"
      ))

      unserialize(session(result).get(settings.sessionKey).get, authenticatorEncoder).get must be equalTo authenticator.copy(
        lastUsedDateTime = now,
        expirationDateTime = now + settings.authenticatorExpiry
      )
      session(result).get("request-other") should beSome("keep")
      session(result).get("result-other") should beSome("keep")
    }

    "throws an AuthenticatorRenewalException exception if an error occurred during renewal" in new Context {
      implicit val request = spy(FakeRequest())
      val now = DateTime.now
      val okResult = (_: Authenticator) => Future.successful(Results.Ok)

      request.session throws new RuntimeException("Cannot get session")
      settings.useFingerprinting returns false
      clock.now returns now

      await(service.renew(authenticator, Results.Ok)) must throwA[AuthenticatorRenewalException].like {
        case e =>
          e.getMessage must startWith(RenewError.format(ID, ""))
      }
    }
  }

  "The `discard` method of the service" should {
    "discard the authenticator from session" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val result = service.discard(authenticator, Results.Ok.withSession(
        settings.sessionKey -> "test"
      ))

      session(result).get(settings.sessionKey) should beNone
    }

    "non authenticator related session data" in new WithApplication with Context {
      implicit val request = FakeRequest().withSession("request-other" -> "keep", settings.sessionKey -> "test")
      val result = service.discard(authenticator, Results.Ok.addingToSession(
        "result-other" -> "keep"
      ))

      session(result).get(settings.sessionKey) should beNone
      session(result).get("request-other") should beSome("keep")
      session(result).get("result-other") should beSome("keep")
    }

    "throws an AuthenticatorDiscardingException exception if an error occurred during discarding" in new WithApplication with Context {
      implicit val request = spy(FakeRequest()).withSession(settings.sessionKey -> "test")
      val result = mock[Result]

      result.removingFromSession(any)(any) throws new RuntimeException("Cannot get session")

      await(service.discard(authenticator, result)) must throwA[AuthenticatorDiscardingException].like {
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
    lazy val fingerprintGenerator = mock[FingerprintGenerator].smart

    /**
     * The authenticator encoder implementation.
     */
    lazy val authenticatorEncoder = new Base64AuthenticatorEncoder

    /**
     * The clock implementation.
     */
    lazy val clock = mock[Clock].smart

    /**
     * The settings.
     */
    lazy val settings = spy(SessionAuthenticatorSettings(
      authenticatorIdleTimeout = Some(30 minutes),
      authenticatorExpiry = 12 hours
    ))

    /**
     * The cache service instance to test.
     */
    lazy val service = new SessionAuthenticatorService(
      settings,
      fingerprintGenerator,
      authenticatorEncoder,
      new DefaultSessionCookieBaker(),
      new DefaultCookieHeaderEncoding(),
      clock
    )

    /**
     * The login info.
     */
    lazy val loginInfo = LoginInfo("test", "1")

    /**
     * An authenticator.
     */
    lazy val authenticator = spy(new SessionAuthenticator(
      loginInfo = LoginInfo("test", "1"),
      lastUsedDateTime = DateTime.now,
      expirationDateTime = DateTime.now + settings.authenticatorExpiry,
      idleTimeout = settings.authenticatorIdleTimeout,
      fingerprint = None
    ))
  }
}
