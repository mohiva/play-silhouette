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
import play.api.mvc.Results
import play.api.test.{ FakeRequest, PlaySpecification }
import com.mohiva.play.silhouette.core.utils.{ CacheLayer, Clock, IDGenerator }
import com.mohiva.play.silhouette.core.{ Authenticator, Identity, LoginInfo }

/**
 * Test case for the [[com.mohiva.play.silhouette.contrib.authenticators.HeaderAuthenticator]].
 */
class HeaderAuthenticatorSpec extends PlaySpecification with Mockito {

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
    "return an authenticator with the generated ID" in new Context {
      implicit val request = FakeRequest()
      val id = "test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns new DateTime
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).id must be equalTo id
    }

    "return an authenticator with the current date as lastUsedDate" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).lastUsedDate must be equalTo now
    }

    "return an authenticator which expires in 12 hours(default value)" in new Context {
      implicit val request = FakeRequest()
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).expirationDate must be equalTo now.plusMinutes(12 * 60)
    }

    "return an authenticator which expires in 6 hours" in new Context {
      implicit val request = FakeRequest()
      val sixHours = 6 * 60 * 60
      val now = new DateTime

      settings.authenticatorExpiry returns sixHours
      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      await(service.create(identity)).expirationDate must be equalTo now.plusSeconds(sixHours)
    }
  }

  "The `retrieve` method of the service" should {
    "return None if no authenticator header exists" in new Context {
      implicit val request = FakeRequest()

      await(service.retrieve) must beNone
    }

    "return None if no authenticator is stored for the header" in new Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> authenticator.id)

      cacheLayer.get[HeaderAuthenticator](authenticator.id) returns Future.successful(None)

      await(service.retrieve) must beNone
    }

    "return authenticator if an authenticator is stored for the header" in new Context {
      implicit val request = FakeRequest().withHeaders(settings.headerName -> authenticator.id)

      cacheLayer.get[HeaderAuthenticator](authenticator.id) returns Future.successful(Some(authenticator))

      await(service.retrieve) must beSome(authenticator)
    }
  }

  "The `init` method of the service" should {
    "return the response with a header if authenticator could be saved in cache" in new Context {
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(Some(authenticator))

      implicit val request = FakeRequest()
      val result = service.init(authenticator, Future.successful(Results.Status(200)))

      header(settings.headerName, result) should beSome(authenticator.id)
    }

    "return the response without a header if authenticator couldn't be saved in cache" in new Context {
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(None)

      implicit val request = FakeRequest()
      val okResult = Results.Status(200)
      val result = await(service.init(authenticator, Future.successful(okResult)))

      result must be equalTo okResult
    }
  }

  "The `update` method of the service" should {
    "update the authenticator in cache" in new Context {
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(Some(authenticator))

      implicit val request = FakeRequest()

      await(service.update(authenticator, _ => Future.successful(Results.Status(200))))

      there was one(cacheLayer).set(authenticator.id, authenticator.copy(lastUsedDate = clock.now), 0)
    }

    "return the result if the authenticator could be stored in cache" in new Context {
      cacheLayer.set[Authenticator](any, any, any) answers { (a, m) =>
        Future.successful(Some(a.asInstanceOf[Array[Any]](1).asInstanceOf[Authenticator]))
      }

      // In this case the result must be 0 because the cache returns the updated authenticator
      // and this returned authenticator doesn't equal the passed authenticator because the
      // lastUsedDate was updated
      implicit val request = FakeRequest()
      val result = service.update(authenticator, a => Future.successful(Results.Status(if (a == authenticator) 1 else 0)))

      status(result) must be equalTo 0
    }

    "return None if something went wrong" in new Context {
      cacheLayer.set[Authenticator](any, any, any) returns Future.successful(None)

      // In this case the result must be 1 because the cache returns None and so the update
      // method returns the original passed authenticator
      implicit val request = FakeRequest()
      val result = service.update(authenticator, a => Future.successful(Results.Status(if (a == authenticator) 1 else 0)))

      status(result) must be equalTo 1
    }
  }

  "The `discard` method of the service" should {
    "remove authenticator from cache" in new Context {
      implicit val request = FakeRequest()
      service.discard(authenticator, Future.successful(Results.Status(200)))

      there was one(cacheLayer).remove(authenticator.id)
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
    lazy val settings = spy(HeaderAuthenticatorSettings(
      headerName = "X-Auth-Token",
      authenticatorIdleTimeout = 30 * 60,
      authenticatorExpiry = 12 * 60 * 60
    ))

    /**
     * The cache service instance to test.
     */
    lazy val service = new HeaderAuthenticatorService(settings, cacheLayer, idGenerator, clock)

    /**
     * An identity.
     */
    lazy val identity = new Identity {
      val loginInfo = LoginInfo("test", "1")
    }

    /**
     * An authenticator.
     */
    lazy val authenticator = new HeaderAuthenticator(
      id = "test-id",
      loginInfo = LoginInfo("test", "1"),
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60),
      idleTimeout = settings.authenticatorIdleTimeout
    )
  }
}
