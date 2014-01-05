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
package com.mohiva.play.silhouette.core.utils

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * A trait which provides a mockable implementation for a cache.
 */
trait CacheLayer {

  /**
   * Set a value into the cache.
   *
   * @param key Item key.
   * @param value Item value.
   * @param expiration Expiration time in seconds (0 second means eternity).
   */
  def set[T](key: String, value: T, expiration: Int = 0): Future[Option[T]]

  /**
   * Retrieve a value from the cache.
   *
   * @param key Item key.
   * @tparam T The type of the object to return.
   */
  def get[T](key: String)(implicit classTag: ClassTag[T]): Future[Option[T]]

  /**
   * Remove a value from the cache.
   */
  def remove(key: String)
}
