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
package com.mohiva.play.silhouette.core.providers.oauth1

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import XingProvider._

/**
 * A Xing OAuth1 Provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param oAuth1Service The OAuth1 service implementation.
 * @param oAuth1Settings The OAuth1 provider settings.
 *
 * @see https://dev.xing.com/docs/get/users/me
 * @see https://dev.xing.com/docs/error_responses
 */
class XingProvider(
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  oAuth1Service: OAuth1Service,
  oAuth1Settings: OAuth1Settings)
    extends OAuth1Provider(cacheLayer, httpLayer, oAuth1Service, oAuth1Settings) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Xing

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth1Info): Future[SocialProfile[OAuth1Info]] = {
    httpLayer.url(API).sign(oAuth1Service.sign(authInfo)).get().map { response =>
      val json = response.json
      (json \ ErrorName).asOpt[String] match {
        case Some(error) =>
          val message = (json \ Message).asOpt[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, error, message.getOrElse("")))
        case _ =>
          val json = response.json
          val userID = (json \ Users \\ ID).head.as[String]
          val fullName = (json \ Users \\ Name).headOption.map(_.as[String])
          val lastName = (json \ Users \\ LastName).headOption.map(_.as[String])
          val firstName = (json \ Users \\ FirstName).headOption.map(_.as[String])
          val avatarURL = (json \ Users \\ ProfileImage).headOption.flatMap(urls => (urls \ Large).asOpt[String])
          val email = (json \ Users \\ ActiveEmail).headOption.map(_.as[String])

          SocialProfile(
            loginInfo = LoginInfo(id, userID),
            authInfo = authInfo,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            avatarURL = avatarURL,
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
object XingProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] error retrieving profile information. Error name: %s, message: %s"

  /**
   * The LinkedIn constants.
   */
  val Xing = "xing"
  val API = "https://api.xing.com/v1/users/me?fields=id,display_name,first_name,last_name,active_email,photo_urls.large"
  val ErrorName = "error_name"
  val Message = "message"
  val Users = "users"
  val ID = "id"
  val Name = "display_name"
  val FirstName = "first_name"
  val LastName = "last_name"
  val ProfileImage = "photo_urls"
  val Large = "large"
  val ActiveEmail = "active_email"
}
