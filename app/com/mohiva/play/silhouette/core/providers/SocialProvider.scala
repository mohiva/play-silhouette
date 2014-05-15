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

import scala.util.Try
import scala.concurrent.Future
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ SimpleResult, RequestHeader }
import com.mohiva.play.silhouette.core.{ LoginInfo, Provider }
import com.mohiva.play.silhouette.core.services.AuthInfo
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import SocialProfileBuilder._

/**
 * The base interface for all social providers.
 *
 * @tparam A The type of the auth info.
 */
trait SocialProvider[A <: AuthInfo] extends Provider with SocialProfileBuilder[A] {

  /**
   * Authenticates the user and fills the profile information.
   *
   * Returns either a SocialProfile if all went OK or a Result that the controller sends
   * to the browser (e.g.: in the case of OAuth where the user needs to be redirected to
   * the service provider).
   *
   * @param request The request header.
   * @return On success either the social profile or a simple result, otherwise a failure.
   */
  def authenticate()(implicit request: RequestHeader): Future[Either[SimpleResult, Profile]] = {
    doAuth().flatMap(_.fold(
      result => Future.successful(Left(result)),
      authInfo => buildProfile(authInfo).map(profile => Right(profile)).recoverWith {
        case e if !e.isInstanceOf[AuthenticationException] =>
          Future.failed(new AuthenticationException(UnspecifiedProfileError.format(id), e))
      }
    ))
  }

  /**
   * Subclasses need to implement the authentication logic.
   *
   * This method needs to return a auth info object that then gets passed to the buildIdentity method.
   *
   * @param request The request header.
   * @return Either a Result or the auth info from the provider.
   */
  protected def doAuth()(implicit request: RequestHeader): Future[Either[SimpleResult, A]]
}

/**
 * The social profile contains all the data returned from the social providers after authentication.
 *
 * @tparam A The type of the auth info.
 */
trait SocialProfile[A <: AuthInfo] {

  /**
   * Gets the linked login info.
   *
   * @return The linked login info.
   */
  def loginInfo: LoginInfo

  /**
   * Gets the current auth info returned from the provider.
   *
   * @return The current auth info returned from the provider.
   */
  def authInfo: A
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
  type Profile <: SocialProfile[A]

  /**
   * The Json parser signature.
   */
  type JsonParser = (JsValue) => CommonSocialProfile[A]

  /**
   * The parser signature.
   */
  type Parser = (A) => JsonParser

  /**
   * Gets the API URL to retrieve the profile data.
   *
   * @return The API URL to retrieve the profile data.
   */
  protected def profileAPI: String

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
   * @param json The Json from the social provider.
   * @return The social profile from given result.
   */
  protected def parseProfile(parser: JsonParser, json: JsValue): Try[Profile]

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
 * @param authInfo The current auth info returned from the provider.
 * @param firstName Maybe the first name of the authenticated user.
 * @param lastName Maybe the last name of the authenticated user.
 * @param fullName Maybe the full name of the authenticated user.
 * @param email Maybe the email of the authenticated provider.
 * @param avatarURL Maybe the avatar URL of the authenticated provider.
 * @tparam A The auth info type.
 */
case class CommonSocialProfile[A <: AuthInfo](
  loginInfo: LoginInfo,
  authInfo: A,
  firstName: Option[String] = None,
  lastName: Option[String] = None,
  fullName: Option[String] = None,
  email: Option[String] = None,
  avatarURL: Option[String] = None) extends SocialProfile[A]

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
  type Profile = CommonSocialProfile[A]

  /**
   * Parses the social profile with the given Json parser.
   *
   * @param parser The Json parser to parse the most common profile.
   * @param json The Json from the social provider.
   * @return The social profile from given result.
   */
  protected def parseProfile(parser: JsonParser, json: JsValue): Try[Profile] = Try(parser(json))
}
