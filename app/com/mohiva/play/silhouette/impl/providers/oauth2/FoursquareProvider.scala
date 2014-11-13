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
package com.mohiva.play.silhouette.impl.providers.oauth2

import com.mohiva.play.silhouette._
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.FoursquareProvider._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * A Foursquare OAuth2 provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 *
 * @see https://developer.foursquare.com/overview/auth
 * @see https://developer.foursquare.com/overview/responses
 * @see https://developer.foursquare.com/docs/explore
 */
abstract class FoursquareProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
  extends OAuth2Provider(httpLayer, stateProvider, settings) {

  /**
   * The content type returned from the provider.
   */
  type Content = JsValue

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  protected val urls = Map("api" -> API)

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    val version = settings.customProperties.getOrElse(APIVersion, DefaultAPIVersion)
    httpLayer.url(urls("api").format(authInfo.accessToken, version)).get().flatMap { response =>
      val json = response.json
      val errorType = (json \ "meta" \ "errorType").asOpt[String]
      (json \ "meta" \ "code").asOpt[Int] match {
        case Some(code) if code != 200 =>
          val errorDetail = (json \ "meta" \ "errorDetail").asOpt[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, code, errorType, errorDetail))
        case _ =>
          // Status code 200 and an existing errorType can only be a deprecated error
          // https://developer.foursquare.com/overview/responses
          if (errorType.isDefined) {
            logger.info("This implementation may be deprecated! Please contact the Silhouette team for a fix!")
          }

          Future.from(parseProfile(parser, json))
      }
    }
  }

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser = (json: JsValue) => {
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
  val ID = "foursquare"
  val API = "https://api.foursquare.com/v2/users/self?oauth_token=%s&v=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new FoursquareProvider(httpLayer, stateProvider, settings) with CommonSocialProfileBuilder
  }
}
