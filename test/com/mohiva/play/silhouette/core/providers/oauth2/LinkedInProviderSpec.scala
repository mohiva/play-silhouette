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
import play.api.libs.json.Json
import play.api.libs.ws.{ Response, WS }
import play.api.test.{ FakeRequest, WithApplication }
import scala.concurrent.Future
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.providers.helpers.LinkedInProfile._
import com.mohiva.play.silhouette.core.{ LoginInfo, AuthenticationException }
import LinkedInProvider._
import OAuth2Provider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.oauth2.LinkedInProvider]] class.
 */
class LinkedInProviderSpec extends OAuth2ProviderSpec {

  "The authenticate method" should {
    "throw AuthenticationException if OAuth2Info can be build because of an unexpected response" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns Json.obj()
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder

      await(provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must startWith(InvalidResponseFormat.format(provider.id, ""))
      }
    }

    "throw AuthenticationException if API returns error" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns oAuthInfo thenReturns Helper.loadJson("providers/oauth2/linkedin.error.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      await(provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          0,
          Some("Unknown authentication scheme"),
          Some("LY860UAC5U"),
          Some(401),
          Some(1390421660154L)))
      }
    }

    "throw AuthenticationException if an unexpected error occurred" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns oAuthInfo thenThrows new RuntimeException("")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      await(provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }
    }

    "return the social profile" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val state = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code&" + State + "=" + state).withSession(CacheKey -> cacheID)
      response.json returns oAuthInfo thenReturns Helper.loadJson("providers/oauth2/linkedin.success.json")
      requestHolder.withHeaders(any) returns requestHolder
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) returns Future.successful(response)
      requestHolder.get() returns Future.successful(response)
      cacheLayer.get[String](cacheID) returns Future.successful(Some(state))
      httpLayer.url(oAuthSettings.accessTokenURL) returns requestHolder
      httpLayer.url(API.format("my.access.token")) returns requestHolder

      await(provider.authenticate()) must beRight.like {
        case p =>
          p must be equalTo new SocialProfile(
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
    lazy val oAuthSettings = OAuth2Settings(
      authorizationURL = "https://www.linkedin.com/uas/oauth2/authorization",
      accessTokenURL = "https://www.linkedin.com/uas/oauth2/accessToken",
      redirectURL = "https://www.mohiva.com",
      clientID = "my.client.id",
      clientSecret = "my.client.secret",
      scope = None)

    /**
     * The OAuth2 info returned by GutHub
     *
     * @see https://developer.linkedin.com/documents/authentication
     */
    override lazy val oAuthInfo = Helper.loadJson("providers/oauth2/linkedin.access.token.json")

    /**
     * The provider to test.
     */
    lazy val provider = new LinkedInProvider(authInfoService, cacheLayer, httpLayer, oAuthSettings)
  }
}
