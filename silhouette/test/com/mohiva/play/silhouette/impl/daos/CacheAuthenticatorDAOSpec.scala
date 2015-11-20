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
package com.mohiva.play.silhouette.impl.daos

import com.mohiva.play.silhouette.api.StorableAuthenticator
import com.mohiva.play.silhouette.api.util.CacheLayer
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.daos.CacheAuthenticatorDAO]] class.
 */
class CacheAuthenticatorDAOSpec extends PlaySpecification with Mockito {

  "The `find` method" should {
    "return value from cache" in new Context {
      cacheLayer.find[StorableAuthenticator]("test-id") returns Future.successful(Some(authenticator))

      await(dao.find("test-id")) must beSome(authenticator)
      there was one(cacheLayer).find[StorableAuthenticator]("test-id")
    }

    "return None if value couldn't be found in cache" in new Context {
      cacheLayer.find[StorableAuthenticator]("test-id") returns Future.successful(None)

      await(dao.find("test-id")) must beNone
      there was one(cacheLayer).find[StorableAuthenticator]("test-id")
    }
  }

  "The `add` method" should {
    "add value in cache" in new Context {
      authenticator.id returns "test-id"
      cacheLayer.save("test-id", authenticator, Duration.Inf) returns Future.successful(authenticator)

      await(dao.add(authenticator)) must be equalTo authenticator
      there was one(cacheLayer).save("test-id", authenticator, Duration.Inf)
    }
  }

  "The `update` method" should {
    "update value in cache" in new Context {
      authenticator.id returns "test-id"
      cacheLayer.save("test-id", authenticator, Duration.Inf) returns Future.successful(authenticator)

      await(dao.update(authenticator)) must be equalTo authenticator
      there was one(cacheLayer).save("test-id", authenticator, Duration.Inf)
    }
  }

  "The `remove` method" should {
    "remove value from cache" in new Context {
      cacheLayer.remove("test-id") returns Future.successful(())

      await(dao.remove("test-id")) must be equalTo (())
      there was one(cacheLayer).remove("test-id")
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * A storable authenticator.
     */
    lazy val authenticator = mock[StorableAuthenticator]

    /**
     * The cache layer implementation.
     */
    lazy val cacheLayer = mock[CacheLayer]

    /**
     * The dAO to test.
     */
    lazy val dao = new CacheAuthenticatorDAO[StorableAuthenticator](cacheLayer)
  }
}
