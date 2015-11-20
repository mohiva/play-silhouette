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
 * Base Yahoo OpenID Provider.
 *
 * @see https://developer.yahoo.com/openid/index.html
 */
trait BaseYahooProvider extends OpenIDProvider {

  /**
   * The content type to parse a profile from.
   */
  override type Content = OpenIDInfo

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  override val id = ID

  /**
   * Defines the URLs that are needed to retrieve the profile data.
   */
  override protected val urls = Map[String, String]()

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OpenIDInfo): Future[Profile] = {
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
  override def parse(info: OpenIDInfo) = Future.successful {
    CommonSocialProfile(
      loginInfo = LoginInfo(ID, info.id),
      fullName = info.attributes.get("fullname"),
      email = info.attributes.get("email"),
      avatarURL = info.attributes.get("image")
    )
  }
}

/**
 * The Yahoo OAuth2 Provider.
 *
 * @param httpLayer The HTTP layer implementation.
 * @param service The OpenID service implementation.
 * @param settings The OpenID provider settings.
 */
class YahooProvider(
  protected val httpLayer: HTTPLayer,
  protected val service: OpenIDService,
  val settings: OpenIDSettings)
  extends BaseYahooProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = YahooProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new YahooProfileParser

  /**
   * Gets a provider initialized with a new settings object.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of the provider initialized with new settings.
   */
  override def withSettings(f: (Settings) => Settings) = new YahooProvider(httpLayer, service, f(settings))
}

/**
 * The companion object.
 */
object YahooProvider {

  /**
   * The Yahoo constants.
   */
  val ID = "yahoo"
}
