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
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import FoursquareProvider._

/**
 * A Foursquare OAuth2 provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 *
 * @see https://developer.foursquare.com/overview/auth
 * @see https://developer.foursquare.com/overview/responses
 * @see https://developer.foursquare.com/docs/explore
 */
abstract class FoursquareProvider(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings)
    extends OAuth2Provider(cacheLayer, httpLayer, settings) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Foursquare

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
    val version = settings.customProperties.getOrElse(APIVersion, DefaultAPIVersion)
    httpLayer.url(profileAPI.format(authInfo.accessToken, version)).get().flatMap { response =>
      val json = response.json
      val errorType = (json \ "meta" \ "errorType").asOpt[String]
      (json \ "meta" \ "code").asOpt[Int] match {
        case Some(code) if code != 200 =>
          val errorDetail = (json \ "meta" \ "errorDetail").asOpt[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, code, errorType, errorDetail))
        case _ =>
          // Status code 200 and an existing errorType can only be a deprecated error
          // https://developer.foursquare.com/overview/responses
          if (errorType.isDefined) {
            logger.info("This implementation may be deprecated! Please contact the Silhouette team for a fix!")
          }

          parseProfile(parser(authInfo), json).asFuture
      }
    }
  }

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser = (authInfo: OAuth2Info) => (json: JsValue) => {
    val user = json \ "response" \ "user"
    val userID = (user \ "id").as[String]
    val lastName = (user \ "lastName").asOpt[String]
    val firstName = (user \ "firstName").asOpt[String]
    val avatarURLPart1 = (user \ "photo" \ "prefix").asOpt[String]
    val avatarURLPart2 = (user \ "photo" \ "suffix").asOpt[String]
    val email = (user \ "contact" \ "email").asOpt[String].filter(!_.isEmpty)
    val resolution = settings.customProperties.getOrElse(AvatarResolution, DefaultAvatarResolution)

    CommonSocialProfile(
      loginInfo = LoginInfo(id, userID),
      authInfo = authInfo,
      firstName = firstName,
      lastName = lastName,
      avatarURL = for (prefix <- avatarURLPart1; postfix <- avatarURLPart2) yield prefix + resolution + postfix,
      email = email)
  }
}

/**
 * The companion object.
 */
object FoursquareProvider {

  /**
   * The version of this implementation.
   *
   * @see https://developer.foursquare.com/overview/versioning
   */
  val DefaultAPIVersion = "20140206"

  /**
   * The default avatar resolution.
   */
  val DefaultAvatarResolution = "100x100"

  /**
   * Some custom properties for this provider.
   */
  val APIVersion = "api.version"
  val AvatarResolution = "avatar.resolution"

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, type: %s, detail: %s"

  /**
   * The Foursquare constants.
   */
  val Foursquare = "foursquare"
  val API = "https://api.foursquare.com/v2/users/self?oauth_token=%s&v=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param cacheLayer The cache layer implementation.
   * @param httpLayer The HTTP layer implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings) = {
    new FoursquareProvider(cacheLayer, httpLayer, settings) with CommonSocialProfileBuilder[OAuth2Info]
  }
}
