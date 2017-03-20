package com.mohiva.play.silhouette.impl.providers.state

import com.mohiva.play.silhouette.api.crypto.CookieSigner
import com.mohiva.play.silhouette.api.util.IDGenerator
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import com.mohiva.play.silhouette.impl.providers.{ DefaultSocialStateHandler, SocialState, SocialStateItem }
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{ Format, Json }
import play.api.mvc.Cookie
import play.api.test.{ FakeRequest, PlaySpecification }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
import play.api.libs.concurrent.Execution.Implicits._

/**
 *  Test case for the [[DefaultSocialStateHandler]] class.
 */
class DefaultSocialStateHandlerSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `state` method" should {
    "return `SocialState` which wraps set of states" in new Context {
      idGenerator.generate returns Future.successful(csrfToken)
      val socialState = await(stateHandler.state)
      socialState.items must contain(CsrfState(csrfToken))
      socialState.items must contain(userState)
    }
  }

  "The `withHandler` method" should {
    "return a new instance with updated set of handlers" in new Context {
      val updatedProvider = stateHandlerWithoutUserState.withHandler(userStateHandler)
      updatedProvider.handlers must contain(userStateHandler)
      updatedProvider.handlers must haveLength(2)
    }
  }

  "The `serialize` method" should {
    "create a state String from `SocialState`" in new Context {
      idGenerator.generate returns Future.successful(csrfToken)
      stateHandler.serialize(SocialState(Set(userState, CsrfState(csrfToken)))) must beAnInstanceOf[String]
    }
  }

  "The `unserialize` method" should {
    "create `SocialState` from a state String" in new Context {
      idGenerator.generate returns Future.successful(csrfToken)
      val stateParam = stateHandler.serialize(SocialState(Set(userState, csrfState)))

      implicit val request = FakeRequest().withCookies(Cookie(
        name = settings.cookieName,
        value = cookieSigner.sign(csrfState.value),
        maxAge = Some(settings.expirationTime.toSeconds.toInt),
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie))
      val socialState = await(stateHandler.unserialize(stateParam))
      socialState.items must contain(userState)
      socialState.items must contain(csrfState)
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
      cookieName = "OAuth2CsrfState",
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

    /**
     * An example usage of UserState where state is of type Map[String, String]
     * @param state
     */
    case class UserState(state: Map[String, String]) extends SocialStateItem

    /**
     * Format to serialize the UserState
     */
    implicit val userStateFormat: Format[UserState] = Json.format[UserState]

    /**
     * An instance of UserState
     */
    val userState = UserState(Map("path" -> "/login"))

    /**
     * Serialized type of UserState
     */
    val itemStructure = ItemStructure("user-state", Json.toJson(userState))

    /**
     * Csrf State value
     */
    val csrfToken = "csrfToken"

    /**
     * An instance of CsrfState
     */
    val csrfState = CsrfState(csrfToken)

    /**
     * An instance of Csrf State Handler
     */
    val csrfStateHandler = new CsrfStateItemHandler(settings, idGenerator, cookieSigner)

    /**
     * An instance of User State Handler
     */
    val userStateHandler = new UserStateItemHandler(userState)

    /**
     * The default state provider with User and Csrf State Handlers to test
     */
    lazy val stateHandler = new DefaultSocialStateHandler(Set(csrfStateHandler, userStateHandler), cookieSigner)

    /**
     * The default state provider without User State Handler to test
     */
    lazy val stateHandlerWithoutUserState = new DefaultSocialStateHandler(Set(csrfStateHandler), cookieSigner)
  }
}
