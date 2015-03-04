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

import java.net.URLEncoder

import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.UnexpectedResponseException
import com.mohiva.play.silhouette.impl.providers.OpenIDProvider._
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.{ FakeRequest, WithApplication }

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

      c.openIDService.redirectURL(any) returns Future.failed(new Exception(""))

      failed[UnexpectedResponseException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(ErrorRedirectURL.format(c.provider.id, ""))
      }
    }

    "redirect to provider by using the provider URL" in new WithApplication {
      implicit val req = FakeRequest()
      c.openIDService.redirectURL(any) returns Future.successful(c.openIDSettings.providerURL)

      result(c.provider.authenticate()) {
        case result =>
          status(result) must equalTo(SEE_OTHER)
          redirectLocation(result) must beSome.which(_ == c.openIDSettings.providerURL)
      }
    }

    "redirect to provider by using a openID" in new WithApplication {
      implicit val req = FakeRequest(GET, "?openID=my.open.id")
      c.openIDService.redirectURL(any) returns Future.successful(c.openIDSettings.providerURL)

      result(c.provider.authenticate()) {
        case result =>
          status(result) must equalTo(SEE_OTHER)
          redirectLocation(result) must beSome.which(_ == c.openIDSettings.providerURL)
      }
    }

    "fix bug 3749" in new WithApplication {
      val e = (v: String) => URLEncoder.encode(v, "UTF-8")
      implicit val req = FakeRequest()
      c.openIDService.redirectURL(any) returns Future.successful("https://domain.com/openid/login?openid.ns=http://specs.openid.net/auth/2.0"
        + "&openid.mode=checkid_setup"
        + "&openid.claimed_id=" + e(c.openIDSettings.providerURL)
        + "&openid.identity=" + e(c.openIDSettings.providerURL)
        + "&openid.return_to=http://www.mydomain.com/auth/domain"
        + "&openid.realm=http://www.mydomain.com")

      result(c.provider.authenticate()) {
        case result =>
          status(result) must equalTo(SEE_OTHER)
          redirectLocation(result) must beSome.which(_ == "https://domain.com/openid/login?openid.ns=http://specs.openid.net/auth/2.0"
            + "&openid.mode=checkid_setup"
            + "&openid.claimed_id=" + e("http://specs.openid.net/auth/2.0/identifier_select")
            + "&openid.identity=" + e("http://specs.openid.net/auth/2.0/identifier_select")
            + "&openid.return_to=http://www.mydomain.com/auth/domain"
            + "&openid.realm=http://www.mydomain.com")
      }
    }

    "fail with an UnexpectedResponseException if auth info cannot be retrieved" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Mode + "=id_res")
      c.openIDService.verifiedID(any) returns Future.failed(new Exception(""))

      failed[UnexpectedResponseException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(ErrorVerification.format(c.provider.id, ""))
      }
    }

    "return the auth info" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Mode + "=id_res")
      c.openIDService.verifiedID(any) returns Future.successful(c.openIDInfo)

      authInfo(c.provider.authenticate()) {
        case authInfo => authInfo must be equalTo c.openIDInfo
      }
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
  lazy val httpLayer: HTTPLayer = mock[HTTPLayer]

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
