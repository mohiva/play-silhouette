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
package com.mohiva.play.silhouette.core.providers

import java.util.UUID
import play.api.Logger
import play.api.libs.oauth._
import play.api.mvc.{RequestHeader, Result, Results}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{HTTPLayer, CacheLayer}
import OAuth1Provider._

/**
 * Base class for all OAuth1 providers.
 *
 * @param settings The provider settings.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @tparam I The type of the identity.
 */
abstract class OAuth1Provider[I <: Identity](
    settings: OAuth1Settings,
    cacheLayer: CacheLayer,
    httpLayer: HTTPLayer)
  extends SocialProvider[I, OAuth1Info] {

  /**
   * The OAuth1 service.
   */
  val service = OAuth(serviceInfo, use10a = true)

  /**
   * Gets the auth method.
   *
   * @return The auth method.
   */
  def authMethod = AuthenticationMethod.OAuth1

  /**
   * Starts the authentication process.
   *
   * @param request The request header.
   * @return Either a Result or the auth info from the provider.
   */
  def doAuth()(implicit request: RequestHeader): Future[Either[Result, OAuth1Info]] = {
    if (request.queryString.get(Denied).isDefined) {
      throw new AccessDeniedException(AuthorizationError.format(id, Denied))
    }

    request.queryString.get(OAuthVerifier) match {
      // Second step in the oauth flow, we have the access token in the cache, we need to
      // swap it for the access token
      case Some(seq) => cachedToken.flatMap { case (cacheID, requestToken) =>
        Future(service.retrieveAccessToken(RequestToken(requestToken.token, requestToken.secret), seq.head)).map(_.fold(
          exception => throw new AuthenticationException(ErrorAccessToken.format(id), exception),
          token => {
            cacheLayer.remove(cacheID)
            Right(OAuth1Info(token.token, token.secret))
          }))
      }
      // The oauth_verifier field is not in the request, this is the first step in the auth flow.
      // we need to get the request tokens
      case _ => Future(service.retrieveRequestToken(settings.callbackURL)).map(_.fold(
        exception => throw new AuthenticationException(ErrorRequestToken.format(id), exception),
        token => {
          val cacheID = UUID.randomUUID().toString
          val url = service.redirectUrl(token.token)
          val redirect = Results.Redirect(url).withSession(request.session + (CacheKey -> cacheID))
          if (Logger.isDebugEnabled) {
            Logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, url))
          }
          cacheLayer.set(cacheID, token, 600) // set it for 10 minutes, plenty of time to log in
          Left(redirect)
        }))
    }
  }

  /**
   * Builds the service info.
   *
   * @return The service info.
   */
  protected def serviceInfo: ServiceInfo = ServiceInfo(
    settings.requestTokenURL,
    settings.accessTokenURL,
    settings.authorizationURL,
    ConsumerKey(settings.consumerKey, settings.consumerSecret))

  /**
   * Gets the cached token if it's stored in cache.
   *
   * @param request The request header.
   * @return A tuple contains the cache ID with the cached token.
   */
  private def cachedToken(implicit request: RequestHeader): Future[(String, RequestToken)] = {
    request.session.get(CacheKey) match {
      case Some(cacheID) => cacheLayer.get[RequestToken](cacheID).map {
        case Some(state) => cacheID -> state
        case _ => throw new AuthenticationException(CachedTokenDoesNotExists.format(id, cacheID))
      }
      case _ => throw new AuthenticationException(CacheKeyNotInSession.format(id, CacheKey))
    }
  }
}

/**
 * The companion object.
 */
object OAuth1Provider {

  /**
   * The error messages.
   */
  val AuthorizationError = "[Silhouette][%s] Authorization server returned error: '%s'"
  val CacheKeyNotInSession = "[Silhouette][%s] Session doesn't contain cache key: %s"
  val CachedTokenDoesNotExists = "[Silhouette][%s] Token doesn't exists in cache for cache key: %s"
  val ErrorAccessToken = "[Silhouette][%s] Error retrieving access token"
  val ErrorRequestToken = "[Silhouette][%s] Error retrieving request token"

  /**
   * The OAuth1 constants.
   */
  val CacheKey = "silhouetteOAuth1Cache"
  val Denied = "denied"
  val OAuthVerifier = "oauth_verifier"
}

/**
 * The OAuth2 settings.
 *
 * @param requestTokenURL The request token URL.
 * @param accessTokenURL The access token URL.
 * @param authorizationURL The authorization URL.
 * @param callbackURL The callback URL.
 * @param consumerKey The consumer ID.
 * @param consumerSecret The consumer secret.
 */
case class OAuth1Settings(
  requestTokenURL: String,
  accessTokenURL: String,
  authorizationURL: String,
  callbackURL: String,
  consumerKey: String,
  consumerSecret: String)

/**
 * The OAuth1 details.
 *
 * @param token The consumer token.
 * @param secret The consumer secret.
 */
case class OAuth1Info(token: String, secret: String)

/**
 * The base trait for all OAuth1 identities.
 */
trait OAuth1Identity extends Identity {

  /**
   * Gets the auth info.
   *
   * @return The auth info.
   */
  def authInfo: OAuth1Info
}
