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
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Base64, Clock, IDGenerator }
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator._
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticatorService._
import org.joda.time.DateTime
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.Crypto
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{ JsNull, Json }
import play.api.mvc.Results
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator]].
 */
class JWTAuthenticatorSpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

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
    "return a JWT with an expiration time" in new WithApplication with Context {
      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("exp" -> (authenticator.expirationDateTime.getMillis / 1000).toInt)
    }

    "return a JWT with an encrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns true

      val jwt = serialize(authenticator)(settings)
      val json = Json.parse(Base64.decode(jwt.split('.').apply(1)))
      val sub = Json.parse(Crypto.decryptAES((json \ "sub").as[String])).as[LoginInfo]

      sub must be equalTo authenticator.loginInfo
    }

    "return a JWT with an unencrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns false

      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("sub" -> Base64.encode(Json.toJson(authenticator.loginInfo)))
    }

    "return a JWT with an issuer" in new WithApplication with Context {
      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("iss" -> settings.issuerClaim)
    }

    "return a JWT with an issued-at time" in new WithApplication with Context {
      val jwt = serialize(authenticator)(settings)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("iat" -> (authenticator.lastUsedDateTime.getMillis / 1000).toInt)
    }

    "throw an AuthenticatorException if a reserved claim will be overridden" in new WithApplication with Context {
      val claims = Json.obj(
        "jti" -> "reserved"
      )

      serialize(authenticator.copy(customClaims = Some(claims)))(settings) must throwA[AuthenticatorException].like {
        case e => e.getMessage must startWith(OverrideReservedClaim.format(ID, "jti", ""))
      }
    }

    "throw an AuthenticatorException if an unexpected value was found in the arbitrary claims" in new WithApplication with Context {
      val claims = Json.obj(
        "null" -> JsNull
      )

      serialize(authenticator.copy(customClaims = Some(claims)))(settings) must throwA[AuthenticatorException].like {
        case e => e.getMessage must startWith(UnexpectedJsonValue.format(ID, ""))
      }
    }

    "return a JWT with arbitrary claims" in new WithApplication with Context {
      val jwt = serialize(authenticator.copy(customClaims = Some(customClaims)))(settings)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("boolean" -> true)
      json must /("string" -> "string")
      json must /("number" -> 1234567890)
      json must /("array") /# 0 / 1
      json must /("array") /# 1 / 2
      json must /("object") / "array" /# 0 / "string1"
      json must /("object") / "array" /# 1 / "string2"
      json must /("object") / "object" / "array" /# 0 / "string"
      json must /("object") / "object" / "array" /# 1 / false
      json must /("object") / "object" / "array" /# 2 / ("number" -> 1)
    }
  }

  "The `unserialize` method of the authenticator" should {
    "throw an AuthenticatorException if the given token can't be parsed" in new WithApplication with Context {
      val jwt = "invalid"
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      unserialize(jwt)(settings) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }

    "throw an AuthenticatorException if the given token couldn't be verified" in new WithApplication with Context {
      val jwt = serialize(authenticator)(settings) + "-wrong-sig"
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      unserialize(jwt)(settings) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }

    "throw an AuthenticatorException if encrypted token gets serialized unencrypted" in new WithApplication with Context {
      settings.encryptSubject returns true

      val jwt = serialize(authenticator)(settings)
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      settings.encryptSubject returns false

      unserialize(jwt)(settings) must beFailedTry.withThrowable[AuthenticatorException](msg)
    }

    "unserialize a JWT with an encrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns true

      val jwt = serialize(authenticator)(settings)

      unserialize(jwt)(settings) must beSuccessfulTry.withValue(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0)
      ))
    }

    "unserialize a JWT with an encrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns false

      val jwt = serialize(authenticator)(settings)

      unserialize(jwt)(settings) must beSuccessfulTry.withValue(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0)
      ))
    }

    "unserialize a JWT with arbitrary claims" in new WithApplication with Context {
      settings.encryptSubject returns false

      val jwt = serialize(authenticator.copy(customClaims = Some(customClaims)))(settings)

      unserialize(jwt)(settings) must beSuccessfulTry.like {
        case a =>
          a.customClaims must beSome(customClaims)
      }
    }
  }

  "The `create` method of the service" should {
    "return an authenticator with the generated ID" in new Context {
      implicit val request = FakeRequest()
      val id = "test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns new DateTime

      await(service(None).create(loginInfo)).id must be equalTo id
    }

    "return an authenticator with the current date as lastUsedDateTime" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(None).create(loginInfo)).lastUsedDateTime must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(None).create(loginInfo)).expirationDateTime must be equalTo now + 12.hours
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 hours
      val now = new DateTime

      settings.authenticatorExpiry returns sixHours
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(None).create(loginInfo)).expirationDateTime must be equalTo now + sixHours
    }

    "throws an AuthenticatorCreationException exception if an error occurred during creation" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.failed(new Exception("Could not generate ID"))

      await(service(None).create(loginInfo)) must throwA[AuthenticatorCreationException].like {
        case e =>
          e.getMessage must startWith(CreateError.format(ID, ""))
      }
    }
  }

  "The `retrieve` method of the service" should {
    "return None if no authenticator header exists" in new Context {
      implicit val request = FakeRequest()

      await(service(None).retrieve) must beNone
    }

    "return None if DAO is enabled an no authenticator is stored for the header" in new Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> "not-stored")

      repository.find(authenticator.id) returns Future.successful(None)

      await(service(Some(repository)).retrieve) must beNone
    }

    "return authenticator if DAO is enabled and an authenticator is stored for the header" in new WithApplication with Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> serialize(authenticator)(settings))

      repository.find(authenticator.id) returns Future.successful(Some(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0)
      )))

      await(service(Some(repository)).retrieve) must beSome(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0)
      ))
    }

    "return authenticator if DAO is disabled and authenticator was found in the header" in new WithApplication with Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> serialize(authenticator)(settings))

      await(service(None).retrieve) must beSome(authenticator.copy(
        expirationDateTime = authenticator.expirationDateTime.withMillisOfSecond(0),
        lastUsedDateTime = authenticator.lastUsedDateTime.withMillisOfSecond(0)
      ))
      there was no(repository).find(any)
    }

    "throws an AuthenticatorRetrievalException exception if an error occurred during retrieval" in new WithApplication with Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> serialize(authenticator)(settings))

      repository.find(authenticator.id) returns Future.failed(new RuntimeException("Cannot find authenticator"))

      await(service(Some(repository)).retrieve) must throwA[AuthenticatorRetrievalException].like {
        case e =>
          e.getMessage must startWith(RetrieveError.format(ID, ""))
      }
    }
  }

  "The `init` method of the service" should {
    "return the token if DAO is enabled and authenticator could be saved in backing store" in new WithApplication with Context {
      repository.add(any) answers { p => Future.successful(p.asInstanceOf[JWTAuthenticator]) }
      implicit val request = FakeRequest()

      val token = await(service(Some(repository)).init(authenticator))

      unserialize(token)(settings).get must be equalTo authenticator
      there was one(repository).add(any)
    }

    "return the token if DAO is disabled" in new WithApplication with Context {
      implicit val request = FakeRequest()

      val token = await(service(None).init(authenticator))

      unserialize(token)(settings).get must be equalTo authenticator
      there was no(repository).add(any)
    }

    "throws an AuthenticatorInitializationException exception if an error occurred during initialization" in new Context {
      repository.add(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()
      val okResult = Future.successful(Results.Ok)

      await(service(Some(repository)).init(authenticator)) must throwA[AuthenticatorInitializationException].like {
        case e =>
          e.getMessage must startWith(InitError.format(ID, ""))
      }
    }
  }

  "The result `embed` method of the service" should {
    "return the response with a header" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val token = serialize(authenticator)(settings)

      val result = service(Some(repository)).embed(token, Results.Ok)

      header(settings.headerName, result) should beSome(token)
    }
  }

  "The request `embed` method of the service" should {
    "return the request with a header " in new WithApplication with Context {
      val token = serialize(authenticator)(settings)
      val request = service(Some(repository)).embed(token, FakeRequest())

      unserialize(request.headers.get(settings.headerName).get)(settings).get must be equalTo authenticator
    }

    "override an existing token" in new WithApplication with Context {
      val token = serialize(authenticator)(settings)
      val request = service(Some(repository)).embed(token, FakeRequest().withHeaders(settings.headerName -> "test"))

      unserialize(request.headers.get(settings.headerName).get)(settings).get must be equalTo authenticator
    }

    "keep non authenticator related headers" in new WithApplication with Context {
      val token = serialize(authenticator)(settings)
      val request = service(Some(repository)).embed(token, FakeRequest().withHeaders("test" -> "test"))

      unserialize(request.headers.get(settings.headerName).get)(settings).get must be equalTo authenticator
      request.headers.get("test") should beSome("test")
    }
  }

  "The `touch` method of the service" should {
    "update the last used date if idle timeout is defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns Some(1 second)
      clock.now returns DateTime.now

      service(None).touch(authenticator) must beLeft[JWTAuthenticator].like {
        case a =>
          a.lastUsedDateTime must be equalTo clock.now
      }
    }

    "do not update the last used date if idle timeout is not defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns None
      clock.now returns DateTime.now

      service(None).touch(authenticator) must beRight[JWTAuthenticator].like {
        case a =>
          a.lastUsedDateTime must be equalTo authenticator.lastUsedDateTime
      }
    }
  }

  "The `update` method of the service" should {
    "update the authenticator in backing store" in new WithApplication with Context {
      repository.update(any) returns Future.successful(authenticator)

      implicit val request = FakeRequest()

      await(service(Some(repository)).update(authenticator, Results.Ok))

      there was one(repository).update(authenticator)
    }

    "return the result if the authenticator could be stored in backing store" in new WithApplication with Context {
      repository.update(any) answers { p => Future.successful(p.asInstanceOf[JWTAuthenticator]) }

      implicit val request = FakeRequest()
      val result = service(Some(repository)).update(authenticator, Results.Ok)

      status(result) must be equalTo OK
      unserialize(header(settings.headerName, result).get)(settings).get must be equalTo authenticator
      there was one(repository).update(authenticator)
    }

    "return the result if backing store is disabled" in new WithApplication with Context {
      repository.update(any) answers { p => Future.successful(p.asInstanceOf[JWTAuthenticator]) }

      implicit val request = FakeRequest()
      val result = service(None).update(authenticator, Results.Ok)

      status(result) must be equalTo OK
      unserialize(header(settings.headerName, result).get)(settings).get must be equalTo authenticator
      there was no(repository).update(any)
    }

    "throws an AuthenticatorUpdateException exception if an error occurred during update" in new WithApplication with Context {
      repository.update(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()

      await(service(Some(repository)).update(authenticator, Results.Ok)) must throwA[AuthenticatorUpdateException].like {
        case e =>
          e.getMessage must startWith(UpdateError.format(ID, ""))
      }
    }
  }

  "The `renew` method of the service" should {
    "renew the authenticator and return the response with a new JWT if DAO is enabled" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = new DateTime(2015, 2, 25, 19, 0, 0, 0)
      val id = "new-test-id"

      repository.remove(any) returns Future.successful(())
      repository.add(any) answers { p => Future.successful(p.asInstanceOf[JWTAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service(Some(repository)).renew(authenticator, Results.Ok)

      unserialize(header(settings.headerName, result).get)(settings).get must be equalTo authenticator.copy(
        id = id,
        expirationDateTime = clock.now + settings.authenticatorExpiry,
        lastUsedDateTime = clock.now
      )

      there was one(repository).add(any)
      there was one(repository).remove(authenticator.id)
    }

    "renew the authenticator and return the response with a new JWT if DAO is disabled" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = new DateTime(2015, 2, 25, 19, 0, 0, 0)
      val id = "new-test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service(None).renew(authenticator, Results.Ok)

      unserialize(header(settings.headerName, result).get)(settings).get must be equalTo authenticator.copy(
        id = id,
        expirationDateTime = clock.now + settings.authenticatorExpiry,
        lastUsedDateTime = clock.now
      )
      there was no(repository).remove(any)
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
    "remove authenticator from backing store if DAO is enabled" in new Context {
      implicit val request = FakeRequest()

      repository.remove(authenticator.id) returns Future.successful(authenticator)

      await(service(Some(repository)).discard(authenticator, Results.Ok))

      there was one(repository).remove(authenticator.id)
    }

    "do not remove the authenticator from backing store if DAO is disabled" in new Context {
      implicit val request = FakeRequest()

      await(service(None).discard(authenticator, Results.Ok))

      there was no(repository).remove(authenticator.id)
    }

    "throws an AuthenticatorDiscardingException exception if an error occurred during discarding" in new Context {
      implicit val request = FakeRequest()
      val okResult = Results.Ok

      repository.remove(authenticator.id) returns Future.failed(new Exception("Cannot remove authenticator"))

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
    lazy val repository: AuthenticatorRepository[JWTAuthenticator] = mock[AuthenticatorRepository[JWTAuthenticator]]

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
    lazy val settings = spy(JWTAuthenticatorSettings(
      headerName = "X-Auth-Token",
      issuerClaim = "play-silhouette",
      encryptSubject = true,
      authenticatorIdleTimeout = Some(30 minutes),
      authenticatorExpiry = 12 hours,
      sharedSecret = "fGhre3$56%43erfkl8)/§$dsdf345gsdfvsdf23kl"
    ))

    /**
     * The authenticator service instance to test.
     */
    lazy val service = (repository: Option[AuthenticatorRepository[JWTAuthenticator]]) =>
      new JWTAuthenticatorService(settings, repository, idGenerator, clock)

    /**
     * The login info.
     */
    lazy val loginInfo = LoginInfo("test", "1")

    /**
     * An authenticator.
     */
    lazy val authenticator = new JWTAuthenticator(
      id = "test-id",
      loginInfo = LoginInfo("test", "1"),
      lastUsedDateTime = new DateTime(2015, 2, 25, 19, 0, 0, 0),
      expirationDateTime = new DateTime(2015, 2, 25, 19, 0, 0, 0) + settings.authenticatorExpiry,
      idleTimeout = settings.authenticatorIdleTimeout
    )

    /**
     * Some custom claims.
     */
    lazy val customClaims = Json.obj(
      "boolean" -> true,
      "string" -> "string",
      "number" -> 1234567890,
      "array" -> Json.arr(1, 2),
      "object" -> Json.obj(
        "array" -> Seq("string1", "string2"),
        "object" -> Json.obj(
          "array" -> Json.arr("string", false, Json.obj("number" -> 1))
        )
      )
    )
  }
}
