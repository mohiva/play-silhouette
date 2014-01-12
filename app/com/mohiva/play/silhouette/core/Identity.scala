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

import play.api.mvc.RequestHeader
import play.api.i18n.Lang

/**
 * This trait represents a user.
 */
trait Identity {

  /**
   * Gets the identity ID.
   *
   * @return The identity ID.
   */
  def identityID: IdentityID

  /**
   * Gets the authentication method.
   *
   * @return The authentication method.
   */
  def authMethod: AuthenticationMethod
}

/**
 * The ID of an Identity.
 *
 * @param userID The user ID on the provider the user came from (eg: twitter, facebook).
 * @param providerID The provider used to sign in.
 */
case class IdentityID(userID: String, providerID: String)

/**
 * Builds an identity from another one.
 *
 * @tparam F The type of the identity to build from.
 * @tparam T The type of the identity to build to.
 */
trait IdentityBuilder[F, T] {

  /**
   * Builds an identity from an other identity.
   *
   * @param from The identity to build from.
   * @param request The request header.
   * @param lang The current lang.
   * @return The build identity instance.
   */
  def apply(from: F)(implicit request: RequestHeader, lang: Lang): T
}
