package com.mohiva.play.silhouette.impl.providers.state

import com.mohiva.play.silhouette.api.crypto.CookieSigner
import com.mohiva.play.silhouette.api.util.IDGenerator
import com.mohiva.play.silhouette.impl.providers.SocialStateItem
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{ Json }
import play.api.test.{ FakeRequest, PlaySpecification }

import scala.util.Success
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Cookie

import scala.concurrent.Future

class CsrfStateItemHandlerSpec extends PlaySpecification with Mockito with JsonMatchers {

  "item method of CsrfStateItemHandler" should {
    "return csrfState" in new Context {
      idGenerator.generate returns Future.successful(csrfToken)
      await(csrfStateHandler.item) must beAnInstanceOf[CsrfState]
    }
  }

  "canHandle method of CsrfStateItemHandler" should {
    "should return Some[SocialStateItem] if it can handle the given SocialStateItem." in new Context {
      csrfStateHandler.canHandle(csrfState) must beSome[SocialStateItem]
    }
  }

  "canHandle method of CsrfStateItemHandler" should {
    "should return None if it can't handle the given SocialStateItem." in new Context {
      csrfStateHandler.canHandle(userState) must beNone
    }
  }

  "canHandle method of CsrfStateItemHandler" should {
    "should return true if it can handle the given ItemStructure." in new Context {
      implicit val request = FakeRequest().withCookies(Cookie(
        name = settings.cookieName,
        value = cookieSigner.sign(csrfState.value),
        maxAge = Some(settings.expirationTime.toSeconds.toInt),
        path = settings.cookiePath,
        domain = settings.cookieDomain,
        secure = settings.secureCookie,
        httpOnly = settings.httpOnlyCookie))
      csrfStateHandler.canHandle(itemStructure) must beTrue
    }
  }

  "canHandle method of CsrfStateItemHandler" should {
    "should return false if it can't handle the given ItemStructure." in new Context {
      implicit val request = FakeRequest()
      csrfStateHandler.canHandle(itemStructure.copy(id = "non-csrf-state")) must beFalse
    }
  }

  "serialize method of CsrfStateItemHandler" should {
    "serialize csrfState to ItemStructure" in new Context {
      csrfStateHandler.serialize(csrfState) must beAnInstanceOf[ItemStructure]
    }
  }

  "unserialize method of CsrfStateItemHandler" should {
    "unserialize ItemStructure to CsrfState" in new Context {
      implicit val request = FakeRequest()
      await(csrfStateHandler.unserialize(itemStructure)) must beAnInstanceOf[CsrfState]
    }
  }

  trait Context extends Scope {

    import CsrfStateItemHandler._
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

    case class UserState(state: Map[String, String]) extends SocialStateItem

    val userState = UserState(Map("path" -> "/login"))

    val csrfToken = "csrfToken"
    val csrfState = CsrfState(csrfToken)

    val itemStructure = ItemStructure("csrf-state", Json.toJson(csrfState))

    val csrfStateHandler = new CsrfStateItemHandler(settings, idGenerator, cookieSigner)
  }
}

