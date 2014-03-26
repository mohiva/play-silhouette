/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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

import scala.util.{ Failure, Success, Try }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.{ SimpleResult, RequestHeader }
import com.mohiva.play.silhouette.core.{ LoginInfo, Provider }
import com.mohiva.play.silhouette.core.services.{ AuthInfo, AuthInfoService }
import com.mohiva.play.silhouette.core.exceptions.SilhouetteException

/**
 * The base interface for all social providers.
 *
 * @tparam A The type of the auth info.
 */
trait SocialProvider[A <: AuthInfo] extends Provider {

  /**
   * Authenticates the user and fills the profile information.
   *
   * Returns either a SocialProfile if all went OK or a Result that the controller sends to the
   * browser (e.g.: in the case of OAuth where the user needs to be redirected to
   * the service provider).
   *
   * @param request The request header.
   * @return On success either the social profile or a simple result, otherwise a failure.
   */
  def authenticate()(implicit request: RequestHeader): Future[Try[Either[SimpleResult, SocialProfile]]] = {
    (for {
      auth <- doAuth()
      either <- auth.asFuture
      result <- either.fold(
        result => Future.successful(Success(Left(result))),
        authInfo => for {
          maybeProfile <- buildProfile(authInfo)
          profile <- maybeProfile.asFuture
        } yield {
          authInfoService.save(profile.loginInfo, authInfo)
          Success(Right(profile))
        })
    } yield result).recover { case e: SilhouetteException => Failure(e) }
  }

  /**
   * Subclasses need to implement the authentication logic.
   *
   * This method needs to return a auth info object that then gets passed to the buildIdentity method.
   *
   * @param request The request header.
   * @return Either a Result or the auth info from the provider.
   */
  protected def doAuth()(implicit request: RequestHeader): Future[Try[Either[SimpleResult, A]]]

  /**
   * Subclasses need to implement this method to populate the profile information from the service provider.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: A): Future[Try[SocialProfile]]

  /**
   * Gets the auth info implementation.
   *
   * @return The auth info implementation.
   */
  protected def authInfoService: AuthInfoService
}

/**
 * The social profile contains all the data returned from the social providers after authentication.
 *
 * Not every provider returns all the data defined in this class. This is also the representation of the
 * most common features provided by the social providers. The data can be used to create a new identity
 * for the first authentication(which is also the registration) or to update an existing identity on every
 * subsequent authentication.
 *
 * @param firstName Maybe the first name of the authenticated user.
 * @param lastName Maybe the last name of the authenticated user.
 * @param fullName Maybe the full name of the authenticated user.
 * @param email Maybe the email of the authenticated provider.
 * @param avatarURL Maybe the avatar URL of the authenticated provider.
 */
case class SocialProfile(
  loginInfo: LoginInfo,
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  fullName: Option[String] = None,
  email: Option[String] = None,
  avatarURL: Option[String] = None)
