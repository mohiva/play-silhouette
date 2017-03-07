package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.crypto.CookieSigner
import com.mohiva.play.silhouette.api.util.IDGenerator
import com.mohiva.play.silhouette.impl.providers.state.{ CsrfState, CsrfStateItemHandler, CsrfStateSettings, UserStateItemHandler }
import org.specs2.specification.Scope

import scala.concurrent.duration._
import scala.util.Success
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{ Format, Json }

import scala.concurrent.Future

class DefaultSocialStateProviderSpec extends SocialStateProviderSpec {

  "The state method of the state provider" should {
    "return Social State which wraps set of states" in new Context {
      val csrfToken = "csrfToken"
      idGenerator.generate returns Future.successful(csrfToken)
      val socialState = await(provider.state)
      socialState.items must contain(CsrfState(csrfToken))
      socialState.items must contain(userState)
    }
  }

  trait Context extends Scope {
    /**
     * The ID generator implementation.
     */
    lazy val idGenerator = mock[IDGenerator].smart
    /**
     * The settings.
     */
    lazy val settings = CsrfStateSettings(
      cookieName = "OAuth2State",
      cookiePath = "/",
      cookieDomain = None,
      secureCookie = true,
      httpOnlyCookie = true,
      expirationTime = 5 minutes
    )

    /**
     * The cookie signer implementation.
     *
     * The cookie signer returns the same value as passed to the methods. This is enough for testing.
     */
    lazy val cookieSigner = {
      val c = mock[CookieSigner].smart
      c.sign(any) answers { p => p.asInstanceOf[String] }
      c.extract(any) answers { p => Success(p.asInstanceOf[String]) }
      c
    }

    case class UserState(state: Map[String, String]) extends SocialStateItem

    implicit val userStateFormat: Format[UserState] = Json.format[UserState]

    val userState = UserState(Map("path" -> "/login"))

    val csrfStateHandler = new CsrfStateItemHandler(settings, idGenerator, cookieSigner)
    val userStateHandler = new UserStateItemHandler(userState)

    /**
     * The state provider implementation to test.
     */
    lazy val provider = new DefaultSocialStateHandler(Set(csrfStateHandler, userStateHandler), cookieSigner)
  }
}
