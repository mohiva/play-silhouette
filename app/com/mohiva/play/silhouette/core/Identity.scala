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
package com.mohiva.play.silhouette.core

/**
 * This trait represents an authenticated user.
 */
trait Identity {

  /**
   * Gets the linked login info for an identity.
   *
   * @return The linked login info for an identity.
   */
  def loginInfo: LoginInfo
}

/**
 * Represents a linked login for an identity (i.e. a local username/password or a facebook/google account.
 *
 * The login info contains the data about the provider that authenticated that identity.
 *
 * @param providerID The ID of the provider.
 * @param providerKey A unique key which identifies a user on this provider (userID, email, ...).
 */
case class LoginInfo(providerID: String, providerKey: String)
