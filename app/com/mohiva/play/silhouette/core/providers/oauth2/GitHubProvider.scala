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
import play.api.libs.json.JsValue
import play.api.http.HeaderNames
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.exceptions.ProfileRetrievalException
import GitHubProvider._

/**
 * A GitHub OAuth2 Provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 *
 * @see https://developer.github.com/v3/oauth/
 */
abstract class GitHubProvider(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings)
    extends OAuth2Provider(cacheLayer, httpLayer, settings) {

  /**
   * A list with headers to send to the API.
   *
   * Without defining the accept header, the response will take the following form:
   * access_token=e72e16c7e42f292c6912e7710c838347ae178b4a&scope=user%2Cgist&token_type=bearer
   *
   * @see https://developer.github.com/v3/oauth/#response
   */
  override protected val headers = Seq(HeaderNames.ACCEPT -> "application/json")

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = GitHub

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
      (json \ "message").asOpt[String] match {
        case Some(msg) =>
          val docURL = (json \ "documentation_url").asOpt[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, msg, docURL))
        case _ => parseProfile(parser, json).asFuture
      }
    }
  }

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser = (json: JsValue) => {
    val userID = (json \ "id").as[Long]
    val fullName = (json \ "name").asOpt[String]
    val avatarUrl = (json \ "avatar_url").asOpt[String]
    val email = (json \ "email").asOpt[String].filter(!_.isEmpty)

    CommonSocialProfile(
      loginInfo = LoginInfo(id, userID.toString),
      fullName = fullName,
      avatarURL = avatarUrl,
      email = email)
  }
}

/**
 * The companion object.
 */
object GitHubProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s, doc URL: %s"

  /**
   * The GitHub constants.
   */
  val GitHub = "github"
  val API = "https://api.github.com/user?access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param cacheLayer The cache layer implementation.
   * @param httpLayer The HTTP layer implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(cacheLayer: CacheLayer, httpLayer: HTTPLayer, settings: OAuth2Settings) = {
    new GitHubProvider(cacheLayer, httpLayer, settings) with CommonSocialProfileBuilder[OAuth2Info]
  }
}
