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
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.core.utils.HTTPLayer
import LinkedInProvider._

/**
 * A LinkedIn OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 *
 * @see https://developer.linkedin.com/documents/oauth-10a
 * @see https://developer.linkedin.com/documents/authentication
 * @see https://developer.linkedin.com/documents/inapiprofile
 */
abstract class LinkedInProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
    extends OAuth2Provider(httpLayer, stateProvider, settings) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = ID

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
    httpLayer.url(profileAPI.format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "errorCode").asOpt[Int] match {
        case Some(error) =>
          val message = (json \ "message").asOpt[String]
          val requestId = (json \ "requestId").asOpt[String]
          val status = (json \ "status").asOpt[Int]
          val timestamp = (json \ "timestamp").asOpt[Long]

          Future.failed(new ProfileRetrievalException(SpecifiedProfileError.format(id, error, message, requestId, status, timestamp)))
        case _ => Future.fromTry(parseProfile(parser, json))
      }
    }
  }

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser = (json: JsValue) => {
    val userID = (json \ "id").as[String]
    val firstName = (json \ "firstName").asOpt[String]
    val lastName = (json \ "lastName").asOpt[String]
    val fullName = (json \ "formattedName").asOpt[String]
    val avatarURL = (json \ "pictureUrl").asOpt[String]
    val email = (json \ "emailAddress").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(id, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = avatarURL,
      email = email)
  }
}

/**
 * The companion object.
 */
object LinkedInProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] error retrieving profile information. Error code: %s, message: %s, requestId: %s, status: %s, timestamp: %s"

  /**
   * The LinkedIn constants.
   */
  val ID = "linkedin"
  val API = "https://api.linkedin.com/v1/people/~:(id,first-name,last-name,formatted-name,picture-url,email-address)?format=json&oauth2_access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new LinkedInProvider(httpLayer, stateProvider, settings) with CommonSocialProfileBuilder[OAuth2Info]
  }
}
