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
package com.mohiva.play.silhouette.core.providers.oauth2

import play.api.libs.json.JsObject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{ HTTPLayer, CacheLayer }
import com.mohiva.play.silhouette.core.providers.{ SocialProfile, OAuth2Info, OAuth2Settings, OAuth2Provider }
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import GoogleProvider._
import OAuth2Provider._

/**
 * A Google OAuth2 Provider.
 *
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param settings The provider settings.
 * @see https://developers.google.com/+/api/auth-migration#timetable
 * @see https://developers.google.com/+/api/auth-migration#oauth2login
 * @see https://developers.google.com/accounts/docs/OAuth2Login
 * @see https://developers.google.com/+/api/latest/people
 */
class GoogleProvider(
  cacheLayer: CacheLayer,
  httpLayer: HTTPLayer,
  settings: OAuth2Settings)
    extends OAuth2Provider(settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Google

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OAuth2Info): Future[SocialProfile[OAuth2Info]] = {
    httpLayer.url(API.format(authInfo.accessToken)).get().map { response =>
      val json = response.json
      (json \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val errorCode = (error \ Code).as[Int]
          val errorMsg = (error \ Message).as[String]

          throw new AuthenticationException(SpecifiedProfileError.format(id, errorCode, errorMsg))
        case _ =>
          val userID = (json \ ID).as[String]
          val firstName = (json \ Name \ GivenName).asOpt[String]
          val lastName = (json \ Name \ FamilyName).asOpt[String]
          val fullName = (json \ DisplayName).asOpt[String]
          val avatarURL = (json \ Image \ URL).asOpt[String]

          // https://developers.google.com/+/api/latest/people#emails.type
          val emailIndex = (json \ Emails \\ Type).indexWhere(_.as[String] == Account)
          val emailValue = if ((json \ Emails \\ Value).isDefinedAt(emailIndex)) {
            (json \ Emails \\ Value)(emailIndex).asOpt[String]
          } else {
            None
          }

          SocialProfile(
            loginInfo = LoginInfo(id, userID),
            authInfo = authInfo,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            avatarURL = avatarURL,
            email = emailValue)
      }
    }.recover {
      case e if !e.isInstanceOf[AuthenticationException] =>
        throw new AuthenticationException(UnspecifiedProfileError.format(id), e)
    }
  }
}

/**
 * The companion object.
 */
object GoogleProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information"
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, message: %s"

  /**
   * The Google constants.
   */
  val Google = "google"
  val API = "https://www.googleapis.com/plus/v1/people/me?access_token=%s"
  val Message = "message"
  val ID = "id"
  val Name = "name"
  val GivenName = "givenName"
  val FamilyName = "familyName"
  val DisplayName = "displayName"
  val Image = "image"
  val URL = "url"
  val Emails = "emails"
  val Value = "value"
  val Type = "type"
  val Account = "account"
}
