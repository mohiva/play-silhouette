/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
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
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider._
import play.api.libs.json.{ JsObject, JsValue }

import scala.concurrent.Future

/**
 * Base Google OAuth2 Provider.
 *
 * @see https://developers.google.com/+/api/auth-migration#timetable
 * @see https://developers.google.com/+/api/auth-migration#oauth2login
 * @see https://developers.google.com/accounts/docs/OAuth2Login
 * @see https://developers.google.com/+/api/latest/people
 */
trait BaseGoogleProvider extends OAuth2Provider {

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
  override protected val urls = Map("api" -> API)

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ "code").as[Int]
          val errorMsg = (error \ "message").as[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorCode, errorMsg))
        case _ => profileParser.parse(json)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class GoogleProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue) = Future.successful {
    val userID = (json \ "id").as[String]
    val firstName = (json \ "name" \ "givenName").asOpt[String]
    val lastName = (json \ "name" \ "familyName").asOpt[String]
    val fullName = (json \ "displayName").asOpt[String]
    val avatarURL = (json \ "image" \ "url").asOpt[String]

    // https://developers.google.com/+/api/latest/people#emails.type
    val emailIndex = (json \ "emails" \\ "type").indexWhere(_.as[String] == "account")
    val emailValue = if ((json \ "emails" \\ "value").isDefinedAt(emailIndex)) {
      (json \ "emails" \\ "value")(emailIndex).asOpt[String]
    } else {
      None
    }

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = avatarURL,
      email = emailValue)
  }
}

/**
 * The Google OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 */
class GoogleProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateProvider: OAuth2StateProvider,
  val settings: OAuth2Settings)
  extends BaseGoogleProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  type Self = GoogleProvider

  /**
   * The profile parser implementation.
   */
  val profileParser = new GoogleProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  def withSettings(f: (Settings) => Settings) = new GoogleProvider(httpLayer, stateProvider, f(settings))
}

/**
 * The companion object.
 */
object GoogleProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Google constants.
   */
  val ID = "google"
  val API = "https://www.googleapis.com/plus/v1/people/me?access_token=%s"
}
