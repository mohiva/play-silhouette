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
package com.mohiva.play.silhouette.core.providers.oauth2

import test.Helper
import java.util.UUID
import play.api.libs.ws.{ WSResponse, WSRequestHolder }
import play.api.test.{ FakeRequest, WithApplication }
import scala.concurrent.Future
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.exceptions.{ ProfileRetrievalException, AuthenticationException }
import SocialProfileBuilder._
import FacebookProvider._
import OAuth2Provider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.oauth2.FacebookProvider]] class.
 */
class FacebookProviderSpec extends OAuth2ProviderSpec {

  "The `authenticate` method" should {
    "fail with AuthenticationException if OAuth2Info can be build because of an unexpected response" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.body returns ""
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(InvalidResponseFormat.format(provider.id, ""))
      }
    }

    "return the auth info with expires value" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.body returns AccessToken + "=my.access.token&" + Expires + "=1"
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder

      authInfo(provider.authenticate()) {
        case authInfo => authInfo must be equalTo OAuth2Info(
          accessToken = "my.access.token",
          tokenType = None,
          expiresIn = Some(1),
          refreshToken = None)
      }
    }

    "return the auth info without expires value" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.body returns AccessToken + "=my.access.token&"
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder

      authInfo(provider.authenticate()) {
        case authInfo => authInfo must be equalTo OAuth2Info(
          accessToken = "my.access.token",
          tokenType = None,
          expiresIn = None,
          refreshToken = None)
      }
    }
  }

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/oauth2/facebook.error.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          "An active access token must be used to query information about the current user.",
          "OAuthException",
          2500))
      }
    }

    "fail with ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      response.json throws new RuntimeException("")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "return the social profile and the auth info with expires value" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/oauth2/facebook.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case p =>
          p must be equalTo new CommonSocialProfile(
            loginInfo = LoginInfo(provider.id, "134405962728980"),
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            fullName = Some("Apollonia Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://fbcdn-sphotos-g-a.akamaihd.net/hphotos-ak-ash2/t1/36245_155530314499277_2350717_n.jpg?lvh=1")
          )
      }
    }

    "return the social profile and the auth info without expires value" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/oauth2/facebook.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case p =>
          p must be equalTo new CommonSocialProfile(
            loginInfo = LoginInfo(provider.id, "134405962728980"),
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            fullName = Some("Apollonia Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://fbcdn-sphotos-g-a.akamaihd.net/hphotos-ak-ash2/t1/36245_155530314499277_2350717_n.jpg?lvh=1")
          )
      }
    }
  }

  /**
   * Defines the context for the abstract OAuth2 provider spec.
   *
   * @return The Context to use for the abstract OAuth2 provider spec.
   */
  override protected def context: OAuth2ProviderSpecContext = new Context {}

  /**
   * The context.
   */
  trait Context extends OAuth2ProviderSpecContext {

    /**
     * The OAuth2 settings.
     */
    lazy val oAuthSettings = OAuth2Settings(
      authorizationURL = "https://graph.facebook.com/oauth/authorize",
      accessTokenURL = "https://graph.facebook.com/oauth/access_token",
      redirectURL = "https://www.mohiva.com",
      clientID = "my.client.id",
      clientSecret = "my.client.secret",
      scope = Some("email"))

    /**
     * The provider to test.
     */
    lazy val provider = FacebookProvider(cacheLayer, httpLayer, oAuthSettings)
  }
}
