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
package com.mohiva.play.silhouette.impl.providers.oauth1

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{ CacheLayer, HTTPLayer }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1.LinkedInProvider._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * A LinkedIn OAuth1 Provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param service The OAuth1 service implementation.
 * @param settings The OAuth1 provider settings.
 *
 * @see https://developer.linkedin.com/documents/oauth-10a
 * @see https://developer.linkedin.com/documents/authentication
 * @see https://developer.linkedin.com/documents/inapiprofile
 */
abstract class LinkedInProvider(
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  service: OAuth1Service,
  settings: OAuth1Settings) extends OAuth1Provider(cacheLayer, httpLayer, service, settings) {

  /**
   * The content type returned from the provider.
   */
  type Content = JsValue

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  protected val urls = Map("api" -> API)

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth1Info): Future[Profile] = {
    httpLayer.url(urls("api")).sign(service.sign(authInfo)).get().flatMap { response =>
      val json = response.json
      (json \ "errorCode").asOpt[Int] match {
        case Some(error) =>
          val message = (json \ "message").asOpt[String]
          val requestId = (json \ "requestId").asOpt[String]
          val status = (json \ "status").asOpt[Int]
          val timestamp = (json \ "timestamp").asOpt[Long]

          Future.failed(new ProfileRetrievalException(SpecifiedProfileError.format(id, error, message, requestId, status, timestamp)))
        case _ => Future.fromTry(parseProfile(parser, json))
      }
    }
  }

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser = (json: JsValue) => {
    val userID = (json \ "id").as[String]
    val firstName = (json \ "firstName").asOpt[String]
    val lastName = (json \ "lastName").asOpt[String]
    val fullName = (json \ "formattedName").asOpt[String]
    val avatarURL = (json \ "pictureUrl").asOpt[String]
    val email = (json \ "emailAddress").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(id, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = avatarURL,
      email = email)
  }
}

/**
 * The companion object.
 */
object LinkedInProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] error retrieving profile information. Error code: %s, message: %s, requestId: %s, status: %s, timestamp: %s"

  /**
   * The LinkedIn constants.
   */
  val ID = "linkedin"
  val API = "https://api.linkedin.com/v1/people/~:(id,first-name,last-name,formatted-name,picture-url,email-address)?format=json"

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
    new LinkedInProvider(cacheLayer, httpLayer, oAuth1Service, auth1Settings) with CommonSocialProfileBuilder
  }
}
