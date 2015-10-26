/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
import com.mohiva.play.silhouette.impl.providers.oauth2.DropboxProvider._
import play.api.http.HeaderNames._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * Base Dropbox OAuth2 Provider.
 *
 * @see https://www.dropbox.com/developers/blog/45/using-oauth-20-with-the-core-api
 * @see https://www.dropbox.com/developers/core/docs#oauth2-methods
 */
trait BaseDropboxProvider extends OAuth2Provider {

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
  override protected val urls = Map("api" -> API)

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api")).withHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}").get().flatMap { response =>
      val json = response.json
      response.status match {
        case 200 => profileParser.parse(json, authInfo)
        case status =>
          val error = (json \ "error").as[String]
          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, error, status))
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class DropboxProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "uid").as[Long]
    val firstName = (json \ "name_details" \ "given_name").asOpt[String]
    val lastName = (json \ "name_details" \ "surname").asOpt[String]
    val fullName = (json \ "display_name").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID.toString),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName)
  }
}

/**
 * The Dropbox OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 */
class DropboxProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateProvider: OAuth2StateProvider,
  val settings: OAuth2Settings)
  extends BaseDropboxProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = DropboxProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new DropboxProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new DropboxProvider(httpLayer, stateProvider, f(settings))
}

/**
 * The companion object.
 */
object DropboxProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s, status code: %s"

  /**
   * The Dropbox constants.
   */
  val ID = "dropbox"
  val API = "https://api.dropbox.com/1/account/info"
}
