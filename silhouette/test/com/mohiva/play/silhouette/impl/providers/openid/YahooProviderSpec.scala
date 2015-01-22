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
package com.mohiva.play.silhouette.impl.providers.openid

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers._
import play.api.test.WithApplication

/**
 * Test case for the [[YahooProvider]] class.
 */
class YahooProviderSpec extends OpenIDProviderSpec {

  "The `retrieveProfile` method" should {
    "return the social profile" in new WithApplication with Context {
      profile(provider.retrieveProfile(openIDInfo)) {
        case p =>
          p must be equalTo new CommonSocialProfile(
            loginInfo = LoginInfo(provider.id, "https://me.yahoo.com/a/Xs6hPjazdrMvmbn4jhQjkjkhcasdGdsKajq9we"),
            fullName = Some("Apollonia Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://s.yimg.com/dh/ap/social/profile/profile_b48.png")
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
    override lazy val openIDInfo = OpenIDInfo("https://me.yahoo.com/a/Xs6hPjazdrMvmbn4jhQjkjkhcasdGdsKajq9we", Map(
      "fullname" -> "Apollonia Vanova",
      "email" -> "apollonia.vanova@watchmen.com",
      "image" -> "https://s.yimg.com/dh/ap/social/profile/profile_b48.png"
    ))

    /**
     * The OpenID settings.
     */
    lazy val openIDSettings = OpenIDSettings(
      providerURL = "https://me.yahoo.com/",
      callbackURL = "http://localhost:9000/authenticate/yahoo",
      axRequired = Seq(
        "fullname" -> "http://axschema.org/namePerson",
        "email" -> "http://axschema.org/contact/email",
        "image" -> "http://axschema.org/media/image/default"
      ),
      realm = Some("http://localhost:9000")
    )

    /**
     * The provider to test.
     */
    lazy val provider = YahooProvider(httpLayer, openIDService, openIDSettings)
  }
}
