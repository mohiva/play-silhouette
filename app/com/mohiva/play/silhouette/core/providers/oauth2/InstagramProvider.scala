/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Brian Porter (poornerd at gmail dot com) - twitter: @poornerd
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
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import InstagramProvider._

/**
 * An Instagram OAuth2 provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 *
 * @see http://instagram.com/developer/authentication/
 * @see http://instagram.com/developer/endpoints/
 */
abstract class InstagramProvider(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings)
    extends OAuth2Provider(cacheLayer, httpLayer, settings) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Instagram

  /**
   * Gets the API URL to retrieve the profile data.
   *
   * @return The API URL to retrieve the profile data.
   */
  protected def profileAPI = API

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "meta" \ "code").asOpt[Int] match {
        case Some(code) if code != 200 =>
          val errorType = (json \ "meta" \ "error_type").asOpt[String]
          val errorMsg = (json \ "meta" \ "error_message").asOpt[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, code, errorType, errorMsg))
        case _ => parseProfile(parser(authInfo), json).asFuture
      }
    }
  }

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser = (authInfo: OAuth2Info) => (json: JsValue) => {
    val data = json \ "data"
    val userID = (data \ "id").as[String]
    val fullName = (data \ "full_name").asOpt[String]
    val avatarURL = (data \ "profile_picture").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(id, userID),
      authInfo = authInfo,
      fullName = fullName,
      avatarURL = avatarURL)
  }
}

/**
 * The companion object.
 */
object InstagramProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, type: %s, message: %s"

  /**
   * The Instagram constants.
   */
  val Instagram = "instagram"
  val API = "https://api.instagram.com/v1/users/self?access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param cacheLayer The cache layer implementation.
   * @param httpLayer The HTTP layer implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings) = {
    new InstagramProvider(cacheLayer, httpLayer, settings) with CommonSocialProfileBuilder[OAuth2Info]
  }
}
