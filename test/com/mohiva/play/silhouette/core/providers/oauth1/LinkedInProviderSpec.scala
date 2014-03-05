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
import play.api.libs.ws.{ Response, WS }
import play.api.test.{ FakeRequest, WithApplication }
import scala.concurrent.Future
import scala.util.Success
import com.mohiva.play.silhouette.core.providers._
import com.mohiva.play.silhouette.core.providers.helpers.LinkedInProfile._
import com.mohiva.play.silhouette.core.{ LoginInfo, AuthenticationException }
import LinkedInProvider._
import OAuth1Provider._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.oauth1.LinkedInProvider]] class.
 */
class LinkedInProviderSpec extends OAuth1ProviderSpec {

  "The authenticate method" should {
    "throw AuthenticationException if API returns error" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(Success(oAuthInfo))
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/linkedin.error.json")
      httpLayer.url(API) returns requestHolder

      await(provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must equalTo(SpecifiedProfileError.format(
          provider.id,
          0,
          Some("Unknown authentication scheme"),
          Some("LY860UAC5U"),
          Some(401),
          Some(1390421660154L)))
      }

      there was one(cacheLayer).remove(cacheID)
    }

    "throw AuthenticationException if an unexpected error occurred" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(Success(oAuthInfo))
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json throws new RuntimeException("")
      httpLayer.url(API) returns requestHolder

      await(provider.authenticate()) must throwAn[AuthenticationException].like {
        case e => e.getMessage must equalTo(UnspecifiedProfileError.format(provider.id))
      }

      there was one(cacheLayer).remove(cacheID)
    }

    "return the social profile" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(Success(oAuthInfo))
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/linkedin.success.json")
      httpLayer.url(API) returns requestHolder

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

      there was one(cacheLayer).remove(cacheID)
    }

    "store the auth info if the authentication was successful" in new WithApplication with Context {
      val cacheID = UUID.randomUUID().toString
      val requestHolder = mock[WS.WSRequestHolder]
      val response = mock[Response]
      implicit val req = FakeRequest(GET, "?" + OAuthVerifier + "=my.verifier").withSession(CacheKey -> cacheID)
      cacheLayer.get[OAuth1Info](cacheID) returns Future.successful(Some(oAuthInfo))
      oAuthService.retrieveAccessToken(oAuthInfo, "my.verifier") returns Future.successful(Success(oAuthInfo))
      requestHolder.sign(any) returns requestHolder
      requestHolder.get() returns Future.successful(response)
      response.json returns Helper.loadJson("providers/oauth1/linkedin.success.json")
      httpLayer.url(API) returns requestHolder

      await(provider.authenticate())
      there was one(authInfoService).save[OAuth1Info](LoginInfo(provider.id, "NhZXBl_O6f"), oAuthInfo)
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
      requestTokenURL = "https://api.linkedin.com/uas/oauth/requestToken",
      accessTokenURL = "https://api.linkedin.com/uas/oauth/accessToken",
      authorizationURL = "https://api.linkedin.com/uas/oauth/authenticate",
      callbackURL = "https://www.mohiva.com",
      consumerKey = "my.consumer.key",
      consumerSecret = "my.consumer.secret")

    /**
     * The provider to test.
     */
    lazy val provider = new LinkedInProvider(authInfoService, cacheLayer, httpLayer, oAuthService, oAuthSettings)
  }
}
