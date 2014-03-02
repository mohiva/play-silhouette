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

import play.api.http.HeaderNames
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth2Info, OAuth2Settings, OAuth2Provider }
import com.mohiva.play.silhouette.core.services.AuthInfoService
import GitHubProvider._

/**
 * A GitHub OAuth2 Provider.
 *
 * @param authInfoService The auth info service.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 * @see https://developer.github.com/v3/oauth/
 */
class GitHubProvider(
  protected val authInfoService: AuthInfoService,
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth2Settings)
    extends OAuth2Provider(settings, cacheLayer, httpLayer) {

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
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return The social profile.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[SocialProfile] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().map { response =>
      val json = response.json
      (json \ Message).asOpt[String] match {
        case Some(msg) =>
          val docURL = (json \ DocURL).asOpt[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, msg, docURL))
        case _ =>
          val userID = (json \ ID).as[Int]
          val fullName = (json \ Name).asOpt[String]
          val avatarUrl = (json \ AvatarURL).asOpt[String]
          val email = (json \ Email).asOpt[String].filter(!_.isEmpty)

          SocialProfile(
            loginInfo = LoginInfo(id, userID.toString),
            fullName = fullName,
            avatarURL = avatarUrl,
            email = email)
      }
    }.recover {
      case e if !e.isInstanceOf[AuthenticationException] =>
        throw new AuthenticationException(UnspecifiedProfileError.format(id), e)
    }
  }
}

/**
 * The companion object.
 */
object GitHubProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s, doc URL: %s"

  /**
   * The Foursquare constants.
   */
  val GitHub = "github"
  val API = "https://api.github.com/user?access_token=%s"
  val Message = "message"
  val DocURL = "documentation_url"
  val ID = "id"
  val Name = "name"
  val AvatarURL = "avatar_url"
  val Email = "email"
}
