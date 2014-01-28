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
package com.mohiva.play.silhouette.contrib.providers

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification
import play.api.libs.oauth.{ RequestToken, OAuth }
import oauth.signpost.exception.{ OAuthException, OAuthMessageSignerException }
import com.mohiva.play.silhouette.core.providers.{ OAuth1Info, OAuth1Settings }

/**
 * Test case for the [[com.mohiva.play.silhouette.contrib.providers.PlayOAuth1Service]] class.
 */
class PlayOAuth1ServiceSpec extends PlaySpecification with Mockito {

  "The retrieveRequestToken method" should {
    "return Failure if the token couldn't be retrieved" in new Context {
      oauth.retrieveRequestToken(settings.callbackURL) returns Left(new OAuthMessageSignerException(""))

      await(service.retrieveRequestToken(settings.callbackURL)) must beFailedTry.withThrowable[OAuthException]
    }

    "return Success if the token could be retrieved" in new Context {
      oauth.retrieveRequestToken(settings.callbackURL) returns Right(RequestToken("", ""))

      await(service.retrieveRequestToken(settings.callbackURL)) must beSuccessfulTry.withValue(OAuth1Info("", ""))
    }
  }

  "The retrieveAccessToken method" should {
    "return Failure if the token couldn't be retrieved" in new Context {
      oauth.retrieveAccessToken(RequestToken("", ""), "") returns Left(new OAuthMessageSignerException(""))

      await(service.retrieveAccessToken(OAuth1Info("", ""), "")) must beFailedTry.withThrowable[OAuthException]
    }

    "return Success if the token could be retrieved" in new Context {
      oauth.retrieveAccessToken(RequestToken("", ""), "") returns Right(RequestToken("", ""))

      await(service.retrieveAccessToken(OAuth1Info("", ""), "")) must beSuccessfulTry.withValue(OAuth1Info("", ""))
    }
  }

  "The redirectUrl method" should {
    "return the redirect Url" in new Context {
      oauth.redirectUrl("token") returns "http://redirect.url"

      service.redirectUrl("token") must be equalTo "http://redirect.url"
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
     * A mock of the Play Framework OAuth implementation.
     */
    lazy val oauth: OAuth = mock[OAuth]

    /**
     * The service to test.
     */
    lazy val service = new PlayOAuth1Service(oauth, settings)
  }
}
