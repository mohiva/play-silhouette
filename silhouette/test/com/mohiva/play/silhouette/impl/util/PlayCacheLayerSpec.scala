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
package com.mohiva.play.silhouette.impl.util

import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.cache.CacheApi
import play.api.test.PlaySpecification

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.util.PlayCacheLayer]] class.
 */
class PlayCacheLayerSpec extends PlaySpecification with Mockito {

  "The `find` method" should {
    "return value from cache" in new Context {
      cacheAPI.get[DateTime]("id") returns Some(value)

      await(layer.find[DateTime]("id")) should beSome(value)

      there was one(cacheAPI).get[DateTime]("id")
    }
  }

  "The `save` method" should {
    "save value in cache" in new Context {
      await(layer.save("id", value))

      there was one(cacheAPI).set("id", value, Duration.Inf)
    }
  }

  "The `remove` method" should {
    "removes value from cache" in new Context {
      await(layer.remove("id")) must be equalTo (())

      there was one(cacheAPI).remove("id")
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The cache API.
     */
    lazy val cacheAPI = mock[CacheApi]

    /**
     * The layer to test.
     */
    lazy val layer = new PlayCacheLayer(cacheAPI)

    /**
     * The value to cache.
     */
    lazy val value = new DateTime
  }
}
