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

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.services.AuthenticatorService._
import com.mohiva.play.silhouette.api.util.{ Clock, IDGenerator }
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticatorService._
import com.mohiva.play.silhouette.impl.daos.AuthenticatorDAO
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.Results
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator]].
 */
class BearerTokenAuthenticatorSpec extends PlaySpecification with Mockito {

  "The `isValid` method of the authenticator" should {
    "return false if the authenticator is expired" in new Context {
      authenticator.copy(expirationDate = DateTime.now.minusHours(1)).isValid must beFalse
    }

    "return false if the authenticator is timed out" in new Context {
      authenticator.copy(
        lastUsedDate = DateTime.now.minusSeconds(settings.authenticatorIdleTimeout.get + 1)
      ).isValid must beFalse
    }

    "return true if the authenticator is valid" in new Context {
      authenticator.copy(
        lastUsedDate = DateTime.now.minusSeconds(settings.authenticatorIdleTimeout.get - 10),
        expirationDate = DateTime.now.plusSeconds(5)
      ).isValid must beTrue
    }
  }

  "The `create` method of the service" should {
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

    "throws an AuthenticatorCreationException exception if an error occurred during creation" in new Context {
      implicit val request = FakeRequest()

      idGenerator.generate returns Future.failed(new Exception("Could not generate ID"))

      await(service.create(loginInfo)) must throwA[AuthenticatorCreationException].like {
        case e =>
          e.getMessage must startWith(CreateError.format(ID, ""))
      }
    }
  }

  "The `retrieve` method of the service" should {
    "return None if no authenticator header exists" in new Context {
      implicit val request = FakeRequest()

      await(service.retrieve) must beNone
    }

    "return None if no authenticator is stored for the header" in new Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> authenticator.id)

      dao.find(authenticator.id) returns Future.successful(None)

