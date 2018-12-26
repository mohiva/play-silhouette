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
import com.mohiva.play.silhouette.api.util.{ ExtractableRequest, MockWSRequest }
import com.mohiva.play.silhouette.impl.exceptions.{ ProfileRetrievalException, UnexpectedResponseException }
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider._
import play.api.libs.json.Json
import play.api.test.{ FakeRequest, WithApplication }
import test.Helper

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Test case for the [[GoogleProvider]] class.
 */
class GoogleProviderSpec extends OAuth2ProviderSpec {

  "The `withSettings` method" should {
    "create a new instance with customized settings" in new WithApplication with Context {
      val s = provider.withSettings { s =>
        s.copy(accessTokenURL = "new-access-token-url")
      }

      s.settings.accessTokenURL must be equalTo "new-access-token-url"
    }
  }

  "The `authenticate` method" should {
    "fail with UnexpectedResponseException for an unexpected response" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      wsResponse.status returns 401
      wsResponse.body returns "Unauthorized"
      wsRequest.withHttpHeaders(any) returns wsRequest
      wsRequest.post[Map[String, Seq[String]]](any)(any) returns Future.successful(wsResponse)
      httpLayer.url(oAuthSettings.accessTokenURL) returns wsRequest
      stateProvider.unserialize(anyString)(any[ExtractableRequest[String]], any[ExecutionContext]) returns Future.successful(state)
      stateProvider.state(any[ExecutionContext]) returns Future.successful(state)

      failed[UnexpectedResponseException](provider.authenticate()) {
        case e => e.getMessage must startWith(UnexpectedResponse.format(provider.id, "Unauthorized", 401))
      }
    }

    "fail with UnexpectedResponseException if OAuth2Info can be build because of an unexpected response" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      wsResponse.status returns 200
      wsResponse.json returns Json.obj()
      wsRequest.withHttpHeaders(any) returns wsRequest
      wsRequest.post[Map[String, Seq[String]]](any)(any) returns Future.successful(wsResponse)
      httpLayer.url(oAuthSettings.accessTokenURL) returns wsRequest
      stateProvider.unserialize(anyString)(any[ExtractableRequest[String]], any[ExecutionContext]) returns Future.successful(state)
      stateProvider.state(any[ExecutionContext]) returns Future.successful(state)

