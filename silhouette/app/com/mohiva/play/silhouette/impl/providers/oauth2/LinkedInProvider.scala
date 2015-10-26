/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Greg Methvin (greg at methvin dot net)
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
import com.mohiva.play.silhouette.impl.providers.oauth2.LinkedInProvider._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * Base LinkedIn OAuth2 Provider.
 *
 * @see https://developer.linkedin.com/documents/oauth-10a
 * @see https://developer.linkedin.com/documents/authentication
 * @see https://developer.linkedin.com/documents/inapiprofile
 */
trait BaseLinkedInProvider extends OAuth2Provider {

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
      (json \ "errorCode").asOpt[Int] match {
        case Some(error) =>
          val message = (json \ "message").asOpt[String]
          val requestId = (json \ "requestId").asOpt[String]
          val status = (json \ "status").asOpt[Int]
          val timestamp = (json \ "timestamp").asOpt[Long]

          Future.failed(new ProfileRetrievalException(SpecifiedProfileError.format(id, error, message, requestId, status, timestamp)))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class LinkedInProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "id").as[String]
    val firstName = (json \ "firstName").asOpt[String]
    val lastName = (json \ "lastName").asOpt[String]
    val fullName = (json \ "formattedName").asOpt[String]
    val avatarURL = (json \ "pictureUrl").asOpt[String]
    val email = (json \ "emailAddress").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = avatarURL,
      email = email)
  }
}

/**
 * The LinkedIn OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 */
class LinkedInProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateProvider: OAuth2StateProvider,
  val settings: OAuth2Settings)
  extends BaseLinkedInProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = LinkedInProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new LinkedInProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new LinkedInProvider(httpLayer, stateProvider, f(settings))
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
}
