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

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
 * Implementation of the authenticator DAO which uses the cache layer to persist the authenticator.
 *
 * @param cacheLayer The cache layer implementation.
 * @tparam T The type of the authenticator to store.
 */
class CacheAuthenticatorDAO[T <: StorableAuthenticator: ClassTag: Manifest](cacheLayer: CacheLayer) extends AuthenticatorDAO[T] {

  /**
   * Finds the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return The found authenticator or None if no authenticator could be found for the given ID.
   */
  def find(id: String): Future[Option[T]] = cacheLayer.find[T](id)

  /**
   * Adds a new authenticator.
   *
   * @param authenticator The authenticator to add.
   * @return The added authenticator.
   */
  def add(authenticator: T): Future[T] = cacheLayer.save[T](authenticator.id, authenticator, Duration.Inf)

  /**
   * Updates an already existing authenticator.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator.
   */
  def update(authenticator: T): Future[T] = cacheLayer.save[T](authenticator.id, authenticator, Duration.Inf)

  /**
   * Removes the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return An empty future.
   */
  def remove(id: String): Future[Unit] = cacheLayer.remove(id)
}
