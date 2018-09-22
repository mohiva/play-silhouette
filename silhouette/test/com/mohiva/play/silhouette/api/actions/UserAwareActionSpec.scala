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
package com.mohiva.play.silhouette.api.actions

import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions.UserAwareActionSpec._
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService, IdentityService }
import net.codingwell.scalaguice.ScalaModule
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.i18n.{ Lang, Langs, MessagesApi }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{ ControllerComponents, _ }
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.api.actions.UserAwareAction]].
 */
class UserAwareActionSpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

  "The `UserAwareAction` action" should {
    "invoke action without identity and authenticator if no authenticator could be found" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(None)

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain(messagesApi("without.identity.and.authenticator"))
      }
    }

    "invoke action without identity and authenticator if invalid authenticator was found" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator.copy(isValid = false)))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain(messagesApi("without.identity.and.authenticator"))
        there was one(env.authenticatorService).discard(any, any)(any)
      }
    }

    "invoke action with valid authenticator if no identity could be found" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain(messagesApi("without.identity.and.with.authenticator"))
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
      }
    }

    "invoke action with authenticator and identity" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain(messagesApi("with.identity.and.authenticator"))
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
      }
    }

    "use next request provider in the chain if first isn't responsible" in new InjectorContext with WithRequestProvider {
      new WithApplication(app) with Context {
        tokenRequestProvider.authenticate(any) returns Future.successful(None)
        basicAuthRequestProvider.authenticate(any) returns Future.successful(Some(identity.loginInfo))
        env.authenticatorService.retrieve(any) returns Future.successful(None)
        env.authenticatorService.create(any)(any) returns Future.successful(authenticator)
        env.authenticatorService.init(any)(any) answers { p => Future.successful(p.asInstanceOf[FakeAuthenticator#Value]) }
        env.authenticatorService.embed(any, any[Result])(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("with.identity.and.authenticator")
        there was one(env.authenticatorService).create(any)(any)
        there was one(env.authenticatorService).init(any)(any)
      }
    }

    "update an initialized authenticator if it was touched" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("with.identity.and.authenticator")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
      }
    }

    "do not update an initialized authenticator if it was not touched" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Right(authenticator)
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain(messagesApi("with.identity.and.authenticator"))
        there was one(env.authenticatorService).touch(any)
        there was no(env.authenticatorService).update(any, any)(any)
      }
    }

    "init an uninitialized authenticator" in new InjectorContext with WithRequestProvider {
      new WithApplication(app) with Context {
        tokenRequestProvider.authenticate(any) returns Future.successful(Some(identity.loginInfo))
        env.authenticatorService.retrieve(any) returns Future.successful(None)
        env.authenticatorService.create(any)(any) returns Future.successful(authenticator)
        env.authenticatorService.init(any)(any) answers { p => Future.successful(p.asInstanceOf[FakeAuthenticator#Value]) }
        env.authenticatorService.embed(any, any[Result])(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("with.identity.and.authenticator")
        there was one(env.authenticatorService).create(any)(any)
        there was one(env.authenticatorService).init(any)(any)
      }
    }

    "renew an initialized authenticator" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.renew(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.renewAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain(messagesApi("renewed"))
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).renew(any, any)(any)
        there was no(env.authenticatorService).update(any, any)(any)
      }
    }

    "renew an uninitialized authenticator" in new InjectorContext with WithRequestProvider {
      new WithApplication(app) with Context {
        tokenRequestProvider.authenticate(any) returns Future.successful(Some(identity.loginInfo))
        env.authenticatorService.retrieve(any) returns Future.successful(None)
        env.authenticatorService.create(any)(any) returns Future.successful(authenticator)
        env.authenticatorService.renew(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.renewAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("renewed")
        there was one(env.authenticatorService).create(any)(any)
        there was one(env.authenticatorService).renew(any, any)(any)
      }
    }

    "discard an initialized authenticator" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.discardAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain(messagesApi("discarded"))
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).discard(any, any)(any)
        there was no(env.authenticatorService).update(any, any)(any)
      }
    }

    "discard an uninitialized authenticator" in new InjectorContext with WithRequestProvider {
      new WithApplication(app) with Context {
        tokenRequestProvider.authenticate(any) returns Future.successful(Some(identity.loginInfo))
        env.authenticatorService.retrieve(any) returns Future.successful(None)
        env.authenticatorService.create(any)(any) returns Future.successful(authenticator)
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.discardAction(request)

        status(result) must equalTo(OK)
        there was one(env.authenticatorService).create(any)(any)
        there was one(env.authenticatorService).discard(any, any)(any)
      }
    }
  }

  "The `UserAwareRequestHandler`" should {
    "return status 401 if authentication was not successful" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(None)

        val result = controller.defaultHandler(request)

        status(result) must equalTo(UNAUTHORIZED)
        there was no(env.authenticatorService).touch(any)
        there was no(env.authenticatorService).update(any, any)(any)
      }
    }

    "return the user if authentication was successful" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.defaultHandler(request)

        status(result) must equalTo(OK)
        contentAsString(result) must */("providerID" -> "test") and */("providerKey" -> "1")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
      }
    }
  }

  /**
   * The injector context.
   */
  trait InjectorContext extends Scope {

    /**
     * The Silhouette environment.
     */
    lazy val env = Environment[UserAwareEnv](
      mock[IdentityService[UserAwareEnv#I]],
      mock[AuthenticatorService[UserAwareEnv#A]],
      Seq(),
      new EventBus
    )

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
      override def configure(): Unit = {
        bind[Silhouette[UserAwareEnv]].to[SilhouetteProvider[UserAwareEnv]]
        bind[Environment[UserAwareEnv]].toInstance(env)
        bind[UserAwareController]
      }
    }

    /**
     * The context.
     */
    trait Context {
      self: WithApplication =>

      /**
       * An identity.
       */
      lazy val identity = FakeIdentity(LoginInfo("test", "1"))

      /**
       * An authenticator.
       */
      lazy val authenticator = FakeAuthenticator(LoginInfo("test", "1"))

      /**
       * A fake request.
       */
      lazy implicit val request = FakeRequest()

      /**
       * The messages API.
       */
      lazy val messagesApi = app.injector.instanceOf[MessagesApi]

      /**
       * The implicit lang.
       */
      lazy implicit val lang: Lang = app.injector.instanceOf[Langs].availables.head

      /**
       * The user aware controller.
       */
      lazy implicit val controller = app.injector.instanceOf[UserAwareController]
    }
  }

  /**
   * Adds some request providers in scope.
   *
   * We add two providers in scope to test the chaining of this providers.
   */
  trait WithRequestProvider {
    self: InjectorContext =>

    /**
     * A mock that simulates a token request provider.
     */
    lazy val tokenRequestProvider = mock[RequestProvider]

    /**
     * A mock that simulates a basic auth request provider.
     */
    lazy val basicAuthRequestProvider = mock[RequestProvider]

    /**
     * A non request provider.
     */
    lazy val nonRequestProvider = mock[RequestProvider]

    /**
     * The Silhouette environment.
     */
    override lazy val env = Environment[UserAwareEnv](
      mock[IdentityService[FakeIdentity]],
      mock[AuthenticatorService[FakeAuthenticator]],
      Seq(
        tokenRequestProvider,
        basicAuthRequestProvider,
        nonRequestProvider
      ),
      new EventBus
    )
  }
}

/**
 * The companion object.
 */
object UserAwareActionSpec {

  /**
   * The environment type.
   */
  trait UserAwareEnv extends Env {
    type I = FakeIdentity
    type A = FakeAuthenticator
  }

  /**
   * A test identity.
   *
   * @param loginInfo The linked login info.
   */
  case class FakeIdentity(loginInfo: LoginInfo) extends Identity

  /**
   * A test authenticator.
   *
   * @param loginInfo The linked login info.
   */
  case class FakeAuthenticator(loginInfo: LoginInfo, isValid: Boolean = true) extends Authenticator

  /**
   * A user aware controller.
   *
   * @param silhouette The Silhouette stack.
   * @param components The Play controller components.
   */
  class UserAwareController @Inject() (
    silhouette: Silhouette[UserAwareEnv],
    components: ControllerComponents
  ) extends AbstractController(components) {

    /**
     * A user aware action.
     *
     * @return The result to send to the client.
     */
    def defaultAction = silhouette.UserAwareAction { implicit request =>
      if (request.identity.isDefined && request.authenticator.isDefined) {
        Ok("with.identity.and.authenticator")
      } else if (request.authenticator.isDefined) {
        Ok("without.identity.and.with.authenticator")
      } else {
        Ok("without.identity.and.authenticator")
      }
    }

    /**
     * A user aware renew action.
     *
     * @return The result to send to the client.
     */
    def renewAction = silhouette.UserAwareAction.async { implicit request =>
      request.authenticator match {
        case Some(a) => silhouette.env.authenticatorService.renew(a, Ok("renewed"))
        case None    => Future.successful(Ok("not.renewed"))
      }
    }

    /**
     * A user aware discard action.
     *
     * @return The result to send to the client.
     */
    def discardAction = silhouette.UserAwareAction.async { implicit request =>
      request.authenticator match {
        case Some(a) => silhouette.env.authenticatorService.discard(a, Ok("discarded"))
        case None    => Future.successful(Ok("not.discarded"))
      }
    }

    /**
     * A user aware request handler.
     */
    def defaultHandler = Action.async { implicit request =>
      silhouette.UserAwareRequestHandler { userAwareRequest =>
        Future.successful(HandlerResult(Ok, userAwareRequest.identity))
      }.map {
        case HandlerResult(r, Some(user)) => Ok(Json.toJson(user.loginInfo))
        case HandlerResult(r, None)       => Unauthorized
      }
    }
  }
}
