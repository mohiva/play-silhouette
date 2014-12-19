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
package com.mohiva.play.silhouette.impl.providers

import java.net.URLEncoder._

import com.mohiva.silhouette.exceptions._
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.StateException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{ JsValue, Json }
import play.api.libs.ws.WSRequestHolder
import play.api.mvc.Result
import play.api.test.{ FakeRequest, WithApplication }

import scala.concurrent.Future

/**
 * Abstract test case for the [[OAuth2Provider]] class.
 *
 * These tests will be additionally executed before every OAuth2 provider spec.
 */
abstract class OAuth2ProviderSpec extends SocialProviderSpec[OAuth2Info] {
  isolated

  "The `authenticate` method" should {
    val c = context
    "fail with an AccessDeniedException if `error` key with value `access_denied` exists in query string" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Error + "=" + AccessDenied)
      failed[AccessDeniedException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(AuthorizationError.format(c.provider.id, ""))
      }
    }

    "fail with an AuthenticationException if `error` key with unspecified value exists in query string" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Error + "=unspecified")
      failed[AuthenticationException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(AuthorizationError.format(c.provider.id, "unspecified"))
      }
    }

    "redirect to authorization URL if authorization code doesn't exists in request" in new WithApplication {
      implicit val req = FakeRequest(GET, "/")
      val sessionKey = "session-key"
      val sessionValue = "session-value"

      c.state.serialize returns sessionValue
      c.stateProvider.build() returns Future.successful(c.state)
      c.stateProvider.publish(any, any)(any) answers { (a, m) =>
        val result = a.asInstanceOf[Array[Any]](0).asInstanceOf[Result]
        val state = a.asInstanceOf[Array[Any]](1).asInstanceOf[OAuth2State]

        result.withSession(sessionKey -> state.serialize)
      }

      result(c.provider.authenticate()) {
        case result =>
          status(result) must equalTo(SEE_OTHER)
          session(result).get(sessionKey) must beSome(c.state.serialize)
          redirectLocation(result) must beSome.which { url =>
            val urlParams = c.urlParams(url)
            val params = c.oAuthSettings.scope.foldLeft(List(
              (ClientID, c.oAuthSettings.clientID),
              (RedirectURI, c.oAuthSettings.redirectURL),
              (ResponseType, Code),
              (State, urlParams(State))) ++ c.oAuthSettings.authorizationParams.toList) {
              case (p, s) => (Scope, s) :: p
            }
            url must be equalTo (c.oAuthSettings.authorizationURL + params.map { p =>
              encode(p._1, "UTF-8") + "=" + encode(p._2, "UTF-8")
            }.mkString("?", "&", ""))
          }
      }
    }

    "fail with an AuthenticationException if state is invalid" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")

      c.stateProvider.validate(any)(any) returns Future.failed(new StateException("Invalid"))

      failed[AuthenticationException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(InvalidState.format(c.provider.id))
      }
    }

    "submit the proper params to the access token post request" in new WithApplication {
      val requestHolder = mock[WSRequestHolder]
      val params = Map(
        ClientID -> Seq(c.oAuthSettings.clientID),
        ClientSecret -> Seq(c.oAuthSettings.clientSecret),
        GrantType -> Seq(AuthorizationCode),
        Code -> Seq("my.code"),
        RedirectURI -> Seq(c.oAuthSettings.redirectURL)) ++ c.oAuthSettings.accessTokenParams.mapValues(Seq(_))
      implicit val req = FakeRequest(GET, "?" + Code + "=my.code")

      requestHolder.withHeaders(any) returns requestHolder
      c.stateProvider.validate(any)(any) returns Future.successful(c.state)

      // We must use this neat trick here because it isn't possible to check the post call with a verification,
      // because of the implicit params needed for the post call. On the other hand we can test it in the abstract
      // spec, because we throw an exception in both cases which stops the test once the post method was called.
      // This protects as for an NPE because of the not mocked dependencies. The other solution would be to execute
      // this test in every provider with the full mocked dependencies.
      requestHolder.post[Map[String, Seq[String]]](any)(any, any) answers {
        _.equals(params) match {
          case true => throw new RuntimeException("success")
          case false => throw new RuntimeException("failure")
        }
      }
      c.httpLayer.url(c.oAuthSettings.accessTokenURL) returns requestHolder

      failed[RuntimeException](c.provider.authenticate()) {
        case e => e.getMessage must startWith("success")
      }
    }
  }

  /**
   * Defines the context for the abstract OAuth2 provider spec.
   *
   * @return The Context to use for the abstract OAuth2 provider spec.
   */
  protected def context: OAuth2ProviderSpecContext
}

/**
 * Context for the OAuth2ProviderSpec.
 */
trait OAuth2ProviderSpecContext extends Scope with Mockito with ThrownExpectations {

  abstract class TestState extends OAuth2State
  abstract class TestStateProvider extends OAuth2StateProvider {
    type State = TestState
  }

  /**
   * The HTTP layer mock.
   */
  lazy val httpLayer: HTTPLayer = mock[HTTPLayer]

  /**
   * A OAuth2 info.
   */
  lazy val oAuthInfo: JsValue = Json.obj(
    AccessToken -> "my.access.token",
    TokenType -> "bearer",
    ExpiresIn -> 3600,
    RefreshToken -> "my.refresh.token")

  /**
   * The OAuth2 state.
   */
  lazy val state: TestState = mock[TestState]

  /**
   * The OAuth2 state provider.
   */
  lazy val stateProvider: TestStateProvider = mock[TestStateProvider]

  /**
   * The OAuth2 settings.
   */
  def oAuthSettings: OAuth2Settings

  /**
   * The provider to test.
   */
  def provider: OAuth2Provider

  /**
   * Extracts the params of a URL.
   *
   * @param url The url to parse.
   * @return The params of a URL.
   */
  def urlParams(url: String) = (url.split('&') map { str =>
    val pair = str.split('=')
    pair(0) -> pair(1)
  }).toMap
}
