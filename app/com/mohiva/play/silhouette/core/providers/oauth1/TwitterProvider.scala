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

import play.api.libs.json.JsValue
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import TwitterProvider._

/**
 * A Twitter OAuth1 Provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param oAuth1Service The OAuth1 service implementation.
 * @param oAuth1Settings The OAuth1 provider settings.
 *
 * @see https://dev.twitter.com/docs/user-profile-images-and-banners
 * @see https://dev.twitter.com/docs/entities#users
 */
abstract class TwitterProvider(
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
  def id = Twitter

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
  protected def buildProfile(authInfo: OAuth1Info): Future[Profile] = {
    httpLayer.url(profileAPI).sign(oAuth1Service.sign(authInfo)).get().flatMap { response =>
      val json = response.json
      (json \ "errors" \\ "code").headOption.map(_.as[Int]) match {
        case Some(code) =>
          val message = (json \ "errors" \\ "message").headOption.map(_.as[String])

          Future.failed(new AuthenticationException(SpecifiedProfileError.format(id, code, message)))
        case _ => parseProfile(parser(authInfo), json).asFuture
      }
    }
  }

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser = (authInfo: OAuth1Info) => (json: JsValue) => {
    val userID = (json \ "id").as[Long]
    val fullName = (json \ "name").asOpt[String]
    val avatarURL = (json \ "profile_image_url_https").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(id, userID.toString),
      authInfo = authInfo,
      fullName = fullName,
      avatarURL = avatarURL)
  }
}

/**
 * The companion object.
 */
object TwitterProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] error retrieving profile information. Error code: %s, message: %s"

  /**
   * The LinkedIn constants.
   */
  val Twitter = "twitter"
  val API = "https://api.twitter.com/1.1/account/verify_credentials.json"

  /**
   * Creates an instance of the provider.
   *
   * @param cacheLayer The cache layer implementation.
   * @param httpLayer The HTTP layer implementation.
   * @param oAuth1Service The OAuth1 service implementation.
   * @param auth1Settings The OAuth1 provider settings.
   * @return An instance of this provider.
   */
  def apply(cacheLayer: CacheLayer,
    httpLayer: HTTPLayer,
    oAuth1Service: OAuth1Service,
    auth1Settings: OAuth1Settings) = {
    new TwitterProvider(cacheLayer, httpLayer, oAuth1Service, auth1Settings) with CommonSocialProfileBuilder[OAuth1Info]
  }
}
