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
package com.mohiva.play.silhouette.impl.daos

import com.mohiva.play.silhouette.api.StorableAuthenticator
import com.mohiva.play.silhouette.api.util.CacheLayer
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.{ PlaySpecification, WithApplication }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.daos.CacheAuthenticatorDAO]] class.
 */
class CacheAuthenticatorDAOSpec extends PlaySpecification with Mockito {

  "The `save` method" should {
    "save value in cache" in new WithApplication with Context {
      authenticator.id returns "test-id"
      cacheLayer.save("test-id", authenticator, 0) returns Future.successful(authenticator)

      await(dao.save(authenticator)) must be equalTo authenticator
      there was one(cacheLayer).save("test-id", authenticator, 0)
    }
  }

  "The `find` method" should {
    "return value from cache" in new WithApplication with Context {
      cacheLayer.find[StorableAuthenticator]("test-id") returns Future.successful(Some(authenticator))

      await(dao.find("test-id")) must beSome(authenticator)
      there was one(cacheLayer).find[StorableAuthenticator]("test-id")
    }

    "return None if value couldn't be found in cache" in new WithApplication with Context {
      cacheLayer.find[StorableAuthenticator]("test-id") returns Future.successful(None)

      await(dao.find("test-id")) must beNone
      there was one(cacheLayer).find[StorableAuthenticator]("test-id")
    }
  }

  "The `remove` method" should {
    "removes value from cache" in new WithApplication with Context {
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
