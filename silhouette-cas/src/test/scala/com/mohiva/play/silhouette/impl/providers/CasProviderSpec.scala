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

import com.mohiva.play.silhouette.api.exceptions.{ ConfigurationException, SilhouetteException }
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.api.{ Logger, LoginInfo }
import org.jasig.cas.client.authentication.AttributePrincipal
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.FakeRequest
import test.SocialProviderSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Test case for the [[CasProvider]] class.
 */
class CasProviderSpec extends SocialProviderSpec[CasInfo] with Mockito with Logger {

  "The settings" should {
    "fail with a ConfigurationException if casURL is invalid" in new Context {
      CasSettings(casURL = "", redirectURL = "https://cas-redirect/") must throwA[ConfigurationException].like {
        case e => e.getMessage must be equalTo CasSettings.CasUrlInvalid.format(CasProvider.ID, "")
      }
    }

    "fail with a ConfigurationException if redirectURL is invalid" in new Context {
      CasSettings(casURL = "https://cas-url/", redirectURL = "") must throwA[ConfigurationException].like {
        case e => e.getMessage must be equalTo CasSettings.RedirectUrlInvalid.format(CasProvider.ID, "")
      }
    }

    "fail with a ConfigurationException if encoding is empty" in new Context {
      CasSettings(
        casURL = "https://cas-url/",
        redirectURL = "https://cas-redirect/",
        encoding = ""
      ) must throwA[ConfigurationException].like {
        case e => e.getMessage must be equalTo CasSettings.EncodingInvalid.format(CasProvider.ID)
      }
    }

    "fail with a ConfigurationException if samlTimeTolerance is negative" in new Context {
      CasSettings(
        casURL = "https://cas-url/",
        redirectURL = "https://cas-redirect/",
        samlTimeTolerance = -1.millis
      ) must throwA[ConfigurationException].like {
        case e => e.getMessage must be equalTo CasSettings.TimeToleranceInvalid.format(CasProvider.ID, -1.millis)
      }
    }
  }

  "The `authenticate` method" should {
    "redirect to CAS server if service ticket is not present in request" in new Context {
      implicit val req = FakeRequest(GET, "/")

      result(provider.authenticate()) { result =>
        status(result) must equalTo(SEE_OTHER)
        redirectLocation(result) must beSome("https://cas-url/?service=https%3A%2F%2Fcas-redirect%2F")
      }
    }

    "redirect to CAS server with the original requested URL if service ticket is not present in the request" in new Context {
      implicit val req = FakeRequest(GET, redirectURLWithOrigin)

      result(provider.authenticate()) { result =>
        status(result) must equalTo(SEE_OTHER)
        redirectLocation(result) must beSome("https://cas-url/?service=https%3A%2F%2Fcas-redirect%2F")
      }
    }

    "return a valid CASAuthInfo object if service ticket is present in request" in new Context {
      implicit val req = FakeRequest(GET, "/?ticket=%s".format(ticket))

      authInfo(provider.authenticate())(authInfo => authInfo must be equalTo CasInfo(ticket))
    }
  }

  "The `retrieveProfile` method" should {
    "return a valid profile if the CAS client validates the ticket" in new Context {
      principal.getName returns userName
      principal.getAttributes returns attr
      client.validateServiceTicket(ticket) returns Future.successful(principal)

      implicit val req = FakeRequest(GET, "/?ticket=%s".format(ticket))

      val futureProfile = for {
        a <- provider.authenticate()
        c <- a.fold(_ => Future.failed(new SilhouetteException("Missing CasInfo")), Future.successful)
        p <- provider.retrieveProfile(c)
      } yield p

      await(futureProfile) must beLike[CommonSocialProfile] {
        case profile =>
          profile must be equalTo CommonSocialProfile(
            loginInfo = new LoginInfo(CasProvider.ID, userName),
            firstName = Some(firstName),
            lastName = Some(lastName),
            fullName = None,
            email = Some(email),
            avatarURL = Some(pictureURL)
          )
      }
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {
    lazy val settings = CasSettings(
      casURL = "https://cas-url/",
      redirectURL = "https://cas-redirect/")

    lazy val httpLayer = {
      val m = mock[HTTPLayer]
      m.executionContext returns global
      m
    }

    lazy val redirectURLWithOrigin = "%s/my/original/url".format(settings.redirectURL)

    lazy val client = spy(new CasClient(settings))

    lazy val provider = new CasProvider(httpLayer, settings, client)

    lazy val ticket = "ST-12345678"

    lazy val casAuthInfo = CasInfo(ticket)

    lazy val principal = mock[AttributePrincipal].smart

    lazy val name = "abc123"
    lazy val email = "email"
    lazy val firstName = "Nick"
    lazy val lastName = "Shaw"
    lazy val userName = "314159"
    lazy val pictureURL = "http://www.gravatar"

    lazy val attr = new java.util.HashMap[String, Object]()
    attr.put(CasProvider.Email, email)
    attr.put(CasProvider.FirstName, firstName)
    attr.put(CasProvider.LastName, lastName)
    attr.put(CasProvider.UserName, userName)
    attr.put(CasProvider.PictureURL, pictureURL)
  }
}
