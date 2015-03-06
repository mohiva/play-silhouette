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
package com.mohiva.play.silhouette.impl.providers.openid.services

import com.mohiva.play.silhouette.impl.providers.{ OpenIDInfo, OpenIDService, OpenIDSettings }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.openid.OpenID
import play.api.mvc.Request

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

/**
 * The OpenID service implementation which wraps Play Framework's OpenID implementation.
 *
 * @param settings The OpenID settings.
 */
class PlayOpenIDService(settings: OpenIDSettings) extends OpenIDService {

  /**
   * Retrieve the URL where the user should be redirected to start the OpenID authentication process.
   *
   * @param openID The OpenID to use for authentication.
   * @param resolvedCallbackURL The full callback URL to the application after a successful authentication.
   * @return The redirect URL where the user should be redirected to start the OpenID authentication process.
   */
  def redirectURL(openID: String, resolvedCallbackURL: String): Future[String] = Try {
    OpenID.redirectURL(openID, resolvedCallbackURL, settings.axRequired, settings.axOptional, settings.realm)
  } match {
    case Success(f) => f
    case Failure(e) => Future.failed(e)
  }

  /**
   * From a request corresponding to the callback from the OpenID server, check the identity of the current user.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return A OpenIDInfo in case of success, Exception otherwise.
   */
  def verifiedID[B](implicit request: Request[B]) = Try {
    OpenID.verifiedId.map(info => OpenIDInfo(info.id, info.attributes))
  } match {
    case Success(f) => f
    case Failure(e) => Future.failed(e)
  }
}
