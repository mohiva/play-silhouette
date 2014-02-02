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

import play.api.libs.ws.Response
import play.api.libs.json.JsObject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth2Info, OAuth2Settings, OAuth2Provider }
import com.mohiva.play.silhouette.core.services.AuthInfoService
import FacebookProvider._
import OAuth2Provider._

/**
 * A Facebook OAuth2 Provider.
 *
 * @param authInfoService The auth info service.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 */
class FacebookProvider(
  protected val authInfoService: AuthInfoService,
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth2Settings)
    extends OAuth2Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Facebook

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return The social profile.
   */
  def buildProfile(authInfo: OAuth2Info): Future[SocialProfile] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().map { response =>
      val json = response.json
      (json \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val errorType = (error \ Type).as[String]
          val errorMsg = (error \ Message).as[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, errorType, errorMsg))
        case _ =>
          val userID = (json \ ID).as[String]
          val firstName = (json \ FirstName).asOpt[String]
          val lastName = (json \ LastName).asOpt[String]
          val fullName = (json \ Name).asOpt[String]
          val avatarURL = (json \ Picture \ Data \ URL).asOpt[String]
          val email = (json \ Email).asOpt[String]

          SocialProfile(
            loginInfo = LoginInfo(id, userID),
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            avatarURL = avatarURL,
            email = email)
      }
    }.recover { case e => throw new AuthenticationException(UnspecifiedProfileError.format(id), e) }
  }

  /**
   * Builds the OAuth2 info.
   *
   * Facebook does not follow the OAuth2 spec :-\
   *
   * @param response The response from the provider.
   * @return The OAuth2 info.
   */
  override protected def buildInfo(response: Response): OAuth2Info = {
    response.body.split("&|=") match {
      case Array(AccessToken, token, Expires, expiresIn) => OAuth2Info(token, None, Some(expiresIn.toInt))
      case Array(AccessToken, token) => OAuth2Info(token)
      case _ => throw new AuthenticationException(InvalidResponseFormat.format(id))
    }
  }
}

/**
 * The companion object.
 */
object FacebookProvider {

  /**
   * The error messages.
   */
  val InvalidResponseFormat = "[Silhouette][%s] Invalid response format for accessToken"
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error type: %s, message: %s"

  /**
   * The Facebook constants.
   */
  val Facebook = "facebook"
  val API = "https://graph.facebook.com/me?fields=name,first_name,last_name,picture,email&return_ssl_resources=1&access_token=%s"
  val Type = "type"
  val Message = "message"
  val ID = "id"
  val FirstName = "first_name"
  val LastName = "last_name"
  val Name = "name"
  val Picture = "picture"
  val Email = "email"
  val Data = "data"
  val URL = "url"
}
