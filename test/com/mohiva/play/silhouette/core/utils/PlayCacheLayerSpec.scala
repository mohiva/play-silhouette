/**
 * Copyright 2014 Christian Kaps (christian.kaps at mohiva dot com)
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
package com.mohiva.play.silhouette.core.utils

import test.AfterWithinAround
import play.api.test.{WithApplication, PlaySpecification}
import play.api.cache.Cache
import play.api.Play.current
import org.joda.time.DateTime
import org.specs2.specification.Scope

/**
 * Test case for the [[com.mohiva.play.silhouette.core.utils.PlayCacheLayer]] class.
 */
class PlayCacheLayerSpec extends PlaySpecification {

  "The set method" should {
    "save value in cache" in new WithApplication with Context {
      await(layer.set("id", value))

      Cache.getAs[DateTime]("id") should beSome(value)
    }
  }

  "The get method" should {
    "return value from cache" in new WithApplication with Context {
      Cache.set("id", value)

      await(layer.get[DateTime]("id")) should beSome(value)
    }
  }

  "The remove method" should {
    "removes value from cache" in new WithApplication with Context {
      Cache.set("id", value)

      layer.remove("id")

      Cache.getAs[DateTime]("id") should beNone
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope with AfterWithinAround {

    /**
     * The layer to test.
     */
    lazy val layer = new PlayCacheLayer

    /**
     * The value to cache.
     */
    lazy val value = new DateTime

    /**
     * Clears the cache after every test
     */
    def after {
      Cache.remove("id")
    }
  }
}
