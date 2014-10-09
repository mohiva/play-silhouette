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
import scala.concurrent.Future
import play.api.libs.ws.{ WSResponse, WSRequestHolder }
import play.api.test.{ FakeRequest, WithApplication }
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.providers.OAuth1Settings
import com.mohiva.play.silhouette.core.exceptions.ProfileRetrievalException
import SocialProfileBuilder._
import XingProvider._
import OAuth1Provider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.oauth1.XingProvider]] class.
 */
class XingProviderSpec extends OAuth1ProviderSpec {

  "The `authenticate` method" should {
    "return the auth info" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.find[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(oAuthInfo)

      authInfo(provider.authenticate()) {
        case authInfo => authInfo must be equalTo oAuthInfo
      }

      there was one(cacheLayer).remove(cacheID)
    }
  }

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/xing.error.json")
      httpLayer.url(API) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo)) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          "INVALID_PARAMETERS",
          "Invalid parameters (Limit must be a non-negative number.)"))
      }
    }

    "throw ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
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
      response.json returns Helper.loadJson("providers/oauth1/xing.success.json")
      httpLayer.url(API) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo)) {
        case p =>
          p must be equalTo new CommonSocialProfile(
            loginInfo = LoginInfo(provider.id, "1235468792"),
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            fullName = Some("Apollonia Vanova"),
            avatarURL = Some("http://www.xing.com/img/users/e/3/d/f94ef165a.123456,1.140x185.jpg"),
            email = Some("apollonia.vanova@watchmen.com")
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
      requestTokenURL = "https://api.xing.com/v1/request_token",
      accessTokenURL = "https://api.xing.com/v1/access_token",
      authorizationURL = "https://api.xing.com/v1/authorize",
      callbackURL = "https://www.mohiva.com",
      consumerKey = "my.consumer.key",
      consumerSecret = "my.consumer.secret")

    /**
     * The provider to test.
     */
    lazy val provider = XingProvider(cacheLayer, httpLayer, oAuthService, oAuthSettings)
  }
}
