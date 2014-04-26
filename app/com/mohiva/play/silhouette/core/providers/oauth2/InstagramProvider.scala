/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Brian Porter (poornerd at gmail dot com) - twitter: @poornerd
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
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth2Info, OAuth2Settings, OAuth2Provider }
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import InstagramProvider._
import OAuth2Provider._

/**
 * An Instagram OAuth2 provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 * @see http://instagram.com/developer/authentication/
 * @see http://instagram.com/developer/endpoints/
 *
 */
class InstagramProvider(
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth2Settings)
    extends OAuth2Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Instagram

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[SocialProfile[OAuth2Info]] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().map { response =>
      val json = response.json
      (json \ Meta \ Code).asOpt[Int] match {
        case Some(code) if code != 200 =>
          val errorType = (json \ Meta \ ErrorType).asOpt[String]
          val errorMsg = (json \ Meta \ ErrorMsg).asOpt[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, code, errorType, errorMsg))
        case _ =>
          val userID = (json \ Data \ ID).as[String]
          val fullName = (json \ Data \ FullName).asOpt[String]
          val avatarURL = (json \ Data \ ProfilePic).asOpt[String]

          SocialProfile(
            loginInfo = LoginInfo(id, userID),
            authInfo = authInfo,
            fullName = fullName,
            avatarURL = avatarURL)
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
object InstagramProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, type: %s, message: %s"

  /**
   * The Instagram constants.
   */
  val Instagram = "instagram"
  val API = "https://api.instagram.com/v1/users/self?access_token=%s"
  val Meta = "meta"
  val ErrorType = "error_type"
  val ErrorMsg = "error_message"
  val User = "user"
  val Data = "data"
  val ID = "id"
  val FullName = "full_name"
  val ProfilePic = "profile_picture"
}
