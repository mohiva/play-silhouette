/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Brian Porter (poornerd at gmail dot com) - twitter: @poornerd
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
import com.mohiva.play.silhouette.impl.providers.oauth1.XingProvider._
import play.api.libs.json.{ JsObject, JsValue }

import scala.concurrent.Future

/**
 * Base Xing OAuth1 Provider.
 *
 * @see https://dev.xing.com/docs/get/users/me
 * @see https://dev.xing.com/docs/error_responses
 */
trait BaseXingProvider extends OAuth1Provider {

  /**
   * The content type to parse a profile from.
   */
  override type Content = JsValue

  /**
   * The provider ID.
   */
  override val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  override protected val urls = Map("api" -> settings.apiURL.getOrElse(API))

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth1Info): Future[Profile] = {
    httpLayer.url(urls("api")).sign(service.sign(authInfo)).get().flatMap { response =>
      val json = response.json
      (json \ "error_name").asOpt[String] match {
        case Some(error) =>
          val message = (json \ "message").asOpt[String]

          Future.failed(new ProfileRetrievalException(SpecifiedProfileError.format(id, error, message.getOrElse(""))))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class XingProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth1Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth1Info) = Future.successful {
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
 * The Xing OAuth1 Provider.
 *
 * @param httpLayer           The HTTP layer implementation.
 * @param service             The OAuth1 service implementation.
 * @param tokenSecretProvider The OAuth1 token secret provider implementation.
 * @param settings            The OAuth1 provider settings.
 */
class XingProvider(
  protected val httpLayer: HTTPLayer,
  val service: OAuth1Service,
  protected val tokenSecretProvider: OAuth1TokenSecretProvider,
  val settings: OAuth1Settings)
  extends BaseXingProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = XingProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new XingProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = {
    new XingProvider(httpLayer, service.withSettings(f), tokenSecretProvider, f(settings))
  }
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
   * The Xing constants.
   */
  val ID = "xing"
  val API = "https://api.xing.com/v1/users/me?fields=id,first_name,last_name,display_name,photo_urls.large,active_email"
}
