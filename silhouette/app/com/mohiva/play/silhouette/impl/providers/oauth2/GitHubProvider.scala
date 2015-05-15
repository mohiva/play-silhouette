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
import scala.concurrent.ExecutionContext

/**
 * A GitHub OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 *
 * @see https://developer.github.com/v3/oauth/
 */
abstract class GitHubProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
  extends OAuth2Provider(httpLayer, stateProvider, settings) {

  /**
   * The content type to parse a profile from.
   */
  type Content = JsValue

  /**
   * The provider ID.
   */
  val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  protected val urls = Map("api" -> API)

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
  protected def buildProfile(authInfo: OAuth2Info)(implicit ec: ExecutionContext): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "message").asOpt[String] match {
        case Some(msg) =>
          val docURL = (json \ "documentation_url").asOpt[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, msg, docURL))
        case _ => profileParser.parse(json)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class GitHubProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  def parse(json: JsValue)(implicit ec: ExecutionContext) = Future.successful {
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
 * The profile builder for the common social profile.
 */
trait GitHubProfileBuilder extends CommonSocialProfileBuilder {
  self: GitHubProvider =>

  /**
   * The profile parser implementation.
   */
  val profileParser = new GitHubProfileParser
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
  val API = "https://api.github.com/user?access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new GitHubProvider(httpLayer, stateProvider, settings) with GitHubProfileBuilder
  }
}
