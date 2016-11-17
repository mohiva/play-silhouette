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
import com.mohiva.play.silhouette.impl.providers.oauth2.LinkedInProvider._
import play.api.libs.json.Json
import play.api.libs.ws.{ WSRequest, WSResponse }
import play.api.test.{ FakeRequest, WithApplication }
import test.Helper

import scala.concurrent.Future

/**
 * Test case for the [[LinkedInProvider]] class.
 */
class LinkedInProviderSpec extends OAuth2ProviderSpec {

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
      response.json returns Helper.loadJson("providers/oauth2/linkedin.error.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          0,
          Some("Unknown authentication scheme"),
          Some("LY860UAC5U"),
          Some(401),
          Some(1390421660154L)))
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
      response.json returns Helper.loadJson("providers/oauth2/linkedin.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(url.format("my.access.token")) returns requestHolder

      await(provider.retrieveProfile(oAuthInfo.as[OAuth2Info]))

      there was one(httpLayer).url(url.format("my.access.token"))
    }

    "return the social profile" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/oauth2/linkedin.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "NhZXBl_O6f"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          fullName = Some("Apollonia Vanova"),
          email = Some("apollonia.vanova@watchmen.com"),
          avatarURL = Some("http://media.linkedin.com/mpr/mprx/0_fsPnURNRhLhk_Ue2fjKLUZkB2FL6TOe2S4bdUZz61GA9Ysxu_y_sz4THGW5JGJWhaMleN0F61-Dg")
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
    override lazy val oAuthSettings = spy(OAuth2Settings(authorizationURL = Some("https://www.linkedin.com/uas/oauth2/authorization"), accessTokenURL = "https://www.linkedin.com/uas/oauth2/accessToken", redirectURL = Some("https://www.mohiva.com"), clientID = "my.client.id", clientSecret = "my.client.secret", scope = None))

    /**
     * The OAuth2 info returned by LinkedIn.
     *
     * @see https://developer.linkedin.com/documents/authentication
     */
    override lazy val oAuthInfo = Helper.loadJson("providers/oauth2/linkedin.access.token.json")

    /**
     * The provider to test.
     */
    lazy val provider = new LinkedInProvider(httpLayer, stateProvider, oAuthSettings)
  }
}
