/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.impl.providers.oauth2

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.exceptions.{ ProfileRetrievalException, UnexpectedResponseException }
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.Auth0Provider._
import play.api.libs.json.Json
import play.api.libs.ws.{ WSRequest, WSResponse }
import play.api.test.{ FakeRequest, WithApplication }
import play.mvc.Http
import test.Helper

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Test case for the [[Auth0Provider]] class.
 */
class Auth0ProviderSpec extends OAuth2ProviderSpec {

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
      stateProvider.unserialize(anyString)(any[ExtractableRequest[String]], any[ExecutionContext]) returns Future.successful(state)
      stateProvider.state(any[ExecutionContext]) returns Future.successful(state)

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
      stateProvider.unserialize(anyString)(any[ExtractableRequest[String]], any[ExecutionContext]) returns Future.successful(state)
      stateProvider.state(any[ExecutionContext]) returns Future.successful(state)

      authInfo(provider.authenticate())(_ must be equalTo oAuthInfo.as[OAuth2Info])
    }
  }

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      val statusCode = Http.Status.UNAUTHORIZED

      response.status returns statusCode
      requestHolder.get() returns Future.successful(response)
      requestHolder.withHeaders(("Authorization", s"Bearer ${oAuthInfoObject.accessToken}")) returns requestHolder
      httpLayer.url(oAuthSettings.apiURL.get) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfoObject)) {
        case e => e.getMessage must equalTo(GenericHttpStatusProfileError.format(provider.id, statusCode))
      }
    }

    "fail with ProfileRetrievalException if an unexpected error occurred" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]

      response.json throws new RuntimeException("")
      requestHolder.get() returns Future.successful(response)
      requestHolder.withHeaders(("Authorization", s"Bearer ${oAuthInfoObject.accessToken}")) returns requestHolder
      httpLayer.url(oAuthSettings.apiURL.get) returns requestHolder

      failed[ProfileRetrievalException](provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case e => e.getMessage must equalTo("[Silhouette][auth0] error retrieving profile information")
      }
    }

    "return the social profile" in new WithApplication with Context {
      val requestHolder = mock[WSRequest]
      val response = mock[WSResponse]
      val userProfile = Helper.loadJson(Auth0UserProfileJson)

      response.status returns Http.Status.OK
      response.json returns userProfile
      requestHolder.get() returns Future.successful(response)
      requestHolder.withHeaders(("Authorization", s"Bearer ${oAuthInfoObject.accessToken}")) returns requestHolder
      httpLayer.url(oAuthSettings.apiURL.get) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) { p =>
        p must be equalTo CommonSocialProfile(
          loginInfo = LoginInfo(provider.id, (userProfile \ "user_id").as[String]),
          email = (userProfile \ "email").asOpt[String],
          avatarURL = (userProfile \ "picture").asOpt[String]
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
     * Paths to the Json fixtures.
     */
    val Auth0ErrorJson = "providers/custom/auth0.error.json"
    val Auth0SuccessJson = "providers/custom/auth0.success.json"
    val Auth0UserProfileJson = "providers/custom/auth0.profile.json"

    /**
     * The OAuth2 settings.
     */
    override lazy val oAuthSettings = spy(OAuth2Settings(
      authorizationURL = Some("https://gerritforge.eu.auth0.com/authorize"),
      accessTokenURL = "https://gerritforge.eu.auth0.com/oauth/token",
      apiURL = Some("https://gerritforge.eu.auth0.com/userinfo"),
      redirectURL = Some("https://www.mohiva.com"),
      clientID = "some.client.id",
      clientSecret = "some.secret",
      scope = Some("email")))

    /**
     * The OAuth2 info returned by Auth0.
     */
    override lazy val oAuthInfo = Helper.loadJson(Auth0SuccessJson)

    /**
     * The OAuth2 info deserialized as case class object
     */
    lazy val oAuthInfoObject = oAuthInfo.as[OAuth2Info]

    /**
     * The provider to test.
     */
    lazy val provider = new Auth0Provider(httpLayer, stateProvider, oAuthSettings)
  }
}
