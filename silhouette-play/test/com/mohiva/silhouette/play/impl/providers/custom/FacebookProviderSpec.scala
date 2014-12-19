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
package com.mohiva.play.silhouette.impl.providers.custom

import com.mohiva.silhouette.LoginInfo
import com.mohiva.silhouette.exceptions.AuthenticationException
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers.SocialProfileBuilder._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider
import com.mohiva.play.silhouette.impl.providers.oauth2.FacebookProvider._
import play.api.libs.json.JsValue
import play.api.libs.ws.{ WSRequestHolder, WSResponse }
import play.api.test.{ FakeRequest, WithApplication }
import test.Helper

import scala.concurrent.Future
import scala.util.Try

/**
 * Test case for the [[FacebookProvider]] class which uses a custom social profile.
 */
class FacebookProviderSpec extends OAuth2ProviderSpec {

  "The `authenticate` method" should {
    "fail with AuthenticationException if OAuth2Info can be build because of an unexpected response" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      response.body returns ""
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      stateProvider.validate(any)(any) returns Future.successful(state)

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(InvalidInfoFormat.format(provider.id, ""))
      }
    }

    "return the auth info" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")
      response.body returns AccessToken + "=my.access.token&" + Expires + "=1"
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      stateProvider.validate(any)(any) returns Future.successful(state)

      authInfo(provider.authenticate()) {
        case authInfo => authInfo must be equalTo OAuth2Info(
          accessToken = "my.access.token",
          tokenType = None,
          expiresIn = Some(1),
          refreshToken = None)
      }
    }
  }

  "The `retrieveProfile` method" should {
    "fail with ProfileRetrievalException if API returns error" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/custom/facebook.error.json")
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
      response.json returns Helper.loadJson("providers/custom/facebook.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case p =>
          p must be equalTo new CustomSocialProfile(
            loginInfo = LoginInfo(provider.id, "134405962728980"),
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            fullName = Some("Apollonia Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://fbcdn-sphotos-g-a.akamaihd.net/hphotos-ak-ash2/t1/36245_155530314499277_2350717_n.jpg?lvh=1"),
            gender = Some("male")
          )
      }
    }

    "return the social profile and the auth info without expires value" in new WithApplication with Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]
      response.json returns Helper.loadJson("providers/custom/facebook.success.json")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.retrieveProfile(oAuthInfo.as[OAuth2Info])) {
        case p =>
          p must be equalTo new CustomSocialProfile(
            loginInfo = LoginInfo(provider.id, "134405962728980"),
            firstName = Some("Apollonia"),
            lastName = Some("Vanova"),
            fullName = Some("Apollonia Vanova"),
            email = Some("apollonia.vanova@watchmen.com"),
            avatarURL = Some("https://fbcdn-sphotos-g-a.akamaihd.net/hphotos-ak-ash2/t1/36245_155530314499277_2350717_n.jpg?lvh=1"),
            gender = Some("male")
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
    lazy val provider = new FacebookProvider(httpLayer, stateProvider, oAuthSettings) with CustomFacebookProfileBuilder
  }

  /**
   * A custom social profile for testing purpose.
   */
  case class CustomSocialProfile(
    loginInfo: LoginInfo,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    fullName: Option[String] = None,
    email: Option[String] = None,
    avatarURL: Option[String] = None,
    gender: Option[String] = None) extends SocialProfile

  /**
   * A custom Facebook profile builder for testing purpose.
   */
  trait CustomFacebookProfileBuilder extends SocialProfileBuilder {
    self: FacebookProvider =>

    type Profile = CustomSocialProfile
    protected def parseProfile(parser: Parser, json: JsValue): Try[Profile] = Try {
      val commonProfile = parser(json)
      val gender = (json \ "gender").as[String]

      CustomSocialProfile(
        loginInfo = commonProfile.loginInfo,
        firstName = commonProfile.firstName,
        lastName = commonProfile.lastName,
        fullName = commonProfile.fullName,
        avatarURL = commonProfile.avatarURL,
        email = commonProfile.email,
        gender = Some(gender))
    }
  }
}
