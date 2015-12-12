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
package com.mohiva.play.silhouette.impl.providers.oauth1.services

import com.mohiva.play.silhouette.api.Logger
import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service._
import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth1Service, OAuth1Settings }
import play.api.libs.oauth.{ ConsumerKey, OAuth, RequestToken, ServiceInfo, _ }
import play.api.libs.ws.WSSignatureCalculator

import scala.concurrent.{ ExecutionContext, Future }

/**
 * The OAuth1 service implementation which wraps Play Framework's OAuth implementation.
 *
 * @param service The Play Framework OAuth implementation.
 * @param settings The service settings.
 */
class PlayOAuth1Service(service: OAuth, val settings: OAuth1Settings) extends OAuth1Service with Logger {

  /**
   * The type of this class.
   */
  override type Self = PlayOAuth1Service

  /**
   * Constructs the default Play Framework OAuth implementation.
   *
   * @param settings The service settings.
   * @return The OAuth1 service.
   */
  def this(settings: OAuth1Settings) = this(OAuth(serviceInfo(settings), use10a = true), settings)

  /**
   * Indicates if the service uses the safer 1.0a specification which addresses the session fixation attack
   * identified in the OAuth Core 1.0 specification.
   *
   * @see http://oauth.net/core/1.0a/
   * @see http://oauth.net/advisories/2009-1/
   *
   * @return True if the services uses 1.0a specification, false otherwise.
   */
  override def use10a = service.use10a

  /**
   * Retrieves the request info and secret.
   *
   * @param callbackURL The URL where the provider should redirect to (usually a URL on the current app).
   * @param ec The execution context to handle the asynchronous operations.
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  override def retrieveRequestToken(callbackURL: String)(implicit ec: ExecutionContext): Future[OAuth1Info] = {
    Future(service.retrieveRequestToken(settings.callbackURL)).map(_.fold(
      e => throw e,
      t => OAuth1Info(t.token, t.secret)))
  }

  /**
   * Exchange a request info for an access info.
   *
   * @param oAuthInfo The info/secret pair obtained from a previous call.
   * @param verifier A string you got through your user, with redirection.
   * @param ec The execution context to handle the asynchronous operations.
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  override def retrieveAccessToken(oAuthInfo: OAuth1Info, verifier: String)(implicit ec: ExecutionContext): Future[OAuth1Info] = {
    Future(service.retrieveAccessToken(RequestToken(oAuthInfo.token, oAuthInfo.secret), verifier)).map(_.fold(
      e => throw e,
      t => OAuth1Info(t.token, t.secret)))
  }

  /**
   * The URL to which the user needs to be redirected to grant authorization to your application.
   *
   * @param token The request info.
   * @return The redirect URL.
   */
  override def redirectUrl(token: String): String = service.redirectUrl(token)

  /**
   * Creates the signature calculator for the OAuth info.
   *
   * @param oAuthInfo The info/secret pair obtained from a previous call.
   * @return The signature calculator for the OAuth1 request.
   */
  override def sign(oAuthInfo: OAuth1Info): WSSignatureCalculator = {
    OAuthCalculator(service.info.key, RequestToken(oAuthInfo.token, oAuthInfo.secret))
  }

  /**
   * Gets a service initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the service initialized with new settings.
   */
  override def withSettings(f: (OAuth1Settings) => OAuth1Settings) = {
    new PlayOAuth1Service(f(settings))
  }
}

/**
 * The companion object.
 */
object PlayOAuth1Service {

  /**
   * Builds the service info.
   *
   * @return The service info.
   */
  def serviceInfo(settings: OAuth1Settings) = ServiceInfo(
    settings.requestTokenURL,
    settings.accessTokenURL,
    settings.authorizationURL,
    ConsumerKey(settings.consumerKey, settings.consumerSecret))
}
