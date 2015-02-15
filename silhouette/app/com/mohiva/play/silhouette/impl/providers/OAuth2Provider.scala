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
package com.mohiva.play.silhouette.impl.providers

import java.net.URLEncoder._

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions._
import com.mohiva.play.silhouette.api.services.AuthInfo
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, HTTPLayer }
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.{ impl, _ }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.api.mvc._

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * The Oauth2 info.
 *
 * @param accessToken The access token.
 * @param tokenType The token type.
 * @param expiresIn The number of seconds before the token expires.
 * @param refreshToken The refresh token.
 * @param params Additional params transported in conjunction with the token.
 */
case class OAuth2Info(
  accessToken: String,
  tokenType: Option[String] = None,
  expiresIn: Option[Int] = None,
  refreshToken: Option[String] = None,
  params: Option[Map[String, String]] = None) extends AuthInfo

/**
 * The Oauth2 info companion object.
 */
object OAuth2Info {

  /**
   * Converts the JSON into a [[impl.providers.OAuth2Info]] object.
   */
  implicit val infoReads = (
    (__ \ AccessToken).read[String] and
    (__ \ TokenType).readNullable[String] and
    (__ \ ExpiresIn).readNullable[Int] and
    (__ \ RefreshToken).readNullable[String]
  )((accessToken: String, tokenType: Option[String], expiresIn: Option[Int], refreshToken: Option[String]) =>
      new OAuth2Info(accessToken, tokenType, expiresIn, refreshToken)
    )
}

/**
 * Base class for all OAuth2 providers.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param stateProvider The state provider implementation.
 * @param settings The provider settings.
 */
abstract class OAuth2Provider(httpLayer: HTTPLayer, stateProvider: OAuth2StateProvider, settings: OAuth2Settings)
  extends SocialProvider with Logger {

  /**
   * The type of the auth info.
   */
  type A = OAuth2Info

  /**
   * A list with headers to send to the API.
   */
  protected val headers: Seq[(String, String)] = Seq()

  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return Either a Result or the auth info from the provider.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, OAuth2Info]] = {
    request.extractString(Error).map {
      case e @ AccessDenied => new AccessDeniedException(AuthorizationError.format(id, e))
      case e => new AuthenticationException(AuthorizationError.format(id, e))
    } match {
      case Some(throwable) => Future.failed(throwable)
      case None => request.extractString(Code) match {
        // We're being redirected back from the authorization server with the access code
        case Some(code) => stateProvider.validate(id).recoverWith {
          case e => Future.failed(new AuthenticationException(InvalidState.format(id), e))
        }.flatMap { state =>
          getAccessToken(code).map(oauth2Info => Right(oauth2Info))
        }
        // There's no code in the request, this is the first step in the OAuth flow
        case None => stateProvider.build.map { state =>
          val serializedState = state.serialize
          val stateParam = if (serializedState.isEmpty) List() else List(State -> serializedState)
          val params = settings.scope.foldLeft(List(
            (ClientID, settings.clientID),
            (RedirectURI, settings.redirectURL),
            (ResponseType, Code)) ++ stateParam ++ settings.authorizationParams.toList) {
            case (p, s) => (Scope, s) :: p
          }
          val encodedParams = params.map { p => encode(p._1, "UTF-8") + "=" + encode(p._2, "UTF-8") }
          val url = settings.authorizationURL.getOrElse {
            throw new AuthenticationException(AuthorizationURLUndefined.format(id))
          } + encodedParams.mkString("?", "&", "")
          val redirect = stateProvider.publish(Results.Redirect(url), state)
          logger.debug("[Silhouette][%s] Use authorization URL: %s".format(id, settings.authorizationURL))
          logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, url))
          Left(redirect)
        }
      }
    }
  }

  /**
   * Gets the access token.
   *
   * @param code The access code.
   * @return The info containing the access token.
   */
  protected def getAccessToken(code: String): Future[OAuth2Info] = {
    httpLayer.url(settings.accessTokenURL).withHeaders(headers: _*).post(Map(
      ClientID -> Seq(settings.clientID),
      ClientSecret -> Seq(settings.clientSecret),
      GrantType -> Seq(AuthorizationCode),
      Code -> Seq(code),
      RedirectURI -> Seq(settings.redirectURL)) ++ settings.accessTokenParams.mapValues(Seq(_))).flatMap { response =>
      logger.debug("[Silhouette][%s] Access token response: [%s]".format(id, response.body))
      Future.from(buildInfo(response))
    }
  }

  /**
   * Builds the OAuth2 info from response.
   *
   * @param response The response from the provider.
   * @return The OAuth2 info on success, otherwise a failure.
   */
  protected def buildInfo(response: WSResponse): Try[OAuth2Info] = {
    response.json.validate[OAuth2Info].asEither.fold(
      error => Failure(new AuthenticationException(InvalidInfoFormat.format(id, error))),
      info => Success(info)
    )
  }
}

