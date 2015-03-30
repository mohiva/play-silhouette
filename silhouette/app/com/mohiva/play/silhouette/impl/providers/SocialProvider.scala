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

import java.net.URI

import com.mohiva.play.silhouette.api.services.AuthInfo
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.api.{ LoginInfo, Provider }
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder._
import org.apache.commons.lang3.reflect.TypeUtils
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{ RequestHeader, Result }

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * The base interface for all social providers.
 */
trait SocialProvider extends Provider with SocialProfileBuilder {

  /**
   * The type of the auth info.
   */
  type A <: AuthInfo

  /**
   * The settings type.
   */
  type Settings

  /**
   * Gets the provider settings.
   *
   * @return The provider settings.
   */
  def settings: Settings

  /**
   * Authenticates the user and returns the auth information.
   *
   * Returns either a AuthInfo if all went OK or a Result that the controller sends
   * to the browser (e.g.: in the case of OAuth where the user needs to be redirected to
   * the service provider).
   *
   * @param request The request.
   * @return Either a Result or the AuthInfo from the provider.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Either[Result, A]]

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

  /**
   * Resolves the url to be absolute relative to the request.
   *
   * This will pass the url through if its already absolute.
   *
   * @param url The url to resolve.
   * @param request The current request.
   * @return The absolute url.
   */
  protected def resolveCallbackURL(url: String)(implicit request: RequestHeader): String = URI.create(url) match {
    case uri if uri.isAbsolute => url
    case uri =>
      val scheme = if (request.secure) "https://" else "http://"
      URI.create(scheme + request.host + request.path).resolve(uri).toString
  }
}

/**
 * A registry that holds and provides access to all social provider implementations.
 *
 * @param providers The list of social providers.
 */
case class SocialProviderRegistry(providers: Seq[SocialProvider]) {

  /**
   * Gets a specific provider by its type.
   *
   * @tparam T The type of the provider.
   * @return Some specific provider type or None if no provider for the given type could be found.
   */
  def get[T: ClassTag]: Option[T] = {
    providers.find(p => TypeUtils.isInstance(p, implicitly[ClassTag[T]].runtimeClass)).map(_.asInstanceOf[T])
  }

  /**
   * Gets a specific provider by its ID.
   *
   * @param id The ID of the provider to return.
   * @return Some social provider or None if no provider for the given ID could be found.
   */
  def get(id: String): Option[SocialProvider] = providers.find(_.id == id)
}

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
 * Parses a social profile.
 *
 * A parser transforms the content returned from the provider into a social profile instance. Parsers can
 * be reused by other parsers to avoid duplicating code.
 *
 * @tparam C The content type to parse a profile from.
 * @tparam P The type of the profile to parse to.
 */
trait SocialProfileParser[C, P <: SocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param content The content returned from the provider.
   * @return The social profile from given result.
   */
  def parse(content: C): Future[P]
}

/**
 * Builds the social profile.
 */
trait SocialProfileBuilder {
  self: SocialProvider =>

  /**
   * The content type to parse a profile from.
   */
  type Content

  /**
   * The type of the profile a profile builder is responsible for.
   */
  type Profile <: SocialProfile

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
   * Returns the profile parser implementation.
   *
   * @return The profile parser implementation.
   */
  protected def profileParser: SocialProfileParser[Content, Profile]
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
 */
trait CommonSocialProfileBuilder {
  self: SocialProfileBuilder =>

  /**
   * The type of the profile a profile builder is responsible for.
   */
  type Profile = CommonSocialProfile
}
