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

import com.mohiva.play.silhouette._
import com.mohiva.play.silhouette.api.StorableAuthenticator
import com.mohiva.play.silhouette.impl.authenticators.{ CookieAuthenticator, CookieSerializationStrategy }

import scala.concurrent.Future
import scala.util.Try

/**
 * The DAO to persist the authenticator.
 *
 * @tparam T The type of the authenticator to store.
 */
trait AuthenticatorDAO[T <: StorableAuthenticator] {

  /**
   * Finds the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return The found authenticator or None if no authenticator could be found for the given ID.
   */
  def find(id: String): Future[Option[T]]

  /**
   * Adds a new authenticator.
   *
   * @param authenticator The authenticator to add.
   * @return The added authenticator.
   */
  def add(authenticator: T): Future[T]

  /**
   * Updates an already existing authenticator.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator.
   */
  def update(authenticator: T): Future[T]

  /**
   * Removes the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return An empty future.
   */
  def remove(id: String): Future[Unit]
}

/**
 * The companion object.
 */
object AuthenticatorDAO {

  /**
   * Creates AuthenticatorDAO that wraps CookieSerializationStrategy. As a result, custom serialization configuration
   * is used through the fake DAO.
   *
   * This hack was introduced in order to allow using best practices without making any backward compatibility breaks.
   *
   * @param cookieSerializationStrategy The serialization strategy to be wrapped by DAO.
   * @return The DAO wrapping the CookieSerializationStrategy.
   */
  def forCookieSerializationStrategy(cookieSerializationStrategy: CookieSerializationStrategy) = new AuthenticatorDAO[CookieAuthenticator]() {

    /**
     * Finds the authenticator for the given ID.
     *
     * @param id The authenticator ID.
     * @return The found authenticator or None if no authenticator could be found for the given ID.
     */
    override def find(id: String): Future[Option[CookieAuthenticator]] = Future.from(cookieSerializationStrategy.unserialize(id).map(Some(_)))

    /**
     * Removes the authenticator for the given ID.
     *
     * @param id The authenticator ID.
     * @return An empty future.
     */
    override def remove(id: String): Future[Unit] = Future.successful(())

    /**
     * Adds a new authenticator.
     *
     * @param authenticator The authenticator to add.
     * @return The added authenticator.
     */
    override def add(authenticator: CookieAuthenticator): Future[CookieAuthenticator] = Future.from(Try {
      authenticator.copy(id = cookieSerializationStrategy.serialize(authenticator))
    })

    /**
     * Updates an already existing authenticator.
     *
     * @param authenticator The authenticator to update.
     * @return The updated authenticator.
     */
    override def update(authenticator: CookieAuthenticator): Future[CookieAuthenticator] = add(authenticator)
  }
}