      await(service.retrieve) must beNone
    }

    "return authenticator if an authenticator is stored for the header" in new Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> authenticator.id)

      dao.find(authenticator.id) returns Future.successful(Some(authenticator))

      await(service.retrieve) must beSome(authenticator)
    }

    "throws an AuthenticatorRetrievalException exception if an error occurred during retrieval" in new Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> authenticator.id)

      dao.find(authenticator.id) returns Future.failed(new RuntimeException("Cannot find authenticator"))

      await(service.retrieve) must throwA[AuthenticatorRetrievalException].like {
        case e =>
          e.getMessage must startWith(RetrieveError.format(ID, ""))
      }
    }
  }

  "The `init` method of the service" should {
    "save the authenticator in backing store" in new Context {
      dao.add(any) answers { p => Future.successful(p.asInstanceOf[BearerTokenAuthenticator]) }

      implicit val request = FakeRequest()
      val token = await(service.init(authenticator))

      token must be equalTo authenticator.id
      there was one(dao).add(authenticator)
    }

    "throws an AuthenticatorInitializationException exception if an error occurred during initialization" in new Context {
      dao.add(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()

      await(service.init(authenticator)) must throwA[AuthenticatorInitializationException].like {
        case e =>
          e.getMessage must startWith(InitError.format(ID, ""))
      }
    }
  }

  "The result `embed` method of the service" should {
    "return the response with a header" in new Context {
      implicit val request = FakeRequest()
      val value = authenticator.id
      val result = service.embed(value, Results.Ok)

      header(settings.headerName, result) should beSome(authenticator.id)
    }
  }

  "The request `embed` method of the service" should {
    "return the request with a header" in new Context {
      val value = authenticator.id
      val request = service.embed(value, FakeRequest())

      request.headers.get(settings.headerName) should beSome(authenticator.id)
    }

    "override an existing header" in new Context {
      val value = authenticator.id
      val request = service.embed(value, FakeRequest().withHeaders(settings.headerName -> "test"))

      request.headers.get(settings.headerName) should beSome(authenticator.id)
    }

    "keep non authenticator related headers" in new Context {
      val value = authenticator.id
      val request = service.embed(value, FakeRequest().withHeaders("test" -> "test"))

      request.headers.get(settings.headerName) should beSome(authenticator.id)
      request.headers.get("test") should beSome("test")
    }
  }

  "The `touch` method of the service" should {
    "update the last used date if idle timeout is defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns Some(1)
      clock.now returns DateTime.now

      service.touch(authenticator) must beLeft[BearerTokenAuthenticator].like {
        case a =>
          a.lastUsedDate must be equalTo clock.now
      }
    }

    "do not update the last used date if idle timeout is not defined" in new WithApplication with Context {
      settings.authenticatorIdleTimeout returns None
      clock.now returns DateTime.now

      service.touch(authenticator) must beRight[BearerTokenAuthenticator].like {
        case a =>
          a.lastUsedDate must be equalTo authenticator.lastUsedDate
      }
    }
  }

  "The `update` method of the service" should {
    "update the authenticator in backing store" in new Context {
      dao.update(any) returns Future.successful(authenticator)

      implicit val request = FakeRequest()

      await(service.update(authenticator, Results.Ok))

      there was one(dao).update(authenticator)
    }

    "return the result if the authenticator could be stored in backing store" in new Context {
      dao.update(any) answers { p => Future.successful(p.asInstanceOf[BearerTokenAuthenticator]) }

      implicit val request = FakeRequest()
      val result = service.update(authenticator, Results.Ok)

      status(result) must be equalTo OK
    }

    "throws an AuthenticatorUpdateException exception if an error occurred during update" in new Context {
      dao.update(any) returns Future.failed(new Exception("Cannot store authenticator"))

      implicit val request = FakeRequest()

      await(service.update(authenticator, Results.Ok)) must throwA[AuthenticatorUpdateException].like {
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
      dao.add(any) answers { p => Future.successful(p.asInstanceOf[BearerTokenAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      await(service.renew(authenticator, Results.Ok))

      there was one(dao).remove(authenticator.id)
    }

    "renew the authenticator and return the response with a new bearer token" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      dao.remove(any) returns Future.successful(())
      dao.add(any) answers { p => Future.successful(p.asInstanceOf[BearerTokenAuthenticator]) }
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      val result = service.renew(authenticator, Results.Ok)

      header(settings.headerName, result) should beSome(id)
    }

    "throws an AuthenticatorRenewalException exception if an error occurred during renewal" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime
      val id = "new-test-id"

      dao.remove(any) returns Future.successful(())
      dao.add(any) returns Future.failed(new Exception("Cannot store authenticator"))
      idGenerator.generate returns Future.successful(id)
      clock.now returns now

      await(service.renew(authenticator, Results.Ok)) must throwA[AuthenticatorRenewalException].like {
        case e =>
          e.getMessage must startWith(RenewError.format(ID, ""))
      }
    }
  }

  "The `discard` method of the service" should {
    "remove authenticator from backing store" in new Context {
      implicit val request = FakeRequest()

      dao.remove(authenticator.id) returns Future.successful(authenticator)

      await(service.discard(authenticator, Results.Ok))

      there was one(dao).remove(authenticator.id)
    }

    "throws an AuthenticatorDiscardingException exception if an error occurred during discarding" in new Context {
      implicit val request = FakeRequest()
      val okResult = Results.Ok

      dao.remove(authenticator.id) returns Future.failed(new Exception("Cannot remove authenticator"))

      await(service.discard(authenticator, okResult)) must throwA[AuthenticatorDiscardingException].like {
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
    lazy val dao: AuthenticatorDAO[BearerTokenAuthenticator] = mock[AuthenticatorDAO[BearerTokenAuthenticator]]

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
    lazy val settings = spy(BearerTokenAuthenticatorSettings(
      headerName = "X-Auth-Token",
      authenticatorIdleTimeout = Some(30 * 60),
      authenticatorExpiry = 12 * 60 * 60
    ))

    /**
     * The authenticator service instance to test.
     */
    lazy val service = new BearerTokenAuthenticatorService(settings, dao, idGenerator, clock)

    /**
     * The login info.
     */
    lazy val loginInfo = LoginInfo("test", "1")

    /**
     * An authenticator.
     */
    lazy val authenticator = new BearerTokenAuthenticator(
      id = "test-id",
      loginInfo = LoginInfo("test", "1"),
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60),
      idleTimeout = settings.authenticatorIdleTimeout
    )
  }
}
