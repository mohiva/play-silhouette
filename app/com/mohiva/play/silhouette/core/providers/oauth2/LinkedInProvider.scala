/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Greg Methvin (greg at methvin dot net)
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
package com.mohiva.play.silhouette.core.providers.oauth2

import scala.concurrent.Future
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth2Info, OAuth2Settings, OAuth2Provider }
import com.mohiva.play.silhouette.core.providers.helpers.LinkedInProfile._
import com.mohiva.play.silhouette.core.services.AuthInfoService
import LinkedInProvider._

/**
 * A LinkedIn OAuth2 Provider.
 *
 * @param authInfoService The auth info service.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 */
class LinkedInProvider(
  protected val authInfoService: AuthInfoService,
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth2Settings)
    extends OAuth2Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = LinkedIn

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return The social profile.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[SocialProfile] = {
    build(httpLayer.url(API.format(authInfo.accessToken)).get())
  }
}

/**
 * The companion object.
 */
object LinkedInProvider {

  /**
   * The LinkedIn constants.
   */
  val API = BaseAPI + "&oauth2_access_token=%s"
}
