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
package com.mohiva.play.silhouette.impl.providers.openid

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.openid.YahooProvider._

import scala.concurrent.Future

/**
 * A Yahoo OpenID Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param service The OpenID service implementation.
 * @param settings The OpenID provider settings.
 *
 * @see https://developer.yahoo.com/openid/index.html
 */
abstract class YahooProvider(httpLayer: HTTPLayer, service: OpenIDService, settings: OpenIDSettings)
  extends OpenIDProvider(httpLayer, service, settings) {

  /**
   * The content type to parse a profile from.
   */
  type Content = OpenIDInfo

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  protected val urls = Map[String, String]()

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  protected def buildProfile(authInfo: OpenIDInfo): Future[Profile] = {
    profileParser.parse(authInfo)
  }
}

/**
 * The profile parser for the common social profile.
 */
class YahooProfileParser extends SocialProfileParser[OpenIDInfo, CommonSocialProfile] {

  /**
   * Parses the social profile.
   *
   * @param info The auth info received from the provider.
   * @return The social profile from given result.
   */
  def parse(info: OpenIDInfo) = Future.successful {
    CommonSocialProfile(
      loginInfo = LoginInfo(ID, info.id),
      fullName = info.attributes.get("fullname"),
      email = info.attributes.get("email"),
      avatarURL = info.attributes.get("image")
    )
  }
}

/**
 * The profile builder for the common social profile.
 */
trait YahooProfileBuilder extends CommonSocialProfileBuilder {
  self: YahooProvider =>

  /**
   * The profile parser implementation.
   */
  val profileParser = new YahooProfileParser
}

/**
 * The companion object.
 */
object YahooProvider {

  /**
   * The Yahoo constants.
   */
  val ID = "yahoo"

  /**
   * Creates an instance of the provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param service The OpenID service implementation.
   * @param settings The OpenID provider settings.
   * @return An instance of this provider.
   */
  def apply(httpLayer: HTTPLayer, service: OpenIDService, settings: OpenIDSettings) = {
    new YahooProvider(httpLayer, service, settings) with YahooProfileBuilder
  }
}
