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
package com.mohiva.play.silhouette.core.providers.oauth2

import scala.concurrent.Future
import play.api.libs.json.{ JsValue, JsObject }
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import GoogleProvider._

/**
 * A Google OAuth2 Provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 *
 * @see https://developers.google.com/+/api/auth-migration#timetable
 * @see https://developers.google.com/+/api/auth-migration#oauth2login
 * @see https://developers.google.com/accounts/docs/OAuth2Login
 * @see https://developers.google.com/+/api/latest/people
 */
abstract class GoogleProvider(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings)
    extends OAuth2Provider(cacheLayer, httpLayer, settings) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Google

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
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ "code").as[Int]
          val errorMsg = (error \ "message").as[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, errorCode, errorMsg))
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
      loginInfo = LoginInfo(id, userID),
      authInfo = authInfo,
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = avatarURL,
      email = emailValue)
  }
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
  val Google = "google"
  val API = "https://www.googleapis.com/plus/v1/people/me?access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param cacheLayer The cache layer implementation.
   * @param httpLayer The HTTP layer implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings) = {
    new GoogleProvider(cacheLayer, httpLayer, settings) with CommonSocialProfileBuilder[OAuth2Info]
  }
}
