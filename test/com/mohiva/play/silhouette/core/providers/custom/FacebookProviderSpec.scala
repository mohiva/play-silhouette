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
package com.mohiva.play.silhouette.core.providers.custom

import test.Helper
import java.util.UUID
import scala.util.Try
import scala.concurrent.Future
import play.api.libs.json.JsValue
import play.api.libs.ws.{ Response, WS }
import play.api.test.{ FakeRequest, WithApplication }
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.providers.oauth2.FacebookProvider
import com.mohiva.play.silhouette.core.exceptions.AuthenticationException
import com.mohiva.play.silhouette.core.services.AuthInfo
import SocialProfileBuilder._
import OAuth2Provider._
import FacebookProvider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.oauth2.FacebookProvider]] class which uses a custom social profile.
 */
class FacebookProviderSpec extends OAuth2ProviderSpec {

  "The authenticate method" should {
    "fail with AuthenticationException if OAuth2Info can be build because of an unexpected response" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
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

    "fail with AuthenticationException if API returns error" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.body returns AccessToken + "=my.access.token&" + Expires + "=1"
      response.json returns Helper.loadJson("providers/custom/facebook.error.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          "An active access token must be used to query information about the current user.",
          "OAuthException",
          2500))
      }
    }

    "fail with AuthenticationException if an unexpected error occurred" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.body returns AccessToken + "=my.access.token&" + Expires + "=1"
      response.json throws new RuntimeException("")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      failed[AuthenticationException](provider.authenticate()) {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "return the social profile and the auth info with expires value" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.body returns AccessToken + "=my.access.token&" + Expires + "=1"
      response.json returns Helper.loadJson("providers/custom/facebook.success.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.authenticate()) {
        case p =>
          p must be equalTo new CustomSocialProfile(
            loginInfo = LoginInfo(provider.id, "134405962728980"),
            authInfo = OAuth2Info(
              accessToken = "my.access.token",
              tokenType = None,
              expiresIn = Some(1),
              refreshToken = None),
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
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.body returns AccessToken + "=my.access.token"
      response.json returns Helper.loadJson("providers/custom/facebook.success.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      profile(provider.authenticate()) {
        case p =>
          p must be equalTo new CustomSocialProfile(
            loginInfo = LoginInfo(provider.id, "134405962728980"),
            authInfo = OAuth2Info(
              accessToken = "my.access.token",
              tokenType = None,
              expiresIn = None,
              refreshToken = None),
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
    lazy val provider = new FacebookProvider(cacheLayer, httpLayer, oAuthSettings) with CustomFacebookProfileBuilder
  }

  /**
   * A custom social profile for testing purpose.
   */
  case class CustomSocialProfile[A <: AuthInfo](
    loginInfo: LoginInfo,
    authInfo: A,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    fullName: Option[String] = None,
    email: Option[String] = None,
    avatarURL: Option[String] = None,
    gender: Option[String] = None) extends SocialProfile[A]

  /**
   * A custom Facebook profile builder for testing purpose.
   */
  trait CustomFacebookProfileBuilder extends SocialProfileBuilder[OAuth2Info] {
    self: FacebookProvider =>

    override type Profile = CustomSocialProfile[OAuth2Info]
    override protected def parseProfile(parser: JsonParser, json: JsValue): Try[Profile] = Try {
      val commonProfile = parser(json)
      val gender = (json \ "gender").as[String]

      CustomSocialProfile(
        loginInfo = commonProfile.loginInfo,
        authInfo = commonProfile.authInfo,
        firstName = commonProfile.firstName,
        lastName = commonProfile.lastName,
        fullName = commonProfile.fullName,
        avatarURL = commonProfile.avatarURL,
        email = commonProfile.email,
        gender = Some(gender))
    }
  }
}
