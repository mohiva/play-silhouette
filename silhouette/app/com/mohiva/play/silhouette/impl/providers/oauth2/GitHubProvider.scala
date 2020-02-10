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
import com.mohiva.play.silhouette.impl.providers.oauth2.GitHubProvider._
import play.api.http.HeaderNames
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * Base GitHub OAuth2 Provider.
 *
 * @see https://developer.github.com/v3/oauth/
 */
trait BaseGitHubProvider extends OAuth2Provider {

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
   * A list with headers to send to the API.
   *
   * Without defining the accept header, the response will take the following form:
   * access_token=e72e16c7e42f292c6912e7710c838347ae178b4a&scope=user%2Cgist&token_type=bearer
   *
   * @see https://developer.github.com/v3/oauth/#response
   */
  override protected val headers = Seq(HeaderNames.ACCEPT -> "application/json")

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api")).withHttpHeaders(HeaderNames.AUTHORIZATION -> s"Bearer ${authInfo.accessToken}").get()
      .flatMap { response =>
        val json = response.json
        (json \ "message").asOpt[String] match {
          case Some(msg) =>
            val docURL = (json \ "documentation_url").asOpt[String]
            throw new ProfileRetrievalException(SpecifiedProfileError.format(id, msg, docURL))

          case _ => profileParser.parse(json, authInfo)
        }
      }
  }
}

/**
 * The profile parser for the common social profile.
 */
class GitHubProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "id").as[Long]
    val fullName = (json \ "name").asOpt[String]
    val avatarUrl = (json \ "avatar_url").asOpt[String]
    val email = (json \ "email").asOpt[String].filter(!_.isEmpty)

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID.toString),
      fullName = fullName,
      avatarURL = avatarUrl,
      email = email)
  }
}

/**
 * The GitHub OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler  The state provider implementation.
 * @param settings      The provider settings.
 */
class GitHubProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings)
  extends BaseGitHubProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = GitHubProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new GitHubProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new GitHubProvider(httpLayer, stateHandler, f(settings))
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
  val ID = "github"
  val API = "https://api.github.com/user"
}
