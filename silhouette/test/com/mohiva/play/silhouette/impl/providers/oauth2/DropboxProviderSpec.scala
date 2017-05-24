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
import com.mohiva.play.silhouette.impl.providers.oauth2.DropboxProvider._
import play.api.libs.json.Json
import play.api.test.{ FakeRequest, WithApplication }
import test.Helper

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Test case for the [[DropboxProvider]] class.
 */
class DropboxProviderSpec extends OAuth2ProviderSpec {

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
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
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
      val authInfo = oAuthInfo.as[OAuth2Info]
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.json returns Helper.loadJson("providers/oauth2/dropbox.error.json")
      wsResponse.status returns 401
      wsRequest.withHttpHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}") returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      failed[ProfileRetrievalException](provider.retrieveProfile(authInfo)) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          "Invalid OAuth request.",
          401))
      }
    }

    "fail with ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
      val authInfo = oAuthInfo.as[OAuth2Info]
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.json throws new RuntimeException("")
      wsRequest.withHttpHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}") returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      failed[ProfileRetrievalException](provider.retrieveProfile(authInfo)) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "use the overridden API URL" in new WithApplication with Context {
      val url = "https://custom.api.url"
      val authInfo = oAuthInfo.as[OAuth2Info]
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      oAuthSettings.apiURL returns Some(url)
      wsResponse.status returns 200
      wsResponse.json returns Helper.loadJson("providers/oauth2/dropbox.success.json")
      wsRequest.withHttpHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}") returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(url) returns wsRequest

      await(provider.retrieveProfile(authInfo))

      there was one(httpLayer).url(url)
    }

    "return the social profile" in new WithApplication with Context {
      val authInfo = oAuthInfo.as[OAuth2Info]
      val wsRequest = mock[MockWSRequest]
      val wsResponse = mock[MockWSRequest#Response]
      wsResponse.status returns 200
      wsResponse.json returns Helper.loadJson("providers/oauth2/dropbox.success.json")
      wsRequest.withHttpHeaders(AUTHORIZATION -> s"Bearer ${authInfo.accessToken}") returns wsRequest
      wsRequest.get() returns Future.successful(wsResponse)
      httpLayer.url(API.format("my.access.token")) returns wsRequest

      profile(provider.retrieveProfile(authInfo)) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, "12345678"),
          firstName = Some("Apollonia"),
          lastName = Some("Vanova"),
          fullName = Some("Apollonia Vanova")
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
      authorizationURL = Some("https://www.dropbox.com/1/oauth2/authorize"),
      accessTokenURL = "https://api.dropbox.com/1/oauth2/token",
      redirectURL = Some("https://www.mohiva.com"),
      clientID = "my.client.id",
      clientSecret = "my.client.secret",
      scope = None))

    /**
     * The OAuth2 info returned by Dropbox.
     *
     * @see https://www.dropbox.com/developers/core/docs#oa2-token
     */
    override lazy val oAuthInfo = Helper.loadJson("providers/oauth2/dropbox.access.token.json")

    /**
     * The stateful auth info.
     */
    override lazy val stateAuthInfo = StatefulAuthInfo(oAuthInfo.as[OAuth2Info], userStateItem)

    /**
     * The provider to test.
     */
    lazy val provider = new DropboxProvider(httpLayer, stateProvider, oAuthSettings)
  }
}
