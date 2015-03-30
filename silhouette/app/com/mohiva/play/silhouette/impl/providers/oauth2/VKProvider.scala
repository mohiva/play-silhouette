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
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.util.{ Success, Failure, Try }

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
   * The content type to parse a profile from.
   */
  type Content = (JsValue, OAuth2Info)

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
  protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ "error_code").as[Int]
          val errorMsg = (error \ "error_msg").as[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorCode, errorMsg))
        case _ => profileParser.parse(json -> authInfo)
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
    response.json.validate[OAuth2Info].asEither.fold(
      error => Failure(new UnexpectedResponseException(InvalidInfoFormat.format(id, error))),
      info => Success(info)
    )
  }
}

/**
 * The profile parser for the common social profile.
 */
class VKProfileParser extends SocialProfileParser[(JsValue, OAuth2Info), CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param data The data returned from the provider.
   * @return The social profile from given result.
   */
  def parse(data: (JsValue, OAuth2Info)) = Future.successful {
    val json = data._1
    val response = (json \ "response").apply(0)
    val userId = (response \ "uid").as[Long]
    val firstName = (response \ "first_name").asOpt[String]
    val lastName = (response \ "last_name").asOpt[String]
    val avatarURL = (response \ "photo").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userId.toString),
      firstName = firstName,
      lastName = lastName,
      email = data._2.params.flatMap(_.get("email")),
      avatarURL = avatarURL)
  }
}

/**
 * The profile builder for the common social profile.
 */
trait VKProfileBuilder extends CommonSocialProfileBuilder {
  self: VKProvider =>

  /**
   * The profile parser implementation.
   */
  val profileParser = new VKProfileParser
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

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new VKProvider(httpLayer, stateProvider, settings) with VKProfileBuilder
  }
}
