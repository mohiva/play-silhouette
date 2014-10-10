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

import java.util.regex.Pattern

import com.mohiva.play.silhouette.api.exceptions.AuthenticationException
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Base64, Clock, IDGenerator }
import com.mohiva.play.silhouette.api.{ Authenticator, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticatorService._
import com.mohiva.play.silhouette.impl.daos.AuthenticatorDAO
import org.joda.time.DateTime
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator]].
 */
class JWTAuthenticatorSpec extends PlaySpecification with Mockito with JsonMatchers {

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

  "The `serialize` method of the service" should {
    "return a JWT with an expiration time" in new WithApplication with Context {
      val jwt = service(None).serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("exp" -> authenticator.expirationDate.getMillis / 1000)
    }

    "return a JWT with an encrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns true

      val jwt = service(None).serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("sub" -> Crypto.encryptAES(Json.toJson(authenticator.loginInfo).toString()))
    }

    "return a JWT with an unencrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns false

      val jwt = service(None).serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("sub" -> Base64.encode(Json.toJson(authenticator.loginInfo)))
    }

    "return a JWT with an issuer" in new WithApplication with Context {
      val jwt = service(None).serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("iss" -> settings.issuerClaim)
    }

    "return a JWT with an issued-at time" in new WithApplication with Context {
      val jwt = service(None).serialize(authenticator)
      val json = Base64.decode(jwt.split('.').apply(1))

      json must /("iat" -> authenticator.lastUsedDate.getMillis / 1000)
    }
  }

  "The `unserialize` method of the service" should {
    "throw an AuthenticationException if the given token can't be parsed" in new WithApplication with Context {
      val jwt = "invalid"
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      service(None).unserialize(jwt) must beFailedTry.withThrowable[AuthenticationException](msg)
    }

    "throw an AuthenticationException if the given token couldn't be verified" in new WithApplication with Context {
      val jwt = service(None).serialize(authenticator) + "-wrong-sig"
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      service(None).unserialize(jwt) must beFailedTry.withThrowable[AuthenticationException](msg)
    }

    "throw an AuthenticationException if encrypted token gets serialized unencrypted" in new WithApplication with Context {
      settings.encryptSubject returns true

      val jwt = service(None).serialize(authenticator)
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      settings.encryptSubject returns false

      service(None).unserialize(jwt) must beFailedTry.withThrowable[AuthenticationException](msg)
    }

    "unserialize a JWT with an encrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns true

      val jwt = service(None).serialize(authenticator)
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      service(None).unserialize(jwt) must beSuccessfulTry.withValue(authenticator.copy(
        expirationDate = authenticator.expirationDate.withMillisOfSecond(0),
        lastUsedDate = authenticator.lastUsedDate.withMillisOfSecond(0)
      ))
    }

    "unserialize a JWT with an encrypted subject" in new WithApplication with Context {
      settings.encryptSubject returns false

      val jwt = service(None).serialize(authenticator)
      val msg = Pattern.quote(InvalidJWTToken.format(ID, jwt))

      service(None).unserialize(jwt) must beSuccessfulTry.withValue(authenticator.copy(
        expirationDate = authenticator.expirationDate.withMillisOfSecond(0),
        lastUsedDate = authenticator.lastUsedDate.withMillisOfSecond(0)
      ))
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

    "return an authenticator with the current date as lastUsedDate" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(None).create(loginInfo)).lastUsedDate must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(None).create(loginInfo)).expirationDate must be equalTo now.plusMinutes(12 * 60)
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 * 60 * 60
      val now = new DateTime

      settings.authenticatorExpiry returns sixHours
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service(None).create(loginInfo)).expirationDate must be equalTo now.plusSeconds(sixHours)
    }

    "throws an Authentication exception if an error occurred during creation" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.failed(new Exception("Could not generate ID"))

      await(service(None).create(loginInfo)) must throwA[AuthenticationException].like {
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

      dao.find(authenticator.id) returns Future.successful(None)

      await(service(Some(dao)).retrieve) must beNone
    }

    "return authenticator if DAO is enabled and an authenticator is stored for the header" in new WithApplication with Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> service(None).serialize(authenticator))

      dao.find(authenticator.id) returns Future.successful(Some(authenticator.copy(
        expirationDate = authenticator.expirationDate.withMillisOfSecond(0),
        lastUsedDate = authenticator.lastUsedDate.withMillisOfSecond(0)
      )))

      await(service(Some(dao)).retrieve) must beSome(authenticator.copy(
        expirationDate = authenticator.expirationDate.withMillisOfSecond(0),
        lastUsedDate = authenticator.lastUsedDate.withMillisOfSecond(0)
      ))
    }

    "return authenticator if DAO is disabled and authenticator was found in " +
      "the header" in new WithApplication with Context {
        implicit val request = FakeRequest().withHeaders(settings.headerName -> service(None).serialize(authenticator))

        await(service(None).retrieve) must beSome(authenticator.copy(
          expirationDate = authenticator.expirationDate.withMillisOfSecond(0),
          lastUsedDate = authenticator.lastUsedDate.withMillisOfSecond(0)
        ))
        there was no(dao).find(any)
      }

    "throws an Authentication exception if an error occurred during retrieval" in new WithApplication with Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> service(None).serialize(authenticator))

      dao.find(authenticator.id) returns Future.failed(new RuntimeException("Cannot find authenticator"))

      await(service(Some(dao)).retrieve) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(RetrieveError.format(ID, ""))
      }
    }
  }

  "The `init` method of the service" should {
    "return the response with a header if DAO is enabled and " +
      "authenticator could be saved in backing store" in new WithApplication with Context {
        dao.save(any) answers { p => Future.successful(p.asInstanceOf[JWTAuthenticator]) }
        implicit val request = FakeRequest()

        val result = service(Some(dao)).init(authenticator, Future.successful(Results.Status(200)))

        header(settings.headerName, result) should beSome(service(None).serialize(authenticator))
      }

    "return the response with a header if DAO is disabled" in new WithApplication with Context {
      implicit val request = FakeRequest()

      val result = service(None).init(authenticator, Future.successful(Results.Status(200)))

      header(settings.headerName, result) should beSome(service(None).serialize(authenticator))
      there was no(dao).save(any)
    }

    "throws an Authentication exception if error an occurred during initialization" in new Context {
      dao.save(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()
      val okResult = Future.successful(Results.Status(200))

      await(service(Some(dao)).init(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(InitError.format(ID, ""))
      }
    }
  }

  "The `update` method of the service" should {
    "update the authenticator in backing store if DAO is enabled and send the token if " +
      "idleTimeout is enabled" in new WithApplication with Context {
        dao.save(any) answers { p => Future.successful(p.asInstanceOf[JWTAuthenticator]) }
        clock.now returns DateTime.now().plusHours(1)
        settings.authenticatorIdleTimeout returns Some(30 * 60)

        // In this case the result must be 0 because the backing store returns the updated authenticator
        // and this returned authenticator doesn't equal the passed authenticator because the
        // lastUsedDate was updated
        implicit val request = FakeRequest()
        val updatedResult = (a: Authenticator) => Future.successful(Results.Status(if (a == authenticator) 1 else 0))
        val result = service(Some(dao)).update(authenticator, updatedResult)

        status(result) must be equalTo 0
        header(settings.headerName, result) should beSome(service(None).serialize(authenticator.copy(
          lastUsedDate = clock.now
        )))
        there was one(dao).save(authenticator.copy(lastUsedDate = clock.now))
      }

    "update the authenticator if DAO is disabled and send the token if idleTimeout is enabled" in new WithApplication with Context {
      clock.now returns DateTime.now().plusHours(1)
      settings.authenticatorIdleTimeout returns Some(30 * 60)

      // In this case the result must be 0 because the updated authenticator doesn't equal the
      // passed authenticator because the lastUsedDate was updated
      implicit val request = FakeRequest()
      val updatedResult = (a: Authenticator) => Future.successful(Results.Status(if (a == authenticator) 1 else 0))
      val result = service(None).update(authenticator, updatedResult)

      status(result) must be equalTo 0
      header(settings.headerName, result) should beSome(service(None).serialize(authenticator.copy(
        lastUsedDate = clock.now
      )))
      there was no(dao).save(any)
    }

    "not update the authenticator and not send the token if idleTimeout is disabled" in new WithApplication with Context {
      clock.now returns DateTime.now().plusHours(1)
      settings.authenticatorIdleTimeout returns None

      // In this case the result must be 1 because the lastUsedDate was not updated
      // and therefore the authenticator will not be updated
      implicit val request = FakeRequest()
      val updatedResult = (a: Authenticator) => Future.successful(Results.Status(if (a == authenticator) 1 else 0))
      val result = service(Some(dao)).update(authenticator, updatedResult)

      status(result) must be equalTo 1
      header(settings.headerName, result) should beNone
    }

    "throws an Authentication exception if an error occurred during update" in new Context {
      dao.save(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()
      val okResult = (a: Authenticator) => Future.successful(Results.Status(200))

      await(service(Some(dao)).update(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(UpdateError.format(ID, ""))
      }
    }
  }

  "The `renew` method of the service" should {
    "renew the authenticator and return the response with a new JWT if DAO is enabled" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      dao.remove(any) returns Future.successful(())
      dao.save(any) answers { p => Future.successful(p.asInstanceOf[JWTAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service(Some(dao)).renew(authenticator, _ => Future.successful(Results.Status(200)))

      header(settings.headerName, result) should beSome(service(None).serialize(authenticator.copy(
        id = id,
        expirationDate = clock.now.plusSeconds(settings.authenticatorExpiry),
        lastUsedDate = clock.now
      )))
      there was one(dao).remove(authenticator.id)
    }

    "renew the authenticator and return the response with a new JWT if DAO is disabled" in new WithApplication with Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service(None).renew(authenticator, _ => Future.successful(Results.Status(200)))

      header(settings.headerName, result) should beSome(service(None).serialize(authenticator.copy(
        id = id,
        expirationDate = clock.now.plusSeconds(settings.authenticatorExpiry),
        lastUsedDate = clock.now
      )))
      there was no(dao).remove(any)
      there was no(dao).save(any)
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

      await(service(Some(dao)).renew(authenticator, okResult)) must throwA[AuthenticationException].like {
        case e =>
          e.getMessage must startWith(RenewError.format(ID, ""))
      }
    }
  }

  "The `discard` method of the service" should {
    "remove authenticator from backing store if DAO is enabled" in new Context {
      implicit val request = FakeRequest()

      dao.remove(authenticator.id) returns Future.successful(authenticator)

      await(service(Some(dao)).discard(authenticator, Future.successful(Results.Status(200))))

      there was one(dao).remove(authenticator.id)
    }

    "do not remove the authenticator from backing store if DAO is disabled" in new Context {
      implicit val request = FakeRequest()

      await(service(None).discard(authenticator, Future.successful(Results.Status(200))))

      there was no(dao).remove(authenticator.id)
    }

    "throws an Authentication exception if an error occurred during discarding" in new Context {
      implicit val request = FakeRequest()
      val okResult = Future.successful(Results.Status(200))

      dao.remove(authenticator.id) returns Future.failed(new Exception("Cannot remove authenticator"))

      await(service(Some(dao)).discard(authenticator, okResult)) must throwA[AuthenticationException].like {
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
    lazy val dao: AuthenticatorDAO[JWTAuthenticator] = mock[AuthenticatorDAO[JWTAuthenticator]]

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
      authenticatorIdleTimeout = Some(30 * 60),
      authenticatorExpiry = 12 * 60 * 60,
      sharedSecret = "fGhre3$56%43erfkl8)/§$dsdf345gsdfvsdf23kl"
    ))

    /**
     * The authenticator service instance to test.
     */
    lazy val service = (dao: Option[AuthenticatorDAO[JWTAuthenticator]]) =>
      new JWTAuthenticatorService(settings, dao, idGenerator, clock)

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
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60),
      idleTimeout = settings.authenticatorIdleTimeout
    )
  }
}
