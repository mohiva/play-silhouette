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
package com.mohiva.play.silhouette.impl.providers.oauth1.services

import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth1Settings }
import oauth.signpost.exception.{ OAuthException, OAuthMessageSignerException }
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.oauth.{ OAuth, RequestToken }
import play.api.libs.ws.WSSignatureCalculator
import play.api.test.{ WithApplication, PlaySpecification }

/**
 * Test case for the [[PlayOAuth1Service]] class.
 */
class PlayOAuth1ServiceSpec extends PlaySpecification with Mockito {

  "The `withSettings` method" should {
    "create a new instance with customized settings" in new WithApplication with Context {
      val s = service.withSettings { s =>
        s.copy("new-request-token-url")
      }

      s.settings.requestTokenURL must be equalTo "new-request-token-url"
    }
  }

  "The alternative constructor" should {
    "construct the service with the default Play Framework OAuth implementation" in new Context {
      new PlayOAuth1Service(settings) should beAnInstanceOf[PlayOAuth1Service]
    }
  }

  "The `use10a` method" should {
    "return true if the safer 1.0a specification will be used" in new Context {
      oauth.use10a returns true

      service.use10a must beTrue
    }

    "return false if the unsafer 1.0 specification will be used" in new Context {
      oauth.use10a returns false

      service.use10a must beFalse
    }
  }

  "The `retrieveRequestToken` method" should {
    "throw exception if the token couldn't be retrieved" in new Context {
      oauth.retrieveRequestToken(settings.callbackURL) returns Left(new OAuthMessageSignerException(""))

      await(service.retrieveRequestToken(settings.callbackURL)) must throwA[OAuthException]
    }

    "return request token" in new Context {
      oauth.retrieveRequestToken(settings.callbackURL) returns Right(token)

      await(service.retrieveRequestToken(settings.callbackURL)) must be equalTo info
    }
  }

  "The `retrieveAccessToken` method" should {
    "throw Exception if the token couldn't be retrieved" in new Context {
      oauth.retrieveAccessToken(token, "") returns Left(new OAuthMessageSignerException(""))

      await(service.retrieveAccessToken(info, "")) must throwA[OAuthException]
    }

    "return access token" in new Context {
      oauth.retrieveAccessToken(token, "") returns Right(token)

      await(service.retrieveAccessToken(info, "")) must be equalTo info
    }
  }

  "The `redirectUrl` method" should {
    "return the redirect Url" in new Context {
      oauth.redirectUrl("token") returns "http://redirect.url"

      service.redirectUrl("token") must be equalTo "http://redirect.url"
    }
  }

  "The `sign` method" should {
    "return the signature calculator" in new Context {
      oauth.info returns PlayOAuth1Service.serviceInfo(settings)

      service.sign(info) must beAnInstanceOf[WSSignatureCalculator]
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The OAuth1 settings.
     */
    lazy val settings = OAuth1Settings(
      requestTokenURL = "https://api.linkedin.com/uas/oauth/requestToken",
      accessTokenURL = "https://api.linkedin.com/uas/oauth/accessToken",
      authorizationURL = "https://api.linkedin.com/uas/oauth/authenticate",
      callbackURL = "https://www.mohiva.com",
      consumerKey = "my.consumer.key",
      consumerSecret = "my.consumer.secret")

    /**
     * The Silhouette OAuth1 info.
     */
    lazy val info = OAuth1Info("my.token", "my.secret")

    /**
     * The Play OAuth request token.
     */
    lazy val token = RequestToken("my.token", "my.secret")

    /**
     * A mock of the Play Framework OAuth implementation.
     */
    lazy val oauth: OAuth = mock[OAuth]

    /**
     * The service to test.
     */
    lazy val service = new PlayOAuth1Service(oauth, settings)
  }
}
