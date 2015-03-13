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
import com.mohiva.play.silhouette.impl.providers._
import play.api.test.WithApplication

/**
 * Test case for the [[SteamProvider]] class.
 */
class SteamProviderSpec extends OpenIDProviderSpec {

  "The `retrieveProfile` method" should {
    "return the social profile" in new WithApplication with Context {
      profile(provider.retrieveProfile(openIDInfo)) {
        case p => p must be equalTo new CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "http://steamcommunity.com/openid/id/16261495063738643")
        )
      }
    }
  }

  /**
   * Defines the context for the abstract OpenID provider spec.
   *
   * @return The Context to use for the abstract OpenID provider spec.
   */
  override protected def context: OpenIDProviderSpecContext = new Context {}

  /**
   * The context.
   */
  trait Context extends OpenIDProviderSpecContext {

    /**
     * A OpenID info.
     */
    override lazy val openIDInfo = OpenIDInfo("http://steamcommunity.com/openid/id/16261495063738643", Map())

    /**
     * The OpenID settings.
     */
    lazy val openIDSettings = spy(OpenIDSettings(
      providerURL = "https://steamcommunity.com/openid/",
      callbackURL = "http://localhost:9000/authenticate/steam",
      realm = Some("http://localhost:9000")
    ))

    /**
     * The provider to test.
     */
    lazy val provider = SteamProvider(httpLayer, openIDService, openIDSettings)
  }
}