/**
 * The OAuth2Provider companion object.
 */
object OAuth2Provider {

  /**
   * The error messages.
   */
  val AuthorizationURLUndefined = "[Silhouette][%s] Authorization URL is undefined"
  val AuthorizationError = "[Silhouette][%s] Authorization server returned error: %s"
  val InvalidInfoFormat = "[Silhouette][%s] Cannot build OAuth2Info because of invalid response format  : %s"
  val InvalidState = "[Silhouette][%s] Invalid state"

  /**
   * The OAuth2 constants.
   */
  val ClientID = "client_id"
  val ClientSecret = "client_secret"
  val RedirectURI = "redirect_uri"
  val Scope = "scope"
  val ResponseType = "response_type"
  val State = "state"
  val GrantType = "grant_type"
  val AuthorizationCode = "authorization_code"
  val AccessToken = "access_token"
  val Error = "error"
  val Code = "code"
  val TokenType = "token_type"
  val ExpiresIn = "expires_in"
  val Expires = "expires"
  val RefreshToken = "refresh_token"
  val AccessDenied = "access_denied"
}

/**
 * The OAuth2 state.
 *
 * This is to prevent the client for CSRF attacks as described in the OAuth2 RFC.
 * @see https://tools.ietf.org/html/rfc6749#section-10.12
 */
trait OAuth2State {

  /**
   * Checks if the state is expired. This is an absolute timeout since the creation of
   * the state.
   *
   * @return True if the state is expired, false otherwise.
   */
  def isExpired: Boolean

  /**
   * Returns a serialized value of the state.
   *
   * @return A serialized value of the state.
   */
  def serialize: String
}

/**
 * Provides state for authentication providers.
 */
trait OAuth2StateProvider {

  /**
   * The type of the state implementation.
   */
  type State <: OAuth2State

  /**
   * Builds the state.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The build state.
   */
  def build[B](implicit request: ExtractableRequest[B]): Future[State]

  /**
   * Validates the provider and the client state.
   *
   * @param id The provider ID.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The state on success, otherwise an failure.
   */
  def validate[B](id: String)(implicit request: ExtractableRequest[B]): Future[State]

  /**
   * Publishes the state to the client.
   *
   * @param result The result to send to the client.
   * @param state The state to publish.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def publish[B](result: Result, state: State)(implicit request: ExtractableRequest[B]): Result
}

/**
 * The OAuth2 settings.
 *
 * @param authorizationURL The authorization URL provided by the OAuth provider.
 * @param accessTokenURL The access token URL provided by the OAuth provider.
 * @param redirectURL The redirect URL to the application after a successful authentication on the OAuth provider.
 * @param clientID The client ID provided by the OAuth provider.
 * @param clientSecret The client secret provided by the OAuth provider.
 * @param scope The OAuth2 scope parameter provided by the OAuth provider.
 * @param authorizationParams Additional params to add to the authorization request.
 * @param accessTokenParams Additional params to add to the access token request.
 * @param customProperties A map of custom properties for the different providers.
 */
case class OAuth2Settings(
  authorizationURL: Option[String] = None,
  accessTokenURL: String,
  redirectURL: String,
  clientID: String,
  clientSecret: String,
  scope: Option[String] = None,
  authorizationParams: Map[String, String] = Map(),
  accessTokenParams: Map[String, String] = Map(),
  customProperties: Map[String, String] = Map())
