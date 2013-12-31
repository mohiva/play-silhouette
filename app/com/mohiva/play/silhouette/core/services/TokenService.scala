/**
 * Copyright 2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
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
 *
 * This file contains source code from the Secure Social project:
 * http://securesocial.ws/
 */
package com.mohiva.play.silhouette.core.services

import scala.concurrent.Future
import com.mohiva.play.silhouette.core.providers.Token

/**
 * A trait that provides the means to find and save tokens for the Silhouette module if
 * the Credentials provider is enabled.
 *
 * Tokens are needed for users that are creating an account in the system instead of using
 * one in a 3rd party system.
 */
trait TokenService[T <: Token] {

  /**
   * Saves a token.
   *
   * @param token The token to save.
   * @return The saved token or None if the token couldn't be saved.
   */
  def save(token: T): Future[Option[T]]

  /**
   * Finds a token.
   *
   * @param id The token ID.
   * @return The found token or None if no token couldn't be found for the given ID.
   */
  def findByID(id: String): Future[Option[T]]

  /**
   * Deletes a token.
   *
   * @param id The ID of the token to delete.
   */
  def deleteByID(id: String)

  /**
   * Deletes all expired tokens.
   */
  def deleteExpired()
}
