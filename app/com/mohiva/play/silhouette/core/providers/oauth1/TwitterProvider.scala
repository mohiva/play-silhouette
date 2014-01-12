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
package com.mohiva.play.silhouette.core.providers.oauth1

import play.api.libs.oauth.{RequestToken, OAuthCalculator}
import play.api.mvc.RequestHeader
import play.api.i18n.Lang
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.utils.{HTTPLayer, CacheLayer}
import com.mohiva.play.silhouette.core.providers.{OAuth1Identity, OAuth1Info, OAuth1Settings, OAuth1Provider}
import TwitterProvider._

/**
 * A Twitter OAuth1 Provider.
 *
 * @param settings The provider settings.
 * @param cacheLayer The cache layer implementation.
 * @param httpLayer The HTTP layer implementation.
 * @param identityBuilder The identity builder implementation.
 */
class TwitterProvider[I <: Identity](
    settings: OAuth1Settings,
    cacheLayer: CacheLayer,
    httpLayer: HTTPLayer,
    identityBuilder: IdentityBuilder[TwitterIdentity, I])
  extends OAuth1Provider[I](settings, cacheLayer, httpLayer) {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = Twitter

  /**
   * Builds the identity.
   *
   * @param authInfo The auth info received from the provider.
   * @param request The request header.
   * @param lang The current lang.
   * @return The identity.
   */
  def buildIdentity(authInfo: OAuth1Info)(implicit request: RequestHeader, lang: Lang): Future[I] = {
    val sign = OAuthCalculator(serviceInfo.key, RequestToken(authInfo.token, authInfo.secret))
    httpLayer.url(API).sign(sign).get().map { response =>
      val json = response.json
      val userId = (json \ ID).as[Int]
      val name = (json \ Name).as[String]
      val avatarURL = (json \ ProfileImage).asOpt[String]

      identityBuilder(TwitterIdentity(
        identityID = IdentityID(userId.toString, id),
        fullName = name,
        avatarURL = avatarURL,
        authMethod = authMethod,
        authInfo = authInfo))
    }.recover { case e => throw new AuthenticationException(UnspecifiedProfileError.format(id), e) }
  }
}

/**
 * The companion object.
 */
object TwitterProvider {

  /**
   * The error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] error retrieving profile information"

  /**
   * The LinkedIn constants.
   */
  val Twitter = "twitter"
  val API = "https://api.twitter.com/1.1/account/verify_credentials.json"
  val ID = "id"
  val Name = "name"
  val ProfileImage = "profile_image_url_https"
}

/**
 * The Twitter identity.
 */
case class TwitterIdentity(
  identityID: IdentityID,
  fullName: String,
  avatarURL: Option[String],
  authMethod: AuthenticationMethod,
  authInfo: OAuth1Info) extends OAuth1Identity
