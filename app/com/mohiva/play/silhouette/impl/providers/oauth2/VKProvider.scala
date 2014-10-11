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
package com.mohiva.play.silhouette.impl.providers.oauth2

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.VKProvider._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{ JsObject, JsValue }

import scala.concurrent.Future

/**
 * A Vk OAuth 2 provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 *
 * @see http://vk.com/dev/auth_sites
 * @see http://vk.com/dev/api_requests
 * @see http://vk.com/pages.php?o=-1&p=getProfiles
 */
abstract class VKProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
  extends OAuth2Provider(httpLayer, stateProvider, settings) {

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
  protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ "error_code").as[Int]
          val errorMsg = (error \ "error_msg").as[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorCode, errorMsg))
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
    val response = (json \ "response").apply(0)
    val userId = (response \ "uid").as[Long]
    val firstName = (response \ "first_name").asOpt[String]
    val lastName = (response \ "last_name").asOpt[String]
    val avatarURL = (response \ "photo").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(id, userId.toString),
      firstName = firstName,
      lastName = lastName,
      avatarURL = avatarURL)
  }
}

/**
 * The companion object.
 */
object VKProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The VK constants.
   */
  val ID = "vk"
  val API = "https://api.vk.com/method/getProfiles?fields=uid,first_name,last_name,photo&access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new VKProvider(httpLayer, stateProvider, settings) with CommonSocialProfileBuilder[OAuth2Info]
  }
}
