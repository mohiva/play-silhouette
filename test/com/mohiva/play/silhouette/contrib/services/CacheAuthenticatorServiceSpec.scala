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
import scala.reflect.ClassTag
import play.api.test.PlaySpecification
import com.mohiva.play.silhouette.core.{ Identity, Authenticator, LoginInfo }
import com.mohiva.play.silhouette.core.utils.{ Clock, IDGenerator, CacheLayer }

/**
 * Test case for the [[com.mohiva.play.silhouette.contrib.services.CookieAuthenticatorService]] class.
 */
class CacheAuthenticatorServiceSpec extends PlaySpecification with Mockito {

  "The create method" should {
    "return an authenticator with the generated ID" in new Context {
      val id = "test-id"

      idGenerator.generate returns Future.successful(id)
      clock.now returns new DateTime

      await(service.create(identity)).id must be equalTo id
    }

    "return an authenticator with the current date as creationDate" in new Context {
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service.create(identity)).creationDate must be equalTo now
    }

    "return an authenticator with the current date as lastUsedDate" in new Context {
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service.create(identity)).lastUsedDate must be equalTo now
    }

    "return an authenticator which expire in 12 hours(default value)" in new Context {
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service.create(identity)).expirationDate must be equalTo now.plusMinutes(12 * 60)
    }

    "return an authenticator which expire in 6 hours" in new Context {
      val sixHours = 6 * 60
      val now = new DateTime

      idGenerator.generate returns Future.successful("test-id")
      clock.now returns now

      await(service.create(identity, sixHours)).expirationDate must be equalTo now.plusMinutes(sixHours)
    }
  }

  "The save method" should {
    "save the authenticator in cache" in new Context {
      cacheLayer.set[Authenticator](authenticator.id, authenticator, 0) returns Future.successful(Some(authenticator))

      await(service.save(authenticator))

      there was one(cacheLayer).set(authenticator.id, authenticator, 0)
    }

    "return the given authenticator if all going well" in new Context {
      cacheLayer.set[Authenticator](authenticator.id, authenticator, 0) returns Future.successful(Some(authenticator))

      await(service.save(authenticator)) must beSome(authenticator)
    }

    "return the None if something went wrong" in new Context {
      cacheLayer.set[Authenticator](authenticator.id, authenticator, 0) returns Future.successful(None)

      await(service.save(authenticator)) must beNone
    }
  }

  "The update method" should {
    "update the authenticator in cache" in new Context {
      cacheLayer.set[Authenticator](authenticator.id, authenticator, 0) returns Future.successful(Some(authenticator))

      await(service.update(authenticator))

      there was one(cacheLayer).set(authenticator.id, authenticator, 0)
    }

    "return the given authenticator if all going well" in new Context {
      cacheLayer.set[Authenticator](authenticator.id, authenticator, 0) returns Future.successful(Some(authenticator))

      await(service.update(authenticator)) must beSome(authenticator)
    }

    "return the None if something went wrong" in new Context {
      cacheLayer.set[Authenticator](authenticator.id, authenticator, 0) returns Future.successful(None)

      await(service.update(authenticator)) must beNone
    }
  }

  "The findByID method" should {
    "return the found authenticator from cache" in new Context {
      cacheLayer.get[Authenticator](authenticator.id) returns Future.successful(Some(authenticator))

      await(service.findByID("test-id")) should beSome(authenticator)
    }

    "return None if no authenticator could be found in cache" in new Context {
      cacheLayer.get[Authenticator](authenticator.id) returns Future.successful(None)

      await(service.findByID("test-id")) should beNone
    }
  }

  "The deleteByID method" should {
    "remove the authenticator from cache" in new Context {
      service.deleteByID("test-id")

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
     * The cache service instance to test.
     */
    lazy val service = new CookieAuthenticatorService(cacheLayer, idGenerator, clock)

    /**
     * An identity.
     */
    lazy val identity = new Identity {
      val loginInfo = LoginInfo("test", "1")
    }

    /**
     * An authenticator.
     */
    lazy val authenticator = new Authenticator(
      id = "test-id",
      loginInfo = LoginInfo("test", "1"),
      creationDate = DateTime.now,
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60)
    )

    /**
     * The class tag for the authenticator.
     */
    implicit lazy val classTag = ClassTag(authenticator.getClass)
  }
}
