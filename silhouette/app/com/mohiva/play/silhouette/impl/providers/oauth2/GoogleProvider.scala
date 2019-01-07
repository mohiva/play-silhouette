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
package com.mohiva.play.silhouette.impl.providers.oauth2

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider._
import play.api.libs.json.{ JsObject, JsValue }

import scala.concurrent.Future

/**
 * Base Google OAuth2 Provider.
 *
 * @see https://developers.google.com/people/api/rest/v1/people/get
 * @see https://developers.google.com/people/v1/how-tos/authorizing
 * @see https://developers.google.com/identity/protocols/OAuth2
 */
trait BaseGoogleProvider extends OAuth2Provider {

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
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ "code").as[Int]
          val errorMsg = (error \ "message").as[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorCode, errorMsg))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class GoogleProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @see https://developers.google.com/people/api/rest/v1/people#Person.Name
   * @see https://developers.google.com/people/api/rest/v1/people#Person.EmailAddress
   * @see https://developers.google.com/people/api/rest/v1/people#Person.Photo
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = primaryValue(json, "names", "id").get
    val fullName = primaryValue(json, "names", "displayName")
    val firstName = primaryValue(json, "names", "givenName")
    val lastName = primaryValue(json, "names", "familyName")
    val email = primaryValue(json, "emailAddresses", "value")
    val avatarURL = primaryValueWithDefault(json, "photos", "url", "default")

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      firstName = firstName,
      lastName = lastName,
      fullName = fullName,
      avatarURL = avatarURL,
      email = email)
  }

  /**
   * Find and return the string value of a node.
   *
   * @param json The json value to parse.
   * @param level1 The top level node.
   * @param level2 The value node.
   * @return A string value if the nodes are found, otherwise None.
   */
  private def primaryValue(json: JsValue, level1: String, level2: String): Option[String] = {
    optValue(json, level1, level2, index(json, level1))
  }

  /**
   * Find and return the string value for a value with a default flag.
   *
   * @param json The json value to parse.
   * @param level1 The top level node.
   * @param valueName The name of the value node.
   * @param defaultName The name of the is-default node.
   * @return
   */
  private def primaryValueWithDefault(json: JsValue, level1: String, valueName: String, defaultName: String): Option[String] = {
    val idx = index(json, level1)
    val isDefault = boolValue(json, level1, defaultName, idx)

    if (!isDefault)
      optValue(json, level1, valueName, idx)
    else
      None
  }

  /**
   * Finds the index of a top level node.
   *
   * @see https://developers.google.com/people/api/rest/v1/people#Person.FieldMetadata
   *
   * @param json The json value to parse.
   * @param level1 The top level node.
   * @param primaryMarker The marker to find the level 1 element for.
   * @return The index of the requested top level node, or -1 if none found.
   */
  private def index(json: JsValue, level1: String, primaryMarker: String = "primary"): Int = {
    (json \ level1 \\ primaryMarker).indexWhere(_.as[Boolean] == true)
  }

  /**
   * Tries to extract a string value from a given path.
   *
   * @param json The json value to parse.
   * @param level1 The top level node.
   * @param level2 The value node.
   * @param index The index within the top level node.
   * @return Maybe a string value.
   */
  private def optValue(json: JsValue, level1: String, level2: String, index: Int) = {
    if ((json \ level1 \\ level2).isDefinedAt(index)) {
      (json \ level1 \\ level2)(index).asOpt[String]
    } else {
      None
    }
  }

  /**
   * Tries to extract a boolean value from a given path.
   * If level 1 is not found, returns false.
   * If level 2 not is not found at the given index, returns false.
   *
   * @param json The json value to parse.
   * @param level1 The top level node.
   * @param level2 The value node.
   * @param index The index within the top level node.
   * @return The boolean value.
   */
  private def boolValue(json: JsValue, level1: String, level2: String, index: Int) = {
    if ((json \ level1 \\ level2).isDefinedAt(index)) {
      (json \ level1 \\ level2)(index).asOpt[Boolean].getOrElse(false)
    } else {
      false
    }
  }
}

/**
 * The Google OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler  The state provider implementation.
 * @param settings      The provider settings.
 */
class GoogleProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings)
  extends BaseGoogleProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  type Self = GoogleProvider

  /**
   * The profile parser implementation.
   */
  val profileParser = new GoogleProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  def withSettings(f: (Settings) => Settings) = new GoogleProvider(httpLayer, stateHandler, f(settings))
}

/**
 * The companion object.
 */
object GoogleProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Google constants.
   */
  val ID = "google"
  val API = "https://people.googleapis.com/v1/people/me?personFields=names,photos,emailAddresses&access_token=%s"
}
