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
package com.mohiva.play.silhouette.core.providers.oauth1

import play.api.libs.oauth.{ RequestToken, OAuthCalculator }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth1Info, OAuth1Settings, OAuth1Provider }
import com.mohiva.play.silhouette.core.services.AuthInfoService
import LinkedInProvider._

/**
 * A LinkedIn OAuth1 Provider.
 *
 * @param authInfoService The auth info service.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 */
class LinkedInProvider(
  val authInfoService: AuthInfoService,
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth1Settings)
    extends OAuth1Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = LinkedIn

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return The social profile.
   */
  def buildProfile(authInfo: OAuth1Info): Future[SocialProfile] = {
    val sign = OAuthCalculator(serviceInfo.key, RequestToken(authInfo.token, authInfo.secret))
    httpLayer.url(API).sign(sign).get().map { response =>
      val json = response.json
      (json \ ErrorCode).asOpt[Int] match {
        case Some(error) =>
          val message = (json \ Message).asOpt[String]
          val requestId = (json \ RequestId).asOpt[String]
          val timestamp = (json \ Timestamp).asOpt[String]

          throw new AuthenticationException(SpecifiedProfileError.format(error, message, requestId, timestamp))
        case _ =>
          val userID = (json \ ID).as[String]
          val firstName = (json \ FirstName).asOpt[String]
          val lastName = (json \ LastName).asOpt[String]
          val fullName = (json \ FormattedName).asOpt[String]
          val avatarURL = (json \ PictureUrl).asOpt[String]
          val email = (json \ EmailAddress).asOpt[String]

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
}

/**
 * The companion object.
 */
object LinkedInProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] error retrieving profile information. Error code: %s, requestId: %s, message: %s, timestamp: %s"

  /**
   * The LinkedIn constants.
   */
  val LinkedIn = "linkedin"
  val API = "https://api.linkedin.com/v1/people/~:(id,first-name,last-name,formatted-name,picture-url,email-address)?format=json"
  val ErrorCode = "errorCode"
  val Message = "message"
  val RequestId = "requestId"
  val Timestamp = "timestamp"
  val ID = "id"
  val FirstName = "firstName"
  val LastName = "lastName"
  val FormattedName = "formattedName"
  val PictureUrl = "pictureUrl"
  val EmailAddress = "emailAddress"
}
