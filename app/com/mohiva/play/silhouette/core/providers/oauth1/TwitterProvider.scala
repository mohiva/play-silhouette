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
package com.mohiva.play.silhouette.core.providers.oauth1

import play.api.libs.oauth.{ RequestToken, OAuthCalculator }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth1Info, OAuth1Settings, OAuth1Provider }
import com.mohiva.play.silhouette.core.services.AuthInfoService
import TwitterProvider._

/**
 * A Twitter OAuth1 Provider.
 *
 * @param authInfoService The auth info service.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 */
class TwitterProvider(
  protected val authInfoService: AuthInfoService,
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth1Settings)
    extends OAuth1Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Twitter

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return The social profile.
   */
  protected def buildProfile(authInfo: OAuth1Info): Future[SocialProfile] = {
    val sign = OAuthCalculator(serviceInfo.key, RequestToken(authInfo.token, authInfo.secret))
    httpLayer.url(API).sign(sign).get().map { response =>
      val json = response.json
      val userId = (json \ ID).as[Int]
      val name = (json \ Name).asOpt[String]
      val avatarURL = (json \ ProfileImage).asOpt[String]

      SocialProfile(
        loginInfo = LoginInfo(id, userId.toString),
        fullName = name,
        avatarURL = avatarURL)
    }.recover { case e => throw new AuthenticationException(UnspecifiedProfileError.format(id), e) }
  }
}

/**
 * The companion object.
 */
object TwitterProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information"

  /**
   * The LinkedIn constants.
   */
  val Twitter = "twitter"
  val API = "https://api.twitter.com/1.1/account/verify_credentials.json"
  val ID = "id"
  val Name = "name"
  val ProfileImage = "profile_image_url_https"
}
