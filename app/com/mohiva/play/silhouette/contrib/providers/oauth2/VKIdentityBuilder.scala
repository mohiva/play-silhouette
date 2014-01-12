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
package com.mohiva.play.silhouette.contrib.providers.oauth2

import com.mohiva.play.silhouette.contrib.User
import com.mohiva.play.silhouette.core.IdentityBuilder
import com.mohiva.play.silhouette.core.providers.oauth2.VKIdentity
import play.api.mvc.RequestHeader
import play.api.i18n.Lang

/**
 * The default VK identity builder.
 */
object VKIdentityBuilder extends IdentityBuilder[VKIdentity, User] {

  /**
   * Builds an identity from an other identity.
   *
   * @param from The identity to build from.
   * @param request The request header.
   * @param lang The current lang.
   * @return The build identity instance.
   */
  def apply(from: VKIdentity)(implicit request: RequestHeader, lang: Lang) = new User(
    identityID = from.identityID,
    firstName = from.firstName,
    lastName = from.lastName,
    fullName = from.firstName + " " + from.lastName,
    email = None,
    avatarURL = from.avatarURL,
    authMethod = from.authMethod,
    oAuth2Info = Some(from.authInfo))
}
