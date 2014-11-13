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

import scala.concurrent.Future

/**
 * The DAO to persist the authenticator.
 *
 * @tparam T The type of the authenticator to store.
 */
trait AuthenticatorDAO[T <: StorableAuthenticator] {

  /**
   * Saves the authenticator.
   *
   * @param authenticator The authenticator to save.
   * @return The saved authenticator.
   */
  def save(authenticator: T): Future[T]

  /**
   * Finds the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return The found authenticator or None if no authenticator could be found for the given ID.
   */
  def find(id: String): Future[Option[T]]

  /**
   * Removes the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return An empty future.
   */
  def remove(id: String): Future[Unit]
}
