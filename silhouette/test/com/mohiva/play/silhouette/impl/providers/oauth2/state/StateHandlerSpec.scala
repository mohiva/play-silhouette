package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.crypto.CookieSigner
import com.mohiva.play.silhouette.api.util.{ Clock, IDGenerator }
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.{ FakeHeaders, FakeRequest, PlaySpecification, WithApplication }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Cookie

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Success

class StateHandlerSpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

  "The `serialize` method of the provider" should {
    "return an non empty string" in new Context {
      await(stateProvider.serialize) must not be equalTo("")
    }
  }

  "The unserialize method of the provider" should {
    "return list of handlers" in new Context {
      val stateParam = await(stateProvider.serialize)
      val headers = FakeHeaders(Seq(("state", stateParam)))
      implicit val req = FakeRequest(GET, "/", headers, "").withCookies(Cookie(settings.cookieName, stateParam))

      val stateMap = await(stateProvider.unserialize)
      stateMap.get("userState") must beSome[Map[String, String]]
      stateMap.get("csrfState") must beSome[Map[String, String]]
    }
  }

  "The unserialize method of the provider" should {
    "throw OAuth2StateException if stateParam doesn't come back from provider" in new Context {
      implicit val req = FakeRequest(GET, "/")

      await(stateProvider.unserialize) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(SocialStateHandler.ProviderStateDoesNotExists)
      }
    }
  }

  trait Context extends Scope {

    /**
     * The ID generator implementation.
     */
    lazy val idGenerator = mock[IDGenerator].smart

    /**
     * The clock implementation.
     */
    lazy val clock = mock[Clock].smart

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

    lazy val csrfStateHandler = new CsrfSocialStateHandler(settings, idGenerator, cookieSigner, clock, Map("value" -> "signed cookie"))

    val userStateHandler = new UserSocialStateHandler(Map("path" -> "/login"))

    val settings = CsrfStateSettings()

    lazy val stateProvider = new StateProviderImpl(Set(csrfStateHandler, userStateHandler))

  }

}
