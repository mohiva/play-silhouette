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
package com.mohiva.play.silhouette.impl.providers

import java.util.UUID

import com.mohiva.play.silhouette.api._
import com.mohiva.silhouette.exceptions._
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers.OAuth1Provider._
import com.mohiva.silhouette.services.AuthInfo
import com.mohiva.silhouette.util.CacheLayer
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSSignatureCalculator
import play.api.mvc.{ Result, RequestHeader, Results }

import scala.concurrent.Future

/**
 * Base class for all OAuth1 providers.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param service The OAuth1 service implementation.
 * @param settings The OAuth1 provider settings.
 */
abstract class OAuth1Provider(
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  service: OAuth1Service,
  settings: OAuth1Settings) extends SocialProvider with Logger {

  /**
   * The type of the auth info.
   */
  type A = OAuth1Info

  /**
   * Starts the authentication process.
   *
   * @param request The request header.
   * @return Either a Result or the auth info from the provider.
   */
  def authenticate()(implicit request: RequestHeader): Future[Either[Result, OAuth1Info]] = {
    logger.debug("[Silhouette][%s] Query string: %s".format(id, request.rawQueryString))
    request.queryString.get(Denied) match {
      case Some(_) => Future.failed(new AccessDeniedException(AuthorizationError.format(id, Denied)))
      case None => request.queryString.get(OAuthVerifier) match {
        // Second step in the OAuth flow.
        // We have the request info in the cache, and we need to swap it for the access info.
        case Some(seq) => cachedInfo.flatMap {
          case (cacheID, cachedInfo) =>
            service.retrieveAccessToken(cachedInfo, seq.head).map { info =>
              cacheLayer.remove(cacheID)
              Right(info)
            }.recover {
              case e => throw new AuthenticationException(ErrorAccessToken.format(id), e)
            }
        }
        // The oauth_verifier field is not in the request.
        // This is the first step in the OAuth flow. We need to get the request tokens.
        case _ => service.retrieveRequestToken(settings.callbackURL).flatMap { info =>
          val cacheID = UUID.randomUUID().toString
          val url = service.redirectUrl(info.token)
          val redirect = Results.Redirect(url).withSession(request.session + (CacheKey -> cacheID))
          logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, url))
          cacheLayer.save(cacheID, info, CacheExpiration).map(_ =>
            Left(redirect)
          )
        }.recover {
          case e => throw new AuthenticationException(ErrorRequestToken.format(id), e)
        }
      }
    }
  }

  /**
   * Gets the cached info if it's stored in cache.
   *
   * @param request The request header.
   * @return A tuple contains the cache ID with the cached info.
   */
  private def cachedInfo(implicit request: RequestHeader): Future[(String, OAuth1Info)] = {
    request.session.get(CacheKey) match {
      case Some(cacheID) => cacheLayer.find[OAuth1Info](cacheID).map {
        case Some(state) => cacheID -> state
        case _ => throw new AuthenticationException(CachedTokenDoesNotExists.format(id, cacheID))
      }
      case _ => Future.failed(new AuthenticationException(CacheKeyNotInSession.format(id, CacheKey)))
    }
  }
}

/**
 * The OAuth1Provider companion object.
 */
object OAuth1Provider {

  /**
   * The error messages.
   */
  val AuthorizationError = "[Silhouette][%s] Authorization server returned error: %s"
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

  /**
   * Cache expiration. Provides sufficient time to log in, but not too much.
   * This is a balance between convenience and security.
   */
  val CacheExpiration = 5 * 60 // 5 minutes
}

/**
 * The OAuth1 service trait.
 */
trait OAuth1Service {

  /**
   * Retrieves the request info and secret.
   *
   * @param callbackURL The URL where the provider should redirect to (usually a URL on the current app).
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  def retrieveRequestToken(callbackURL: String): Future[OAuth1Info]

  /**
   * Exchange a request info for an access info.
   *
   * @param oAuthInfo The info/secret pair obtained from a previous call.
   * @param verifier A string you got through your user with redirection.
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  def retrieveAccessToken(oAuthInfo: OAuth1Info, verifier: String): Future[OAuth1Info]

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
}

/**
 * The OAuth1 settings.
 *
 * @param requestTokenURL The request token URL provided by the OAuth provider.
 * @param accessTokenURL The access token URL provided by the OAuth provider.
 * @param authorizationURL The authorization URL provided by the OAuth provider.
 * @param callbackURL The callback URL to the application after a successful authentication on the OAuth provider.
 * @param consumerKey The consumer ID provided by the OAuth provider.
 * @param consumerSecret The consumer secret provided by the OAuth provider.
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
case class OAuth1Info(token: String, secret: String) extends AuthInfo
