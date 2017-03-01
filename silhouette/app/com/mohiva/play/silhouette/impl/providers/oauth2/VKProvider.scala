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
import com.mohiva.play.silhouette.impl.exceptions.{ UnexpectedResponseException, ProfileRetrievalException }
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.VKProvider._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.util.{ Success, Failure, Try }

/**
 * Base Vk OAuth 2 provider.
 *
 * @see http://vk.com/dev/auth_sites
 * @see http://vk.com/dev/api_requests
 * @see http://vk.com/pages.php?o=-1&p=getProfiles
 */
trait BaseVKProvider extends OAuth2Provider {

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
          val errorCode = (error \ "error_code").as[Int]
          val errorMsg = (error \ "error_msg").as[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorCode, errorMsg))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }

  /**
   * Builds the OAuth2 info from response.
   *
   * VK provider needs it own Json reads to extract the email from response.
   *
   * @param response The response from the provider.
   * @return The OAuth2 info on success, otherwise a failure.
   */
  override protected def buildInfo(response: WSResponse): Try[OAuth2Info] = {
    Try(response.json) match {
      case Success(json) => json.validate[OAuth2Info].asEither.fold(
        error => Failure(new UnexpectedResponseException(InvalidInfoFormat.format(id, error))),
        info => Success(info)
      )
      case Failure(error) => Failure(new UnexpectedResponseException(JsonParseError.format(id, response.body, error)))
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class VKProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json     The data returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val response = (json \ "response").apply(0)
    val userId = (response \ "uid").as[Long]
    val firstName = (response \ "first_name").asOpt[String]
    val lastName = (response \ "last_name").asOpt[String]
    val avatarURL = (response \ "photo").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userId.toString),
      firstName = firstName,
      lastName = lastName,
      email = authInfo.params.flatMap(_.get("email")),
      avatarURL = avatarURL)
  }
}

/**
 * The VK OAuth2 Provider.
 *
 * @param httpLayer     The HTTP layer implementation.
 * @param stateHandler  The state provider implementation.
 * @param settings      The provider settings.
 */
class VKProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings)
  extends BaseVKProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = VKProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new VKProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new VKProvider(httpLayer, stateHandler, f(settings))
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
   * Converts the JSON into a [[OAuth2Info]] object.
   */
  implicit val infoReads: Reads[OAuth2Info] = (
    (__ \ AccessToken).read[String] and
    (__ \ TokenType).readNullable[String] and
    (__ \ ExpiresIn).readNullable[Int] and
    (__ \ RefreshToken).readNullable[String] and
    (__ \ "email").readNullable[String]
  )((accessToken: String, tokenType: Option[String], expiresIn: Option[Int], refreshToken: Option[String], email: Option[String]) =>
      new OAuth2Info(accessToken, tokenType, expiresIn, refreshToken, email.map(e => Map("email" -> e)))
    )
}
