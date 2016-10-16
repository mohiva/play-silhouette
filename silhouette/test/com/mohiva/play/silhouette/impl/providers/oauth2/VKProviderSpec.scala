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
package com.mohiva.play.silhouette.impl.providers.oauth2

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.exceptions.{ ProfileRetrievalException, UnexpectedResponseException }
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.VKProvider._
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.ws.{ WSRequest, WSResponse }
import play.api.test.{ FakeRequest, WithApplication }
import test.Helper

import scala.concurrent.Future

/**
 * Test case for the [[VKProvider]] class.
 */
class VKProviderSpec extends OAuth2ProviderSpec {

  "The `withSettings` method" should {
    "create a new instance with customized settings" in new WithApplication with Context {
      val s = provider.withSettings { s =>
        s.copy(accessTokenURL = "new-access-token-url")
      }

      s.settings.accessTokenURL must be equalTo "new-access-token-url"
    }
  }

  "The `authenticate` method" should {
    "fail with UnexpectedResponseException if OAuth2Info can be build because of an unexpected response" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      response.json returns Json.obj()
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any) returns Future.successful(response)
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      stateProvider.validate(any, any) returns Future.successful(state)

      failed[UnexpectedResponseException](provider.authenticate()) {
        case e => e.getMessage must startWith(InvalidInfoFormat.format(provider.id, ""))
      }
    }

    "return the auth info" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      response.json returns oAuthInfo
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any) returns Future.successful(response)
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      stateProvider.validate(any, any) returns Future.successful(state)

      authInfo(provider.authenticate())(_ must be equalTo oAuthInfo.as[OAuth2Info])
    }
  }

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/oauth2/vk.error.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          10,
          "Internal server error: could not get application"))
      }
    }

    "fail with ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      response.json throws new RuntimeException("")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "use the overridden API URL" in new WithApplication with Context {
      val url = "https://custom.api.url?access_token=%s"
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      oAuthSettings.apiURL returns Some(url)
      response.json returns Helper.loadJson("providers/oauth2/vk.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(url.format("my.access.token")) returns requestHolder

      await(provider.retrieveProfile(oAuthInfo.as[OAuth2Info]))

      there was one(httpLayer).url(url.format("my.access.token"))
    }

    "return the social profile with email" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/oauth2/vk.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "66748"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          email = Some("apollonia.vanova@watchmen.com"),
          avatarURL = Some("http://vk.com/images/camera_b.gif")
        )
      }
    }

    "return the social profile without email" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/oauth2/vk.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.retrieveProfile((oAuthInfo.as[JsObject] - "email").as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "66748"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          email = None,
          avatarURL = Some("http://vk.com/images/camera_b.gif")
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
    lazy val oAuthSettings = spy(OAuth2Settings(
      authorizationURL = Some("http://oauth.vk.com/authorize"),
      accessTokenURL = "https://oauth.vk.com/access_token",
      redirectURL = "https://www.mohiva.com",
      clientID = "my.client.id",
      clientSecret = "my.client.secret",
      scope = Some("email")))

    /**
     * The OAuth2 info returned by VK.
     *
     * @see http://vk.com/dev/auth_sites
     */
    override lazy val oAuthInfo = Helper.loadJson("providers/oauth2/vk.access.token.json")

    /**
     * The provider to test.
     */
    lazy val provider = new VKProvider(httpLayer, stateProvider, oAuthSettings)
  }
}