      failed[UnexpectedResponseException](provider.authenticate()) {
        case e => e.getMessage must startWith(InvalidInfoFormat.format(provider.id, ""))
      }
    }

    "return the auth info" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      wsResponse.status returns 200
      wsResponse.json returns oAuthInfo
      wsRequest.withHttpHeaders(any) returns wsRequest
      wsRequest.post[Map[String, Seq[String]]](any)(any) returns Future.successful(wsResponse)
      httpLayer.url(oAuthSettings.accessTokenURL) returns wsRequest
      stateProvider.unserialize(anyString)(any[ExtractableRequest[String]], any[ExecutionContext]) returns Future.successful(state)
      stateProvider.state(any[ExecutionContext]) returns Future.successful(state)

      authInfo(provider.authenticate())(_ must be equalTo oAuthInfo.as[OAuth2Info])
    }
  }

  "The `authenticate` method with user state" should {
    "return stateful auth info" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      wsResponse.status returns 200
      wsResponse.json returns oAuthInfo
      wsRequest.withHttpHeaders(any) returns wsRequest
      wsRequest.post[Map[String, Seq[String]]](any)(any) returns Future.successful(wsResponse)
      httpLayer.url(oAuthSettings.accessTokenURL) returns wsRequest
      stateProvider.unserialize(anyString)(any[ExtractableRequest[String]], any[ExecutionContext]) returns Future.successful(state)
      stateProvider.state(any[ExecutionContext]) returns Future.successful(state)
      stateProvider.withHandler(any[SocialStateItemHandler]) returns stateProvider
      state.items returns Set(userStateItem)

      statefulAuthInfo(provider.authenticate(userStateItem))(_ must be equalTo stateAuthInfo)
    }
  }

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 401
      wsResponse.json returns Helper.loadJson("providers/oauth2/google.error.json")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          401,
          "Invalid Credentials"))
      }
    }

    "fail with ProfileRetrievalException if API returns missing People API config" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 403
      wsResponse.json returns Helper.loadJson("providers/oauth2/google.error.api.missing.json")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      val apiErrMsg = "People API has not been used in project 1234567890 before or it is disabled. Enable it by visiting https://console.developers.google.com/apis/api/people.googleapis.com/overview?project=1234567890 then retry. If you enabled this API recently, wait a few minutes for the action to propagate to our systems and retry."

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          403,
          apiErrMsg))
      }
    }

    "fail with ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 500
      wsResponse.json throws new RuntimeException("")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "use the overridden API URL" in new WithApplication with Context {
      val url = "https://custom.api.url?access_token=%s"
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      oAuthSettings.apiURL returns Some(url)
      wsResponse.status returns 200
      wsResponse.json returns Helper.loadJson("providers/oauth2/google.success.json")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(url.format("my.access.token")) returns wsRequest

      await(provider.retrieveProfile(oAuthInfo.as[OAuth2Info]))

      there was one(httpLayer).url(url.format("my.access.token"))
    }

    "return the social profile with an email" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 200
      wsResponse.json returns Helper.loadJson("providers/oauth2/google.success.json")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "109476598527568979481"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          fullName = Some("Apollonia Vanova"),
          email = Some("apollonia.vanova@watchmen.com"),
          avatarURL = Some("https://lh6.googleusercontent.com/-m34A6I77dJU/ASASAASADAAI/AVABAAAAAJk/5cg1hcjo_4s/photo.jpg?sz=50")
        )
      }
    }

    "return the social profile without an email" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 200
      wsResponse.json returns Helper.loadJson("providers/oauth2/google.without.email.json")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "109476598527568979481"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          fullName = Some("Apollonia Vanova"),
          email = None,
          avatarURL = Some("https://lh6.googleusercontent.com/-m34A6I77dJU/ASASAASADAAI/AVABAAAAAJk/5cg1hcjo_4s/photo.jpg?sz=50")
        )
      }
    }
    "return the social profile with an avatar url" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 200
      wsResponse.json returns Helper.loadJson("providers/oauth2/google.img.non-default.json")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "109476598527568979481"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          fullName = Some("Apollonia Vanova"),
          email = Some("apollonia.vanova@watchmen.com"),
          avatarURL = Some("https://lh6.googleusercontent.com/-m34A6I77dJU/ASASAASADAAI/AVABAAAAAJk/5cg1hcjo_4s/photo.jpg?sz=50")
        )
      }
    }
    "return the social profile without an avatar url" in new WithApplication with Context {
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 200
      wsResponse.json returns Helper.loadJson("providers/oauth2/google.img.default.json")
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "109476598527568979481"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          fullName = Some("Apollonia Vanova"),
          email = Some("apollonia.vanova@watchmen.com"),
          avatarURL = None
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
    override lazy val oAuthSettings = spy(OAuth2Settings(
      authorizationURL = Some("https://accounts.google.com/o/oauth2/auth"),
      accessTokenURL = "https://accounts.google.com/o/oauth2/token",
      redirectURL = Some("https://www.mohiva.com"),
      clientID = "my.client.id",
      clientSecret = "my.client.secret",
      scope = Some("profile,email")))

    /**
     * The OAuth2 info returned by Google.
     *
     * @see https://developers.google.com/accounts/docs/OAuth2Login#sendauthrequest
     */
    override lazy val oAuthInfo = Helper.loadJson("providers/oauth2/google.access.token.json")

    /**
     * The stateful auth info.
     */
    override lazy val stateAuthInfo = StatefulAuthInfo(oAuthInfo.as[OAuth2Info], userStateItem)

    /**
     * The provider to test.
     */
    lazy val provider = new GoogleProvider(httpLayer, stateProvider, oAuthSettings)
  }
}
