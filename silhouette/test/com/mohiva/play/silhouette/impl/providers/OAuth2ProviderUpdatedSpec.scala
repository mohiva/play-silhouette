package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.AccessDeniedException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider.{ AccessToken, AuthorizationError, ExpiresIn, RefreshToken, TokenType }
import com.mohiva.play.silhouette.impl.providers.oauth2.state.StateProvider
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{ JsValue, Json }
import play.api.test.{ FakeRequest, WithApplication }
import play.api.libs.concurrent.Execution._
import play.api.mvc.Result

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
/**
 * Created by sahebmotiani on 19/12/2016.
 */
abstract class OAuth2ProviderUpdatedSpec extends SocialProviderSpec[OAuth2Info] {
  isolated

  val c = context

  "The `authenticate` method" should {
    "fail with an AccessDeniedException if `error` key with value `access_denied` exists in query string" in new WithApplication {
      implicit val req = FakeRequest(GET, "?" + "error" + "=" + "access_denied")
      failed[AccessDeniedException](c.provider.authenticate()) {
        case e => e.getMessage must startWith(AuthorizationError.format(c.provider.id, ""))
      }
    }
  }

  "sending and receiving user state params" in new WithApplication() {
    c.oAuthSettings.authorizationURL match {
      case None => skipped("authorizationURL is not defined, so this step isn't needed for provider: " + c.provider.getClass)
      case Some(_) =>
        implicit val req = FakeRequest(GET, "/")

        c.stateProvider.serialize(any)(any) returns Future.successful("")
        c.stateProvider.publish(any, any)(any) answers { (a, m) =>
          a.asInstanceOf[Array[Any]](0).asInstanceOf[Result]
        }

        val userState = Map("path" -> "/login")
        statefulResult(c.provider.authenticate(userState))(result =>
          Await.result(result, Duration.Inf).userState must_== userState)
    }
  }
  protected def context: OAuth2ProviderUpdatedSpecContext
}

trait OAuth2ProviderUpdatedSpecContext extends Scope with Mockito with ThrownExpectations {

  abstract class TestState extends OAuth2State
  abstract class TestStateProvider extends StateProvider {
    type State = TestState
  }

  /**
   * The HTTP layer mock.
   */
  lazy val httpLayer = {
    val m = mock[HTTPLayer].smart
    m.executionContext returns defaultContext
    m
  }

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
  lazy val state = mock[TestState].smart

  /**
   * The OAuth2 state provider.
   */
  lazy val stateProvider = mock[TestStateProvider].smart

  /**
   * The OAuth2 settings.
   */
  def oAuthSettings: OAuth2Settings = spy(OAuth2Settings(
    authorizationURL = Some("https://graph.facebook.com/oauth/authorize"),
    accessTokenURL = "https://graph.facebook.com/oauth/access_token",
    clientID = "my.client.id",
    clientSecret = "my.client.secret",
    scope = Some("email")))

  /**
   * The provider to test.
   */
  def provider: OAuth2ProviderUpdated

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
