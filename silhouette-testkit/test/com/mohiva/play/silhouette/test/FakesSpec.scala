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
package com.mohiva.play.silhouette.test

import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.authenticators.jwt.{JWTAuthenticator, JWTAuthenticatorService}
import com.mohiva.play.silhouette.test.FakesSpec._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Controller
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

/**
 * Test case for the [[com.mohiva.play.silhouette.test]] helpers.
 */
class FakesSpec extends PlaySpecification with JsonMatchers {

  "The `retrieve` method of the `FakeIdentityService`" should {
    "return the identity for the given login info" in {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      val service = new FakeIdentityService[FakeIdentity](loginInfo -> identity)

      await(service.retrieve(loginInfo)) must beSome(identity)
    }

    "return None if no identity could be found for the given login info" in {
      val loginInfo = LoginInfo("test", "test")
      val service = new FakeIdentityService[FakeIdentity]()

      await(service.retrieve(loginInfo)) must beNone
    }
  }

  "The `find` method of the `FakeAuthenticatorRepository`" should {
    "return an authenticator for the given ID" in {
      val loginInfo = LoginInfo("test", "test")
      val authenticator = new FakeAuthenticator(loginInfo, "test")
      val dao = new FakeAuthenticatorRepository[FakeAuthenticator]()

      await(dao.add(authenticator))

      await(dao.find("test")) must beSome(authenticator)
    }

    "return None if no authenticator could be found for the given ID" in {
      val dao = new FakeAuthenticatorRepository[FakeAuthenticator]()

      await(dao.find("test")) must beNone
    }
  }

  "The `add` method of the `FakeAuthenticatorRepository`" should {
    "add an authenticator" in {
      val loginInfo = LoginInfo("test", "test")
      val authenticator = new FakeAuthenticator(loginInfo)
      val dao = new FakeAuthenticatorRepository[FakeAuthenticator]()

      await(dao.add(authenticator)) must be equalTo authenticator
    }
  }

  "The `update` method of the `FakeAuthenticatorRepository`" should {
    "update an authenticator" in {
      val loginInfo = LoginInfo("test", "test")
      val authenticator = new FakeAuthenticator(loginInfo)
      val dao = new FakeAuthenticatorRepository[FakeAuthenticator]()

      await(dao.update(authenticator)) must be equalTo authenticator
    }
  }

  "The `remove` method of the `FakeAuthenticatorRepository`" should {
    "remove an authenticator" in {
      val loginInfo = LoginInfo("test", "test")
      val authenticator = new FakeAuthenticator(loginInfo, "test")
      val dao = new FakeAuthenticatorRepository[FakeAuthenticator]()

      await(dao.add(authenticator))
      await(dao.find("test")) must beSome(authenticator)
      await(dao.remove("test"))
      await(dao.find("test")) must beNone
    }
  }

  "The `FakeAuthenticatorService` factory" should {
    "return a `SessionAuthenticatorService`" in {
      FakeAuthenticatorService[SessionAuthenticator]() must beAnInstanceOf[SessionAuthenticatorService]
    }

    "return a `CookieAuthenticatorService`" in new WithApplication {
      FakeAuthenticatorService[CookieAuthenticator]() must beAnInstanceOf[CookieAuthenticatorService]
    }

    "return a `BearerTokenAuthenticatorService`" in {
      FakeAuthenticatorService[BearerTokenAuthenticator]() must beAnInstanceOf[BearerTokenAuthenticatorService]
    }

    "return a `JWTAuthenticatorService`" in {
      FakeAuthenticatorService[JWTAuthenticator]() must beAnInstanceOf[JWTAuthenticatorService]
    }

    "return a `DummyAuthenticatorService`" in {
      FakeAuthenticatorService[DummyAuthenticator]() must beAnInstanceOf[DummyAuthenticatorService]
    }
  }

  "The `FakeAuthenticator` factory" should {
    "return a `SessionAuthenticator`" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[SessionEnv](Seq(loginInfo -> identity))
      implicit val request = FakeRequest()

      FakeAuthenticator(loginInfo) must beAnInstanceOf[SessionAuthenticator]
    }

