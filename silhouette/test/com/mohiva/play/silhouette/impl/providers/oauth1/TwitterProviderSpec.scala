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
import com.mohiva.play.silhouette.api.util.MockWSRequest
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider._
import play.api.test.WithApplication
import test.Helper

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.oauth1.TwitterProvider]] class.
 */
class TwitterProviderSpec extends OAuth1ProviderSpec {

  "The `withSettings` method" should {
    "create a new instance with customized settings" in new WithApplication with Context {
      val overrideSettingsFunction: OAuth1Settings => OAuth1Settings = { s =>
        s.copy("new-request-token-url")
      }
      val s = provider.withSettings(overrideSettingsFunction)

      s.settings.requestTokenURL must be equalTo "new-request-token-url"
      there was one(oAuthService).withSettings(overrideSettingsFunction)
    }
  }

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsRequest.sign(any) returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      wsResponse.json returns Helper.loadJson("providers/oauth1/twitter.error.json")
      httpLayer.url(API) returns wsRequest

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo)) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          215,
          Some("Bad Authentication data")))
      }
    }

    "fail with ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsRequest.sign(any) returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      wsResponse.json throws new RuntimeException("")
      httpLayer.url(API) returns wsRequest

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo)) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "use the overridden API URL" in new WithApplication with Context {
      val url = "https://custom.api.url"
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      oAuthSettings.apiURL returns Some(url)
      wsRequest.sign(any) returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      wsResponse.json returns Helper.loadJson("providers/oauth1/twitter.with.email.json")
      httpLayer.url(url) returns wsRequest

      await(provider.retrieveProfile(oAuthInfo))

      there was one(httpLayer).url(url)
    }

    "return the social profile" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsRequest.sign(any) returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      wsResponse.json returns Helper.loadJson("providers/oauth1/twitter.success.json")
      httpLayer.url(API) returns wsRequest

      profile(provider.retrieveProfile(oAuthInfo)) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "6253282"),
          fullName = Some("Apollonia Vanova"),
          avatarURL = Some("https://pbs.twimg.com/profile_images/1209905677/appolonia_.jpg")
        )
      }
    }

    "return the social profile with email" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsRequest.sign(any) returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      wsResponse.json returns Helper.loadJson("providers/oauth1/twitter.with.email.json")
      httpLayer.url(API) returns wsRequest

      profile(provider.retrieveProfile(oAuthInfo)) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "6253282"),
          fullName = Some("Apollonia Vanova"),
          email = Some("apollonia.vanova@watchmen.com"),
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
    override lazy val oAuthSettings = spy(OAuth1Settings(
      requestTokenURL = "https://twitter.com/oauth/request_token",
      accessTokenURL = "https://twitter.com/oauth/access_token",
      authorizationURL = "https://twitter.com/oauth/authenticate",
      callbackURL = "https://www.mohiva.com",
      consumerKey = "my.consumer.key",
      consumerSecret = "my.consumer.secret"
    ))

    /**
     * The provider to test.
     */
    lazy val provider = new TwitterProvider(httpLayer, oAuthService, oAuthTokenSecretProvider, oAuthSettings)
  }
}
