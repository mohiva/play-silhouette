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

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.exceptions.{ AccessDeniedException, UnexpectedResponseException }
import com.mohiva.play.silhouette.impl.providers.OAuth1Provider._
import play.api.libs.ws.WSSignatureCalculator
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Base implementation for all OAuth1 providers.
 */
trait OAuth1Provider extends SocialProvider with OAuth1Constants with Logger {

  /**
   * Check if services uses 1.0a specification because it address the session fixation attack identified
   * in the OAuth Core 1.0 specification.
   *
   * We implement only the 1.0a specification with the new oauth_verifier parameter, so we throw an
   * exception here if the old version was specified for the service.
   *
   * @see http://oauth.net/core/1.0a/
   * @see http://oauth.net/advisories/2009-1/
   */
  if (!service.use10a) {
    throw new RuntimeException("You must use the the 1.0a specification to address the session fixation " +
      "attack identified in the OAuth Core 1.0 specification")
  }

  /**
   * The type of the auth info.
   */
  type A = OAuth1Info

  /**
   * The settings type.
   */
  type Settings = OAuth1Settings

  /**
   * The OAuth1 service implementation.
   */
  protected val service: OAuth1Service

  /**
   * The OAuth1 token secret provider implementation.
   */
  protected val tokenSecretProvider: OAuth1TokenSecretProvider

  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return Either a Result or the auth info from the provider.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, OAuth1Info]] = {
    request.extractString(Denied) match {
      case Some(_) => Future.failed(new AccessDeniedException(AuthorizationError.format(id, Denied)))
      case None => request.extractString(OAuthVerifier) -> request.extractString(OAuthToken) match {
        // Second step in the OAuth flow.
        // We have received the verifier and the request token, and we need to swap it for the access token.
        case (Some(verifier), Some(token)) => tokenSecretProvider.retrieve.flatMap { tokenSecret =>
          service.retrieveAccessToken(OAuth1Info(token, tokenSecret.value), verifier).map { info =>
            Right(info)
          }.recover {
            case e => throw new UnexpectedResponseException(ErrorAccessToken.format(id), e)
          }
        }
        // The oauth_verifier field is not in the request.
        // This is the first step in the OAuth flow. We need to get the request tokens.
        case _ => service.retrieveRequestToken(resolveCallbackURL(settings.callbackURL)).flatMap { info =>
          tokenSecretProvider.build(info).map { tokenSecret =>
            val url = service.redirectUrl(info.token)
            val redirect = Results.Redirect(url)
            logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, url))
            Left(tokenSecretProvider.publish(redirect, tokenSecret))
          }
        }.recover {
          case e => throw new UnexpectedResponseException(ErrorRequestToken.format(id), e)
        }
      }
    }
  }
}

/**
 * The OAuth1Provider companion object.
 */
object OAuth1Provider extends OAuth1Constants {

  /**
   * The error messages.
   */
  val AuthorizationError = "[Silhouette][%s] Authorization server returned error: %s"
  val ErrorAccessToken = "[Silhouette][%s] Error retrieving access token"
  val ErrorRequestToken = "[Silhouette][%s] Error retrieving request token"
}

/**
 * The OAuth1 constants.
 */
trait OAuth1Constants {

  val Denied = "denied"
  val OAuthVerifier = "oauth_verifier"
  val OAuthToken = "oauth_token"
}

/**
 * The OAuth1 service trait.
 */
trait OAuth1Service {

  /**
   * The type of the concrete implementation of this abstract type.
   */
  type Self <: OAuth1Service

  /**
   * Indicates if the service uses the safer 1.0a specification which addresses the session fixation attack
   * identified in the OAuth Core 1.0 specification.
   *
   * @see http://oauth.net/core/1.0a/
   * @see http://oauth.net/advisories/2009-1/
   * @return True if the services uses 1.0a specification, false otherwise.
   */
  def use10a: Boolean

