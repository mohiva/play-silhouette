/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Brian Porter (poornerd at gmail dot com) - twitter: @poornerd
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.InstagramProvider._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * Base Instagram OAuth2 provider.
 *
 * @see http://instagram.com/developer/authentication/
 * @see http://instagram.com/developer/endpoints/
 */
trait BaseInstagramProvider extends OAuth2Provider {

  /**
   * The content type to parse a profile from.
   */
  override type Content = JsValue

  /**
   * The provider ID.
   */
  override val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  override protected val urls = Map("api" -> settings.apiURL.getOrElse(API))

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "meta" \ "code").asOpt[Int] match {
        case Some(code) if code != 200 =>
          val errorType = (json \ "meta" \ "error_type").asOpt[String]
          val errorMsg = (json \ "meta" \ "error_message").asOpt[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, code, errorType, errorMsg))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class InstagramProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val data = json \ "data"
    val userID = (data \ "id").as[String]
    val fullName = (data \ "full_name").asOpt[String]
    val avatarURL = (data \ "profile_picture").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      fullName = fullName,
      avatarURL = avatarURL)
  }
}

/**
 * The Instagram OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler  The state provider implementation.
 * @param settings      The provider settings.
 */
class InstagramProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings)
  extends BaseInstagramProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = InstagramProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new InstagramProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new InstagramProvider(httpLayer, stateHandler, f(settings))
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
  val ID = "instagram"
  val API = "https://api.instagram.com/v1/users/self?access_token=%s"
}
