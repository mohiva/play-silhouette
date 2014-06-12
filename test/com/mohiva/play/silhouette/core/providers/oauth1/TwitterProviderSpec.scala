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
package com.mohiva.play.silhouette.core.providers.oauth1

import test.Helper
import java.util.UUID
import play.api.libs.ws.{ WSResponse, WSRequestHolder }
import play.api.test.{ FakeRequest, WithApplication }
import scala.concurrent.Future
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.providers.OAuth1Settings
import com.mohiva.play.silhouette.core.providers.OAuth1Info
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import SocialProfileBuilder._
import OAuth1Provider._
import TwitterProvider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.oauth1.TwitterProvider]] class.
 */
class TwitterProviderSpec extends OAuth1ProviderSpec {

  "The authenticate method" should {
    "fail with AuthenticationException if API returns error" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(oAuthInfo)
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/twitter.error.json")
      httpLayer.url(API) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          215,
          Some("Bad Authentication data")))
      }

      there was one(cacheLayer).remove(cacheID)
    }

    "fail with AuthenticationException if an unexpected error occurred" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(oAuthInfo)
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json throws new RuntimeException("")
      httpLayer.url(API) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }

      there was one(cacheLayer).remove(cacheID)
    }

    "return the social profile" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(oAuthInfo)
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/twitter.success.json")
      httpLayer.url(API) returns requestHolder

      profile(provider.authenticate()) {
        case p =>
          p must be equalTo new CommonSocialProfile(
            loginInfo = LoginInfo(provider.id, "6253282"),
            authInfo = oAuthInfo,
            fullName = Some("Apollonia Vanova"),
            avatarURL = Some("https://pbs.twimg.com/profile_images/1209905677/appolonia_.jpg")
          )
      }

      there was one(cacheLayer).remove(cacheID)
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
    lazy val provider = TwitterProvider(cacheLayer, httpLayer, oAuthService, oAuthSettings)
  }
}
