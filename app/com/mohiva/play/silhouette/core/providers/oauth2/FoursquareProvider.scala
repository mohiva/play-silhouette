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
import com.mohiva.play.silhouette.core.utils.{HTTPLayer, CacheLayer}
import com.mohiva.play.silhouette.core.providers.{SocialProfile, OAuth2Info, OAuth2Settings, OAuth2Provider}
import com.mohiva.play.silhouette.core.services.AuthInfoService
import FoursquareProvider._

/**
 * A Foursquare OAuth2 provider.
 *
 * @param authInfoService The auth info service.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 */
class FoursquareProvider(
    val authInfoService: AuthInfoService,
    cacheLayer: CacheLayer,
    httpLayer: HTTPLayer,
    settings: OAuth2Settings)
  extends OAuth2Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Foursquare

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return The social profile.
   */
  def buildProfile(authInfo: OAuth2Info): Future[SocialProfile] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().map { response =>
      val json = response.json
      (json \ Response \ User).asOpt[String] match {
        case Some(msg) => throw new AuthenticationException(SpecifiedProfileError.format(msg))
        case _ =>
          val userID = ( json \ Response \ User \ ID).asOpt[String]
          val lastName = (json \ Response \ User \ LastName).asOpt[String]
          val firstName = (json \ Response \ User \ FirstName).asOpt[String]
          val avatarURLPart1  = (json \ Response \ User \ AvatarURL \ Prefix).asOpt[String]
          val avatarURLPart2 = (json \ Response \ User \ AvatarURL \ Suffix).asOpt[String]
          val email = (json \ Response \ User \ Contact \ Email).asOpt[String].filter( !_.isEmpty )

          SocialProfile(
            loginInfo = LoginInfo(id, userID.get),
            firstName = firstName,
            lastName = lastName,
            avatarURL = for (prefix <- avatarURLPart1; postfix <- avatarURLPart2) yield prefix + "100x100" + postfix,
            email = email)
      }
    }.recover { case e => throw new AuthenticationException(UnspecifiedProfileError.format(id), e) }
  }
}

/**
 * The companion object.
 */
object FoursquareProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s"

  /**
   * The Foursquare constants.
   */
  val Foursquare = "foursquare"
  val API = "https://api.foursquare.com/v2/users/self?oauth_token=%s"
  val ID = "id"
  val Response = "response"
  val User = "user"
  val Contact = "contact"
  val LastName = "lastName"
  val FirstName = "firstName"
  val AvatarURL = "photo"
  val Email = "email"
  val Prefix = "prefix"
  val Suffix = "suffix"
}
