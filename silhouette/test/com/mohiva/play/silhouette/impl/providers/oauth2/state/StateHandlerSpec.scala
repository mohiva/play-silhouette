package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.{ FakeHeaders, FakeRequest, PlaySpecification, WithApplication }
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration.FiniteDuration

class StateHandlerSpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

  "The `validate` method of the provider" should {
    "return the state if it's valid" in new WithApplication with Context {
      val stateParam = await(stateProvider.serialize)
      val headers = FakeHeaders(Seq(("state", stateParam)))
      implicit val req = FakeRequest(GET, "/", headers, "")

      await(stateProvider.validate) must be equalTo true
    }
  }

  "The `serialize` method of the provider" should {
    "return an non empty string" in new Context {
      await(stateProvider.serialize) must not be equalTo("")
    }
  }

  "The unserialize method of the provider" should {
    "return list of handlers" in new Context {
      val stateParam = await(stateProvider.serialize)
      val headers = FakeHeaders(Seq(("state", stateParam)))
      implicit val req = FakeRequest(GET, "/", headers, "")

      await(stateProvider.unserialize).toList.foreach {
        sh => sh must beLike { case handler: StateHandler => ok }
      }
    }
  }

  "The unserialize method of the provider" should {
    "throw OAuth2StateException if stateParam doesn't come back from provider" in new Context {
      implicit val req = FakeRequest(GET, "/")

      await(stateProvider.unserialize) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(StateHandler.ProviderStateDoesNotExists)
      }
    }
  }

  "The validate method of the provider" should {
    "throw OAuth2StateException if stateParam doesn't come back from provider" in new Context {
      implicit val req = FakeRequest(GET, "/")

      await(stateProvider.validate) must throwA[OAuth2StateException].like {
        case e => e.getMessage must startWith(StateHandler.ProviderStateDoesNotExists)
      }
    }
  }

  trait Context extends Scope {

    val csrfStateHandler = new CsrfStateHandler(Map("value" -> "signed cookie"))

    val userStateHandler = new UserStateHandler(Map("path" -> "/login"))

    val settings = StateProviderImplSettings()

    lazy val stateProvider = new StateProviderImpl(Set(csrfStateHandler, userStateHandler), settings)

  }

}
