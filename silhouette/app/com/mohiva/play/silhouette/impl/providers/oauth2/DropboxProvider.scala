/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * A Dropbox OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 *
 * @see https://www.dropbox.com/developers/blog/45/using-oauth-20-with-the-core-api
 * @see https://www.dropbox.com/developers/core/docs#oauth2-methods
 */
abstract class DropboxProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
  extends OAuth2Provider(httpLayer, stateProvider, settings) {

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
  protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api")).withHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}").get().flatMap { response =>
      val json = response.json
      response.status match {
        case 200 => profileParser.parse(json)
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
class DropboxProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  def parse(json: JsValue) = Future.successful {
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
 * The profile builder for the common social profile.
 */
trait DropboxProfileBuilder extends CommonSocialProfileBuilder {
  self: DropboxProvider =>

  /**
   * The profile parser implementation.
   */
  val profileParser = new DropboxProfileParser
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

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new DropboxProvider(httpLayer, stateProvider, settings) with DropboxProfileBuilder
  }
}
