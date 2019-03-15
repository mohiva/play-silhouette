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
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.UnexpectedResponseException
import com.mohiva.play.silhouette.impl.providers.OpenIDProvider._
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.{ AnyContent, AnyContentAsEmpty }
import play.api.test.{ FakeHeaders, FakeRequest, WithApplication }
import play.mvc.Http.HeaderNames
import test.SocialProviderSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Abstract test case for the [[OpenIDProvider]] class.
 *
 * These tests will be additionally executed before every OpenIDProvider provider spec.
 */
abstract class OpenIDProviderSpec extends SocialProviderSpec[OpenIDInfo] {
  isolated

  "The authenticate method" should {
    val c = context
    "fail with an UnexpectedResponseException if redirect URL couldn't be retrieved" in new WithApplication {
      implicit val req = FakeRequest()

      c.openIDService.redirectURL(any, any)(any) returns Future.failed(new Exception(""))

      failed[UnexpectedResponseException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(ErrorRedirectURL.format(c.provider.id, ""))
      }
    }

    "redirect to provider by using the provider URL" in new WithApplication {
      implicit val req = FakeRequest()
      c.openIDService.redirectURL(any, any)(any) answers { _ => Future.successful(c.openIDSettings.providerURL) }

      result(c.provider.authenticate()) { result =>
        status(result) must equalTo(SEE_OTHER)
        redirectLocation(result) must beSome.which(_ == c.openIDSettings.providerURL)
      }
    }

    "redirect to provider by using a openID" in new WithApplication {
      implicit val req = FakeRequest(GET, "?openID=my.open.id")
      c.openIDService.redirectURL(any, any)(any) answers { _ => Future.successful(c.openIDSettings.providerURL) }

      result(c.provider.authenticate()) { result =>
        status(result) must equalTo(SEE_OTHER)
        redirectLocation(result) must beSome.which(_ == c.openIDSettings.providerURL)
      }
    }

    "resolves relative callbackURLs before starting the flow" in new WithApplication {
      verifyRelativeCallbackURLResolution("/callback-url", secure = false, "http://www.example.com/callback-url")
    }

    "resolves path relative callbackURLs before starting the flow" in new WithApplication {
      verifyRelativeCallbackURLResolution("callback-url", secure = false, "http://www.example.com/request-path/callback-url")
    }

    "resolves relative callbackURLs before starting the flow over https" in new WithApplication {
      verifyRelativeCallbackURLResolution("/callback-url", secure = true, "https://www.example.com/callback-url")
    }

    def verifyRelativeCallbackURLResolution(callbackURL: String, secure: Boolean, resolvedCallbackURL: String) = {
      implicit val req = FakeRequest[AnyContent](
        method = GET,
        uri = "/request-path/something",
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "www.example.com")),
        body = AnyContentAsEmpty,
        secure = secure
      )

      c.openIDSettings.callbackURL returns callbackURL
      c.openIDService.redirectURL(any, any)(any) answers { _ => Future.successful(c.openIDSettings.providerURL) }

      await(c.provider.authenticate())
      there was one(c.openIDService).redirectURL(any, ===(resolvedCallbackURL))(any)
    }

    "fail with an UnexpectedResponseException if auth info cannot be retrieved" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Mode + "=id_res")
      c.openIDService.verifiedID(any, any) returns Future.failed(new Exception(""))

      failed[UnexpectedResponseException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(ErrorVerification.format(c.provider.id, ""))
      }
    }

    "return the auth info" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Mode + "=id_res")
      c.openIDService.verifiedID(any, any) answers { _ => Future.successful(c.openIDInfo) }

      authInfo(c.provider.authenticate())(_ must be equalTo c.openIDInfo)
    }
  }

  "The `settings` method" should {
    val c = context
    "return the settings instance" in {
      c.provider.settings must be equalTo c.openIDSettings
    }
  }

  /**
   * Defines the context for the abstract OpenIDProvider provider spec.
   *
   * @return The Context to use for the abstract OpenIDProvider provider spec.
   */
  protected def context: OpenIDProviderSpecContext
}

/**
 * Context for the OpenIDProviderSpec.
 */
trait OpenIDProviderSpecContext extends Scope with Mockito with ThrownExpectations {

  /**
   * The HTTP layer mock.
   */
  lazy val httpLayer = {
    val m = mock[HTTPLayer]
    m.executionContext returns global
    m
  }

  /**
   * A OpenID info.
   */
  lazy val openIDInfo = OpenIDInfo("my.openID", Map())

  /**
   * The OpenID service mock.
   */
  lazy val openIDService: OpenIDService = mock[OpenIDService]

  /**
   * The OpenID settings.
   */
  def openIDSettings: OpenIDSettings

  /**
   * The provider to test.
   */
  def provider: OpenIDProvider
}
