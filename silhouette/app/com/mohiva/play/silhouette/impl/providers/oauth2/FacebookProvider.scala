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
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider._
import play.api.libs.json.{ JsObject, JsValue }
import play.api.libs.ws.WSResponse

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import scala.concurrent.ExecutionContext

/**
 * A Facebook OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 *
 * @see https://developers.facebook.com/tools/explorer
 * @see https://developers.facebook.com/docs/graph-api/reference/user
 * @see https://developers.facebook.com/docs/facebook-login/access-tokens
 */
abstract class FacebookProvider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
  extends OAuth2Provider(httpLayer, stateProvider, settings) {

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
  protected def buildProfile(authInfo: OAuth2Info)(implicit ec: ExecutionContext): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "error").asOpt[JsObject] match {
        case Some(error) =>
          val errorMsg = (error \ "message").as[String]
          val errorType = (error \ "type").as[String]
          val errorCode = (error \ "code").as[Int]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorMsg, errorType, errorCode))
        case _ => profileParser.parse(json)
      }
    }
  }

  /**
   * Builds the OAuth2 info.
   *
   * Facebook does not follow the OAuth2 spec :-\
   *
   * @param response The response from the provider.
   * @return The OAuth2 info on success, otherwise an failure.
   */
  override protected def buildInfo(response: WSResponse): Try[OAuth2Info] = {
    response.body.split("&|=") match {
      case Array(AccessToken, token, Expires, expiresIn) => Success(OAuth2Info(token, None, Some(expiresIn.toInt)))
      case Array(AccessToken, token) => Success(OAuth2Info(token))
      case _ => Failure(new UnexpectedResponseException(InvalidInfoFormat.format(id, response.body)))
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class FacebookProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  def parse(json: JsValue)(implicit ec: ExecutionContext) = Future.successful {
    val userID = (json \ "id").as[String]
    val firstName = (json \ "first_name").asOpt[String]
    val lastName = (json \ "last_name").asOpt[String]
    val fullName = (json \ "name").asOpt[String]
    val avatarURL = (json \ "picture" \ "data" \ "url").asOpt[String]
    val email = (json \ "email").asOpt[String]

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
trait FacebookProfileBuilder extends CommonSocialProfileBuilder {
  self: FacebookProvider =>

  /**
   * The profile parser implementation.
   */
  val profileParser = new FacebookProfileParser
}

/**
 * The companion object.
 */
object FacebookProvider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s, type: %s, code: %s"

  /**
   * The Facebook constants.
   */
  val ID = "facebook"
  val API = "https://graph.facebook.com/me?fields=name,first_name,last_name,picture,email&return_ssl_resources=1&access_token=%s"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The state provider implementation.
   * @param settings The provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings) = {
    new FacebookProvider(httpLayer, stateProvider, settings) with FacebookProfileBuilder
  }
}
