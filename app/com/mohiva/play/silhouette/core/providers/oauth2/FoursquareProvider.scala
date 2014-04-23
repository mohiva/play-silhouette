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
import FoursquareProvider._
import OAuth2Provider._

/**
 * A Foursquare OAuth2 provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 * @see https://developer.foursquare.com/overview/auth
 * @see https://developer.foursquare.com/overview/responses
 * @see https://developer.foursquare.com/docs/explore
 */
class FoursquareProvider(
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
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[SocialProfile[OAuth2Info]] = {
    val version = settings.customProperties.getOrElse(APIVersion, DefaultAPIVersion)
    httpLayer.url(API.format(authInfo.accessToken, version)).get().map { response =>
      val json = response.json
      val errorType = (json \ Meta \ ErrorType).asOpt[String]
      (json \ Meta \ Code).asOpt[Int] match {
        case Some(code) if code != 200 =>
          val errorDetail = (json \ Meta \ ErrorDetail).asOpt[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, code, errorType, errorDetail))
        case _ =>
          // Status code 200 and an existing errorType can only be a deprecated error
          // https://developer.foursquare.com/overview/responses
          if (errorType.isDefined) {
            logger.info("This implementation may be deprecated! Please contact the Silhouette team for a fix!")
          }

          val userID = (json \ Response \ User \ ID).as[String]
          val lastName = (json \ Response \ User \ LastName).asOpt[String]
          val firstName = (json \ Response \ User \ FirstName).asOpt[String]
          val avatarURLPart1 = (json \ Response \ User \ Photo \ Prefix).asOpt[String]
          val avatarURLPart2 = (json \ Response \ User \ Photo \ Suffix).asOpt[String]
          val email = (json \ Response \ User \ Contact \ Email).asOpt[String].filter(!_.isEmpty)
          val resolution = settings.customProperties.getOrElse(AvatarResolution, DefaultAvatarResolution)

          SocialProfile(
            loginInfo = LoginInfo(id, userID),
            authInfo = authInfo,
            firstName = firstName,
            lastName = lastName,
            avatarURL = for (prefix <- avatarURLPart1; postfix <- avatarURLPart2) yield prefix + resolution + postfix,
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
object FoursquareProvider {

  /**
   * The version of this implementation.
   *
   * @see https://developer.foursquare.com/overview/versioning
   */
  val DefaultAPIVersion = "20140206"

  /**
   * The default avatar resolution.
   */
  val DefaultAvatarResolution = "100x100"

  /**
   * Some custom properties for this provider.
   */
  val APIVersion = "api.version"
  val AvatarResolution = "avatar.resolution"

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, type: %s, detail: %s"

  /**
   * The Foursquare constants.
   */
  val Foursquare = "foursquare"
  val API = "https://api.foursquare.com/v2/users/self?oauth_token=%s&v=%s"
  val ID = "id"
  val Meta = "meta"
  val ErrorType = "errorType"
  val ErrorDetail = "errorDetail"
  val Deprecated = "deprecated"
  val Response = "response"
  val User = "user"
  val FirstName = "firstName"
  val LastName = "lastName"
  val Photo = "photo"
  val Prefix = "prefix"
  val Suffix = "suffix"
  val Contact = "contact"
  val Email = "email"
}