    "return a `CookieAuthenticator`" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))
      implicit val request = FakeRequest()

      FakeAuthenticator(loginInfo) must beAnInstanceOf[CookieAuthenticator]
    }

    "return a `BearerTokenAuthenticator`" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[BearerTokenEnv](Seq(loginInfo -> identity))
      implicit val request = FakeRequest()

      FakeAuthenticator(loginInfo) must beAnInstanceOf[BearerTokenAuthenticator]
    }

    "return a `JWTAuthenticator`" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[JWTEnv](Seq(loginInfo -> identity))
      implicit val request = FakeRequest()

      FakeAuthenticator(loginInfo) must beAnInstanceOf[JWTAuthenticator]
    }

    "return a `DummyAuthenticator`" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[DummyEnv](Seq(loginInfo -> identity))
      implicit val request = FakeRequest()

      FakeAuthenticator(loginInfo) must beAnInstanceOf[DummyAuthenticator]
    }
  }

  "The `securedAction` method of the `SecuredController`" should {
    "return a 401 status code if no authenticator was found" in new InjectorContext {
      new WithApplication(app) {
        val loginInfo = LoginInfo("test", "test")
        val identity = FakeIdentity(loginInfo)
        val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))
        val request = FakeRequest()

        val controller = app.injector.instanceOf[SecuredController]
        val result = controller.defaultSecuredAction(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }

    "return a 401 status code if authenticator but no identity was found" in new InjectorContext {
      new WithApplication(app) {
        val loginInfo = LoginInfo("test", "test")
        val identity = FakeIdentity(loginInfo)
        implicit val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))
        val request = FakeRequest().withAuthenticator(LoginInfo("invalid", "invalid"))

        val controller = app.injector.instanceOf[SecuredController]
        val result = controller.defaultSecuredAction(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }

    "return a 200 status code if authenticator and identity was found" in new InjectorContext {
      new WithApplication(app) {
        val loginInfo = LoginInfo("test", "test")
        val identity = FakeIdentity(loginInfo)
        implicit val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))
        val request = FakeRequest().withAuthenticator(loginInfo)

        val controller = app.injector.instanceOf[SecuredController]
        val result = controller.defaultSecuredAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must */("providerID" -> "test") and */("providerKey" -> "test")
      }
    }
  }

  "The `userAwareAction` method of the `SecuredController`" should {
    "return a 401 status code if no authenticator was found" in new InjectorContext {
      new WithApplication(app) {
        val loginInfo = LoginInfo("test", "test")
        val identity = FakeIdentity(loginInfo)
        val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))
        val request = FakeRequest()

        val controller = app.injector.instanceOf[SecuredController]
        val result = controller.defaultUserAwareAction(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }

    "return a 401 status code if authenticator but no identity was found" in new InjectorContext {
      new WithApplication(app) {
        val loginInfo = LoginInfo("test", "test")
        val identity = FakeIdentity(loginInfo)
        implicit val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))
        val request = FakeRequest().withAuthenticator(LoginInfo("invalid", "invalid"))

        val controller = app.injector.instanceOf[SecuredController]
        val result = controller.defaultUserAwareAction(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }

    "return a 200 status code if authenticator and identity was found" in new InjectorContext {
      new WithApplication(app) {
        val loginInfo = LoginInfo("test", "test")
        val identity = FakeIdentity(loginInfo)

        implicit val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))
        val request = FakeRequest().withAuthenticator(loginInfo)

        val controller = app.injector.instanceOf[SecuredController]
        val result = controller.defaultUserAwareAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must */("providerID" -> "test") and */("providerKey" -> "test")
      }
    }
  }

  /**
   * The injector context.
   */
  trait InjectorContext extends Scope {

    /**
     * A login info.
     */
    val loginInfo = LoginInfo("test", "test")

    /**
     * An identity.
     */
    val identity = FakeIdentity(loginInfo)

    /**
     * The Silhouette environment.
     */
    implicit val env = FakeEnvironment[CookieEnv](Seq(loginInfo -> identity))

    /**
     * The guice application builder.
     */
    lazy val app = new GuiceApplicationBuilder()
      .bindings(new GuiceModule)
      .build()

    /**
     * The guice module.
     */
    class GuiceModule extends ScalaModule {
      def configure(): Unit = {
        bind[Silhouette[CookieEnv]].to[SilhouetteProvider[CookieEnv]]
        bind[Environment[CookieEnv]].toInstance(env)
        bind[SecuredController]
      }
    }
  }
}

/**
 * The companion object.
 */
object FakesSpec {

  /**
   * The cookie environment.
   */
  trait CookieEnv extends Env {
    type I = FakeIdentity
    type A = CookieAuthenticator
  }

  /**
   * The session environment.
   */
  trait SessionEnv extends Env {
    type I = FakeIdentity
    type A = SessionAuthenticator
  }

  /**
   * The JWT environment.
   */
  trait JWTEnv extends Env {
    type I = FakeIdentity
    type A = JWTAuthenticator
  }

  /**
   * The bearer token environment.
   */
  trait BearerTokenEnv extends Env {
    type I = FakeIdentity
    type A = BearerTokenAuthenticator
  }

  /**
   * The dummy environment.
   */
  trait DummyEnv extends Env {
    type I = FakeIdentity
    type A = DummyAuthenticator
  }

  /**
   * A secured controller implementation.
   *
   * @param messagesApi The Play messages API.
   * @param silhouette The Silhouette stack.
   */
  class SecuredController @Inject() (
    val messagesApi: MessagesApi,
    val silhouette: Silhouette[CookieEnv])
    extends Controller {

    /**
     * A secured action.
     *
     * @return The result to send to the client.
     */
    def defaultSecuredAction = silhouette.SecuredAction { implicit request =>
      Ok(Json.toJson(request.identity.loginInfo))
    }

    /**
     * A user aware action.
     *
     * @return The result to send to the client.
     */
    def defaultUserAwareAction = silhouette.UserAwareAction { implicit request =>
      request.identity match {
        case Some(identity) => Ok(Json.toJson(identity.loginInfo))
        case None           => Unauthorized
      }
    }
  }
}
