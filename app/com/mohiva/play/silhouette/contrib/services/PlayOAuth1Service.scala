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
package com.mohiva.play.silhouette.contrib.services

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.oauth._
import play.api.libs.ws.SignatureCalculator
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.ServiceInfo
import play.api.libs.oauth.OAuth
import play.api.libs.oauth.RequestToken
import com.mohiva.play.silhouette.core.providers.{ OAuth1Info, OAuth1Service, OAuth1Settings }
import PlayOAuth1Service._

/**
 * The OAuth1 service implementation which wraps Play Framework's OAuth implementation.
 *
 * @param service The Play Framework OAuth implementation.
 * @param settings The service settings.
 */
class PlayOAuth1Service(service: OAuth, settings: OAuth1Settings) extends OAuth1Service {

  /**
   * Constructs the default Play Framework OAuth implementation.
   *
   * @param settings The service settings.
   * @return The OAuth1 service.
   */
  def this(settings: OAuth1Settings) = this(OAuth(serviceInfo(settings), use10a = true), settings)

  /**
   * Retrieves the request info and secret.
   *
   * @param callbackURL The URL where the provider should redirect to (usually a URL on the current app).
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  def retrieveRequestToken(callbackURL: String): Future[OAuth1Info] = {
    Future(service.retrieveRequestToken(settings.callbackURL)).map(_.fold(
      e => throw e,
      t => OAuth1Info(t.token, t.secret)))
  }

  /**
   * Exchange a request info for an access info.
   *
   * @param oAuthInfo The info/secret pair obtained from a previous call.
   * @param verifier A string you got through your user, with redirection.
   * @return A OAuth1Info in case of success, Exception otherwise.
   */
  def retrieveAccessToken(oAuthInfo: OAuth1Info, verifier: String): Future[OAuth1Info] = {
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
  def redirectUrl(token: String): String = service.redirectUrl(token)

  /**
   * Creates the signature calculator for the OAuth info.
   *
   * @param oAuthInfo The info/secret pair obtained from a previous call.
   * @return The signature calculator for the OAuth1 request.
   */
  def sign(oAuthInfo: OAuth1Info): SignatureCalculator = {
    OAuthCalculator(service.info.key, RequestToken(oAuthInfo.token, oAuthInfo.secret))
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
