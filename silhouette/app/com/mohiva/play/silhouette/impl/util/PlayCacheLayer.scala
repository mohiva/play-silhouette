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

import javax.inject.Inject

import com.mohiva.play.silhouette.api.util.CacheLayer
import play.api.cache.AsyncCacheApi

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
 * Implementation of the cache layer which uses the default Play cache plugin.
 *
 * @param cacheApi Plays cache API implementation.
 */
class PlayCacheLayer @Inject() (cacheApi: AsyncCacheApi) extends CacheLayer {

  /**
   * Save a value in cache.
   *
   * @param key The item key under which the value should be saved.
   * @param value The value to save.
   * @param expiration Expiration time in seconds (0 second means eternity).
   * @return The value saved in cache.
   */
  override def save[T](key: String, value: T, expiration: Duration = Duration.Inf): Future[T] = Future.successful {
    cacheApi.set(key, value, expiration)
    value
  }

  /**
   * Finds a value in the cache.
   *
   * @param key The key of the item to found.
   * @tparam T The type of the object to return.
   * @return The found value or None if no value could be found.
   */
  override def find[T: ClassTag](key: String): Future[Option[T]] = cacheApi.get[T](key)

  /**
   * Remove a value from the cache.
   *
   * @param key Item key.
   * @return An empty future to wait for removal.
   */
  override def remove(key: String) = Future.successful(cacheApi.remove(key))
}
