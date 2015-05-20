/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * Base Twitter OAuth1 Provider.
 *
 * @see https://dev.twitter.com/docs/user-profile-images-and-banners
 * @see https://dev.twitter.com/docs/entities#users
 */
trait BaseTwitterProvider extends OAuth1Provider {

  /**
   * The content type to parse a profile from.
   */
  type Content = JsValue

  /**
   * The provider ID.
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
      (json \ "errors" \\ "code").headOption.map(_.as[Int]) match {
        case Some(code) =>
          val message = (json \ "errors" \\ "message").headOption.map(_.as[String])

          Future.failed(new ProfileRetrievalException(SpecifiedProfileError.format(id, code, message)))
        case _ => profileParser.parse(json)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class TwitterProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  def parse(json: JsValue) = Future.successful {
    val userID = (json \ "id").as[Long]
    val fullName = (json \ "name").asOpt[String]
    val avatarURL = (json \ "profile_image_url_https").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID.toString),
      fullName = fullName,
      avatarURL = avatarURL)
  }
}

/**
 * The Twitter OAuth1 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param service The OAuth1 service implementation.
 * @param tokenSecretProvider The OAuth1 token secret provider implementation.
 * @param settings The OAuth1 provider settings.
 */
class TwitterProvider(
  protected val httpLayer: HTTPLayer,
  protected val service: OAuth1Service,
  protected val tokenSecretProvider: OAuth1TokenSecretProvider,
  val settings: OAuth1Settings)
  extends BaseTwitterProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  type Self = TwitterProvider

  /**
   * The profile parser implementation.
   */
  val profileParser = new TwitterProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  def withSettings(f: (Settings) => Settings) = {
    new TwitterProvider(httpLayer, service, tokenSecretProvider, f(settings))
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
   * The Twitter constants.
   */
  val ID = "twitter"
  val API = "https://api.twitter.com/1.1/account/verify_credentials.json"
}