  /**
   * Retrieves the request info and secret.
   *
   * @param callbackURL The URL where the provider should redirect to (usually a URL on the current app).
   * @param ec          The execution context to handle the asynchronous operations.
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  def retrieveRequestToken(callbackURL: String)(implicit ec: ExecutionContext): Future[OAuth1Info]

  /**
   * Exchange a request info for an access info.
   *
   * @param oAuthInfo The info/secret pair obtained from a previous call.
   * @param verifier  A string you got through your user with redirection.
   * @param ec The execution context to handle the asynchronous operations.
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  def retrieveAccessToken(oAuthInfo: OAuth1Info, verifier: String)(implicit ec: ExecutionContext): Future[OAuth1Info]

  /**
   * The URL to which the user needs to be redirected to grant authorization to your application.
   *
   * @param token The request info.
   * @return The redirect URL.
   */
  def redirectUrl(token: String): String

  /**
   * Creates the signature calculator for the OAuth request.
   *
   * @param oAuthInfo The info/secret pair obtained from a previous call.
   * @return The signature calculator for the OAuth1 request.
   */
  def sign(oAuthInfo: OAuth1Info): WSSignatureCalculator

  /**
   * Gets a service initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the service initialized with new settings.
   */
  def withSettings(f: OAuth1Settings => OAuth1Settings): Self
}

/**
 * The OAuth1 token secret.
 *
 * This represents the oauth_token_secret returned from the provider with the request token and which
 * is then needed to retrieve the access token. The secret must be stored between two requests and
 * this implementation provides an abstract way to store the secret in different locations.
 */
trait OAuth1TokenSecret {

  /**
   * The secret.
   *
   * @return The secret.
   */
  def value: String

  /**
   * Checks if the secret is expired. This is an absolute timeout since the creation of
   * the secret.
   *
   * @return True if the secret is expired, false otherwise.
   */
  def isExpired: Boolean

  /**
   * Returns a serialized value of the secret.
   *
   * @return A serialized value of the secret.
   */
  def serialize: String
}

/**
 * Provides the token secret for OAuth1 authentication providers.
 */
trait OAuth1TokenSecretProvider {

  /**
   * The type of the secret implementation.
   */
  type Secret <: OAuth1TokenSecret

  /**
   * Builds the secret from OAuth info.
   *
   * @param info    The OAuth info returned from the provider.
   * @param request The current request.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The build secret.
   */
  def build[B](info: OAuth1Info)(implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Secret]

  /**
   * Retrieves the token secret.
   *
   * @param request The current request.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return A secret on success, otherwise an failure.
   */
  def retrieve[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Secret]

  /**
   * Publishes the secret to the client.
   *
   * @param result  The result to send to the client.
   * @param secret  The secret to publish.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def publish[B](result: Result, secret: Secret)(implicit request: ExtractableRequest[B]): Result
}

/**
 * The OAuth1 settings.
 *
 * @param requestTokenURL  The request token URL provided by the OAuth provider.
 * @param accessTokenURL   The access token URL provided by the OAuth provider.
 * @param authorizationURL The authorization URL provided by the OAuth provider.
 * @param callbackURL      The callback URL to the application after a successful authentication on the OAuth
 *                         provider. The URL can be a relative path which will be resolved against the current
 *                         request's host.
 * @param apiURL           The URL to fetch the profile from the API. Can be used to override the default URL
 *                         hardcoded in every provider implementation.
 * @param consumerKey      The consumer ID provided by the OAuth provider.
 * @param consumerSecret   The consumer secret provided by the OAuth provider.
 */
case class OAuth1Settings(
  requestTokenURL: String,
  accessTokenURL: String,
  authorizationURL: String,
  callbackURL: String,
  apiURL: Option[String] = None,
  consumerKey: String,
  consumerSecret: String)

/**
 * The OAuth1 details.
 *
 * @param token  The consumer token.
 * @param secret The consumer secret.
 */
case class OAuth1Info(token: String, secret: String) extends AuthInfo
