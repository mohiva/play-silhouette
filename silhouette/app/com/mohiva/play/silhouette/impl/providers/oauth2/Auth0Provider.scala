/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.mohiva.play.silhouette.impl.providers.oauth2.Auth0Provider._
import play.api.libs.json.{ JsString, JsValue }
import play.api.mvc.RequestHeader
import play.mvc.Http

import scala.concurrent.Future

/**
 * Base Auth0 OAuth2 Provider.
 *
 * OAuth Provider configuration in silhouette.conf must indicate:
 *
 *   # Auth0 Service URLs
 *   auth0.authorizationURL="https://mydomain.eu.auth0.com/authorize"
 *   auth0.accessTokenURL="https://mydomain.eu.auth0.com/oauth/token"
 *   auth0.apiURL="https://mydomain.eu.auth0.com/userinfo"
 *
 *   # Application URL and credentials
 *   auth0.redirectURL="http://localhost:9000/authenticate/auth0"
 *   auth0.clientID=myoauthclientid
 *   auth0.clientSecret=myoauthclientsecret
 *
 *   # Auth0 user's profile information requested
 *   auth0.scope="openid profile email"
 *
 * See http://auth0.com for more information on the Auth0 Auth 2.0 Provider and Service.
 */
trait BaseAuth0Provider extends OAuth2Provider {

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
    val request = httpLayer.url(urls("api"))
    val requestWithHeader = request.withHttpHeaders(("Authorization", s"Bearer ${authInfo.accessToken}"))

    val httpResponse = requestWithHeader.get()
    httpResponse.flatMap { response =>
      response.status match {
        case Http.Status.OK =>
          profileParser.parse(response.json, authInfo)

        case _ => {
          val json = response.json
          (json \ "error").asOpt[JsString] match {
            case Some(error) =>
              val errorDescription = (json \ "error_description")
              throw new ProfileRetrievalException(SpecifiedProfileError.format(id, errorDescription, error, response.status))
            case _ =>
              throw new ProfileRetrievalException(GenericHttpStatusProfileError.format(id, response.status))
          }
        }
      }
    }
  }

  /**
   * Gets the access token.
   *
   * @param code    The access code.
   * @param request The current request.
   * @return The info containing the access token.
   */
  override protected def getAccessToken(code: String)(implicit request: RequestHeader): Future[OAuth2Info] = {
    request.getQueryString("token_type") match {
      case Some("bearer") => Future(OAuth2Info(code))
      case _              => super.getAccessToken(code)
    }
  }
}

/**
 * The profile parser for the common social profile.
 */
class Auth0ProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param json The content returned from the provider.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info): Future[CommonSocialProfile] = Future.successful {
    val userID = (json \ "sub").as[String]
    val fullName = (json \ "name").asOpt[String]
    val avatarURL = (json \ "picture").asOpt[String]
    val email = (json \ "email").asOpt[String]

    CommonSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      fullName = fullName,
      avatarURL = avatarURL,
      email = email)
  }
}

/**
 * The Auth0 OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateHandler The state provider implementation.
 * @param settings The provider settings.
 */
class Auth0Provider(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings)
  extends BaseAuth0Provider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = Auth0Provider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new Auth0ProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new Auth0Provider(httpLayer, stateHandler, f(settings))
}

/**
 * The companion object.
 */
object Auth0Provider {

  /**
   * The error messages.
   */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error message: %s, type: %s, code: %s"

  val GenericHttpStatusProfileError = "[Silhouette][%s] Cannot get user profile from provider - HTTP status code %s"

  /**
   * The Auth0 provider constant.
   */
  val ID = "auth0"

  /**
   * Default Auth0 provider endpoint
   */
  val API = "https://auth0.auth0.com/userinfo"
}

