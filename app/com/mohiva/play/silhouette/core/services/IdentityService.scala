/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.core.services

import scala.concurrent.Future
import com.mohiva.play.silhouette.core.{Identity, LoginInfo}

/**
 * A trait that provides the means to find and save identities
 * for the Silhouette module.
 */
trait IdentityService[T <: Identity] {

  /**
   * Saves an identity.
   *
   * This method gets called when a user logs in(social auth) or registers.
   * This is your chance to save the user information in your backing store.
   *
   * @param identity The identity to save.
   * @return The saved identity or None if the identity couldn't be saved.
   */
  def save(identity: T): Future[Option[T]]

  /**
   * Finds an identity that matches the specified login info.
   *
   * @param loginInfo The login info to find an identity.
   * @return The found identity or None if no identity could be found for the given login info.
   */
  def findByLoginInfo(loginInfo: LoginInfo): Future[Option[T]]
}
