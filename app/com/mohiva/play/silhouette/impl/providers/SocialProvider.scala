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
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.services.AuthInfo
import com.mohiva.play.silhouette.api.{ LoginInfo, Provider }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ RequestHeader, Result }

import scala.concurrent.Future
import scala.util.Try

/**
 * The base interface for all social providers.
 *
 * @tparam A The type of the auth info.
 */
trait SocialProvider[A <: AuthInfo] extends Provider with SocialProfileBuilder[A] {

  /**
   * Authenticates the user and returns the auth information.
   *
   * Returns either a AuthInfo if all went OK or a Result that the controller sends
   * to the browser (e.g.: in the case of OAuth where the user needs to be redirected to
   * the service provider).
   *
   * @param request The request header.
   * @return Either a Result or the AuthInfo from the provider.
   */
  def authenticate()(implicit request: RequestHeader): Future[AuthenticationResult]

  /**
   * Retrieves the user profile for the given auth info.
   *
   * This method can be used to retrieve the profile information for an already authenticated
   * identity.
   *
   * @param authInfo The auth info for which the profile information should be retrieved.
   * @return The profile information for the given auth info.
   */
  def retrieveProfile(authInfo: A): Future[Profile] = {
    buildProfile(authInfo).recoverWith {
      case e if !e.isInstanceOf[ProfileRetrievalException] =>
        Future.failed(new ProfileRetrievalException(UnspecifiedProfileError.format(id), e))
    }
  }
}

/**
 * The result of a request to Provider.authenticate().
 */
sealed trait AuthenticationResult

/**
 * Wraps a redirect response in the authentication flow.
 */
case class AuthenticationOngoing(result: Result) extends AuthenticationResult

/**
 * Means the authentication has been completed, the auth info is available.
 */
case class AuthenticationCompleted[A <: AuthInfo](authInfo: A) extends AuthenticationResult

/**
 * The social profile contains all the data returned from the social providers after authentication.
 */
trait SocialProfile {

  /**
   * Gets the linked login info.
   *
   * @return The linked login info.
   */
  def loginInfo: LoginInfo
}

/**
 * Builds the social profile.
 *
 * @tparam A The type of the auth info.
 */
trait SocialProfileBuilder[A <: AuthInfo] {
  self: SocialProvider[A] =>

  /**
   * The type of the profile.
   */
  type Profile <: SocialProfile

  /**
   * The content type to parse.
   */
  type Content

  /**
   * The parser signature.
   */
  type Parser = (Content) => CommonSocialProfile

  /**
   * Gets the URLs that are needed to retrieve the profile data.
   *
   * Some providers need more than one request to different URLs to query the profile data.
   * So we use a Map here to allow defining multiple URLs.
   *
   * @return The URLs that are needed to retrieve the profile data.
   */
  protected def urls: Map[String, String]

  /**
   * Subclasses need to implement this method to populate the profile information from the service provider.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: A): Future[Profile]

  /**
   * Parses the social profile with the given Json parser.
   *
   * @param parser The Json parser to parse the most common profile.
   * @param content The content returned from the provider.
   * @return The social profile from given result.
   */
  protected def parseProfile(parser: Parser, content: Content): Try[Profile]

  /**
   * Defines the parser which parses the most common profile supported by Silhouette.
   *
   * @return The parser which parses the most common profile supported by Silhouette.
   */
  protected def parser: Parser
}

/**
 * The companion object.
 */
object SocialProfileBuilder {

  /**
   * Some error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information"
}

/**
 * The social profile contains all the data returned from the social providers after authentication.
 *
 * Not every provider returns all the data defined in this class. This is also the representation of the
 * most common profile information provided by the social providers. The data can be used to create a new
 * identity for the first authentication(which is also the registration) or to update an existing identity
 * on every subsequent authentication.
 *
 * @param loginInfo The linked login info.
 * @param firstName Maybe the first name of the authenticated user.
 * @param lastName Maybe the last name of the authenticated user.
 * @param fullName Maybe the full name of the authenticated user.
 * @param email Maybe the email of the authenticated provider.
 * @param avatarURL Maybe the avatar URL of the authenticated provider.
 */
case class CommonSocialProfile(
  loginInfo: LoginInfo,
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  fullName: Option[String] = None,
  email: Option[String] = None,
  avatarURL: Option[String] = None) extends SocialProfile

/**
 * The profile builder for the common social profile.
 *
 * @tparam A The auth info type.
 */
trait CommonSocialProfileBuilder[A <: AuthInfo] {
  self: SocialProfileBuilder[A] =>

  /**
   * The type of the profile.
   */
  type Profile = CommonSocialProfile

  /**
   * Parses the social profile with the given Json parser.
   *
   * @param parser The Json parser to parse the most common profile.
   * @param content The content returned from the provider.
   * @return The social profile from given result.
   */
  protected def parseProfile(parser: Parser, content: Content): Try[Profile] = Try(parser(content))
}
