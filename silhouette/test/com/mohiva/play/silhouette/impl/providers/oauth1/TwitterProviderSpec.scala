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
package com.mohiva.play.silhouette.impl.providers.oauth1

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider._
import play.api.libs.ws.{ WSRequestHolder, WSResponse }
import play.api.test.WithApplication
import test.Helper

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider]] class.
 */
class TwitterProviderSpec extends OAuth1ProviderSpec {

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/twitter.error.json")
      httpLayer.url(API) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo)) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          215,
          Some("Bad Authentication data")))
      }
    }

    "fail with ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json throws new RuntimeException("")
      httpLayer.url(API) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo)) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "return the social profile" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/twitter.success.json")
      httpLayer.url(API) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo)) {
        case p =>
          p must be equalTo new CommonSocialProfile(
            loginInfo = LoginInfo(provider.id, "6253282"),
            fullName = Some("Apollonia Vanova"),
            avatarURL = Some("https://pbs.twimg.com/profile_images/1209905677/appolonia_.jpg")
          )
      }
    }
  }

  /**
   * Defines the context for the abstract OAuth1 provider spec.
   *
   * @return The Context to use for the abstract OAuth1 provider spec.
   */
  override protected def context: OAuth1ProviderSpecContext = new Context {}

  /**
   * The context.
   */
  trait Context extends OAuth1ProviderSpecContext {

    /**
     * The OAuth1 settings.
     */
    lazy val oAuthSettings = OAuth1Settings(
      requestTokenURL = "https://twitter.com/oauth/request_token",
      accessTokenURL = "https://twitter.com/oauth/access_token",
      authorizationURL = "https://twitter.com/oauth/authenticate",
      callbackURL = "https://www.mohiva.com",
      consumerKey = "my.consumer.key",
      consumerSecret = "my.consumer.secret")

    /**
     * The provider to test.
     */
    lazy val provider = TwitterProvider(httpLayer, oAuthService, oAuthTokenSecretProvider, oAuthSettings)
  }
}
