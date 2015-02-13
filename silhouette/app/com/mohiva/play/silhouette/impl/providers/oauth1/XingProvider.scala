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
package com.mohiva.play.silhouette.impl.providers.oauth1

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1.XingProvider._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{ JsObject, JsValue }

import scala.concurrent.Future

/**
 * A Xing OAuth1 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param service The OAuth1 service implementation.
 * @param tokenSecretProvider The OAuth1 token secret provider implementation.
 * @param settings The OAuth1 provider settings.
 *
 * @see https://dev.xing.com/docs/get/users/me
 * @see https://dev.xing.com/docs/error_responses
 */
abstract class XingProvider(
  httpLayer: HTTPLayer,
  service: OAuth1Service,
  tokenSecretProvider: OAuth1TokenSecretProvider,
  settings: OAuth1Settings)
  extends OAuth1Provider(httpLayer, service, tokenSecretProvider, settings) {

  /**
   * The content type to parse a profile from.
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
      (json \ "error_name").asOpt[String] match {
        case Some(error) =>
          val message = (json \ "message").asOpt[String]

          Future.failed(new ProfileRetrievalException(SpecifiedProfileError.format(id, error, message.getOrElse(""))))
        case _ => profileParser.parse(json)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class XingProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  def parse(json: JsValue) = Future.successful {
    val users = (json \ "users").as[Seq[JsObject]].head
    val userID = (users \ "id").as[String]
    val firstName = (users \ "first_name").asOpt[String]
    val lastName = (users \ "last_name").asOpt[String]
    val fullName = (users \ "display_name").asOpt[String]
    val avatarURL = (users \ "photo_urls" \ "large").asOpt[String]
    val email = (users \ "active_email").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = avatarURL,
      email = email)
  }
}

/**
 * The profile builder for the common social profile.
 */
trait XingProfileBuilder extends CommonSocialProfileBuilder {
  self: XingProvider =>

  /**
   * The profile parser implementation.
   */
  val profileParser = new XingProfileParser
}

/**
 * The companion object.
 */
object XingProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] error retrieving profile information. Error name: %s, message: %s"

  /**
   * The LinkedIn constants.
   */
  val ID = "xing"
  val API = "https://api.xing.com/v1/users/me?fields=id,first_name,last_name,display_name,photo_urls.large,active_email"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param service The OAuth1 service implementation.
   * @param tokenSecretProvider The OAuth1 token secret provider implementation.
   * @param settings The OAuth1 provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, service: OAuth1Service, tokenSecretProvider: OAuth1TokenSecretProvider, settings: OAuth1Settings) = {
    new XingProvider(httpLayer, service, tokenSecretProvider, settings) with XingProfileBuilder
  }
}
