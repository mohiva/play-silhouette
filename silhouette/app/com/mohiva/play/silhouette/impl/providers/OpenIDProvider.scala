/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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

import java.net.URLEncoder

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.exceptions.UnexpectedResponseException
import com.mohiva.play.silhouette.impl.providers.OpenIDProvider._
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Base implementation for all OpenID providers.
 */
trait OpenIDProvider extends SocialProvider with Logger {

  /**
   * The type of the auth info.
   */
  type A = OpenIDInfo

  /**
   * The settings type.
   */
  type Settings = OpenIDSettings

  /**
   * The OpenID service implementation.
   */
  protected val service: OpenIDService

  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return Either a Result or the auth info from the provider.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, OpenIDInfo]] = {
    request.extractString(Mode) match {
      // Tries to verify the user after the provider has redirected back to the application
      case Some(_) => service.verifiedID.map(info => Right(info)).recover {
        case e => throw new UnexpectedResponseException(ErrorVerification.format(id, e.getMessage), e)
      }
      // Starts the OpenID authentication process
      case None =>
        // Either we get the openID from request or we use the provider ID to retrieve the redirect URL
        val openID = request.extractString(OpenID).getOrElse(settings.providerURL)
        service.redirectURL(openID, resolveCallbackURL(settings.callbackURL)).map { url =>
          val redirect = Results.Redirect(fix3749(url))
          logger.debug("[Silhouette][%s] Redirecting to: %s".format(id, url))
          Left(redirect)
        }.recover {
          case e => throw new UnexpectedResponseException(ErrorRedirectURL.format(id, e.getMessage), e)
        }
    }
  }

  /**
   * A temporary fix for: https://github.com/playframework/playframework/pull/3749
   *
   * @see https://github.com/playframework/playframework/issues/3740
   * @see http://stackoverflow.com/questions/22041522/steam-openid-and-play-framework
   * @param url The URL to fix.
   * @param request The request.
   * @tparam B The type of the request body.
   * @return The fixed URL.
   */
  def fix3749[B](url: String)(implicit request: ExtractableRequest[B]) = {
    if (request.extractString(OpenID).isDefined) {
      // We've found a non-unique ID so this bug doesn't affect us
      url
    } else {
      // We use "OpenID Provider driven identifier selection", so this bug affects us
      val search = URLEncoder.encode(settings.providerURL, "UTF-8")
      val replace = URLEncoder.encode("http://specs.openid.net/auth/2.0/identifier_select", "UTF-8")
      url
        .replace("openid.claimed_id=" + search, "openid.claimed_id=" + replace)
        .replace("openid.identity=" + search, "openid.identity=" + replace)
    }
  }
}

/**
 * The OpenIDProvider companion object.
 */
object OpenIDProvider {

  /**
   * The error messages.
   */
  val ErrorVerification = "[Silhouette][%s] Error verifying the ID: %s"
  val ErrorRedirectURL = "[Silhouette][%s] Error retrieving the redirect URL: %s"

  /**
   * The OpenID constants.
   */
  val Mode = "openid.mode"
  val OpenID = "openID"
}

/**
 * The OpenID service trait.
 */
trait OpenIDService {

  /**
   * Retrieve the URL where the user should be redirected to start the OpenID authentication process.
   *
   * @param openID The OpenID to use for authentication.
   * @param resolvedCallbackURL The full callback URL to the application after a successful authentication.
   * @param ec The execution context to handle the asynchronous operations.
   * @return The redirect URL where the user should be redirected to start the OpenID authentication process.
   */
  def redirectURL(openID: String, resolvedCallbackURL: String)(implicit ec: ExecutionContext): Future[String]

  /**
   * From a request corresponding to the callback from the OpenID server, check the identity of the current user.
   *
   * @param request The current request.
   * @param ec The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return A OpenIDInfo in case of success, Exception otherwise.
   */
  def verifiedID[B](implicit request: Request[B], ec: ExecutionContext): Future[OpenIDInfo]
}

/**
 * The OpenID settings.
 *
 * @param providerURL The OpenID provider URL used if no openID was given. @see https://willnorris.com/2009/07/openid-directed-identity-identifier-select
 * @param callbackURL The callback URL to the application after a successful authentication on the OpenID provider.
 *                    The URL can be a relative path which will be resolved against the current request's host.
 * @param axRequired Required attributes to return from the provider after a successful authentication.
 * @param axOptional Optional attributes to return from the provider after a successful authentication.
 * @param realm An URL pattern that represents the part of URL-space for which an OpenID Authentication request is valid.
 */
case class OpenIDSettings(
  providerURL: String,
  callbackURL: String,
  axRequired: Seq[(String, String)] = Seq.empty,
  axOptional: Seq[(String, String)] = Seq.empty,
  realm: Option[String] = None)

/**
 * The OpenID details.
 *
 * @param id The openID.
 * @param attributes The attributes returned from the provider.
 */
case class OpenIDInfo(id: String, attributes: Map[String, String]) extends AuthInfo
