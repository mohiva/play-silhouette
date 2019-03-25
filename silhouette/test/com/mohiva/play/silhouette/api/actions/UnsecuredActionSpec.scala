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

import akka.actor.{ Actor, ActorSystem, Props }
import akka.testkit.TestProbe
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions.UnsecuredActionSpec._
import com.mohiva.play.silhouette.api.exceptions.NotAuthorizedException
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService, IdentityService }
import net.codingwell.scalaguice.ScalaModule
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Test case for the [[com.mohiva.play.silhouette.api.actions.UnsecuredActionSpec]].
 */
class UnsecuredActionSpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

  "The `UnsecuredAction` action" should {
    "grant access if no valid authenticator can be retrieved" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(None)

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("full.access")
      }
    }

    "grant access and discard authenticator if an invalid authenticator can be retrieved" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator.copy(isValid = false)))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("full.access")
        there was one(env.authenticatorService).discard(any, any)(any)
      }
    }

    "grant access and discard authenticator if no identity could be found for an authenticator" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

        val result = controller.defaultAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("full.access")
        there was one(env.authenticatorService).discard(any, any)(any)
      }
    }

    "display local not-authorized result if user is authenticated" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.actionWithErrorHandler(request)

        status(result) must equalTo(FORBIDDEN)
        contentAsString(result) must contain("local.not.authorized")
      }
    }

    "display global not-authorized result if user is authenticated" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.defaultAction(request)

        status(result) must equalTo(FORBIDDEN)
        contentAsString(result) must contain("global.not.authorized")
      }
    }
  }

  "The `UnsecuredRequestHandler`" should {
    "return status 403 if user is authenticated" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        val result = controller.defaultHandler(request)

        status(result) must equalTo(FORBIDDEN)
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
      }
    }

    "return the data if user is not authenticated" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(None)

        val result = controller.defaultHandler(request)

        status(result) must equalTo(OK)
        contentAsString(result) must be equalTo "data"
        there was no(env.authenticatorService).touch(any)
        there was no(env.authenticatorService).update(any, any)(any)
      }
    }
  }

  "The `exceptionHandler` method of the UnsecuredErrorHandler" should {
    "translate an ForbiddenException into a 403 Forbidden result" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(None)
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }

        val failed = Future.failed(new NotAuthorizedException("Access denied"))
        val result = controller.recover(failed)

        status(result) must equalTo(FORBIDDEN)
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
    lazy val env = Environment[UnsecuredEnv](
      mock[IdentityService[UnsecuredEnv#I]],
      mock[AuthenticatorService[UnsecuredEnv#A]],
      Seq(),
      new EventBus
    )

    /**
     * The guice application builder.
     */
    lazy val app = new GuiceApplicationBuilder()
      .bindings(new GuiceModule)
      .overrides(bind[UnsecuredErrorHandler].to[GlobalUnsecuredErrorHandler])
      .build()

    /**
     * The guice module.
     */
    class GuiceModule extends ScalaModule {
      override def configure(): Unit = {
        bind[Environment[UnsecuredEnv]].toInstance(env)
        bind[Silhouette[UnsecuredEnv]].to[SilhouetteProvider[UnsecuredEnv]]
        bind[UnsecuredController]
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
       * The unsecured controller.
       */
      lazy implicit val controller = app.injector.instanceOf[UnsecuredController]

      /**
       * The Play actor system.
       */
      lazy implicit val system = app.injector.instanceOf[ActorSystem]

      /**
       * The test probe.
       */
      lazy val theProbe = TestProbe()

      /**
       * Executes a block after event bus initialization, so that the event can be handled inside the given block.
       *
       * @param ct The class tag of the event.
       * @tparam T The type of the event to handle.
       * @return The result of the block.
       */
      def withEvent[T <: SilhouetteEvent](block: => Any)(implicit ct: ClassTag[T]) = {
        val listener = system.actorOf(Props(new Actor {
          def receive = {
            case e: T => theProbe.ref ! e
          }
        }))

        env.eventBus.subscribe(listener, ct.runtimeClass.asInstanceOf[Class[T]])

        block
      }
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
    override lazy val env = Environment[UnsecuredEnv](
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
object UnsecuredActionSpec {

  /**
   * The environment type.
   */
  trait UnsecuredEnv extends Env {
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
   * The global unsecured error handler.
   */
  class GlobalUnsecuredErrorHandler extends UnsecuredErrorHandler {

    /**
     * Called when a user is authenticated but not authorized.
     *
     * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
     *
     * @param request The request header.
     * @return The result to send to the client.
     */
    def onNotAuthorized(implicit request: RequestHeader) = {
      Future.successful(Forbidden("global.not.authorized"))
    }
  }

  /**
   * An unsecured controller.
   *
   * @param silhouette The Silhouette stack.
   * @param components The Play controller components.
   */
  class UnsecuredController @Inject() (
    silhouette: Silhouette[UnsecuredEnv],
    components: ControllerComponents
  ) extends AbstractController(components) {

    /**
     * A local error handler.
     */
    lazy val errorHandler = new UnsecuredErrorHandler {
      override def onNotAuthorized(implicit request: RequestHeader) = {
        Future.successful(Forbidden("local.not.authorized"))
      }
    }

    /**
     * An unsecured action.
     *
     * @return The result to send to the client.
     */
    def defaultAction = silhouette.UnsecuredAction {
      Ok("full.access")
    }

    /**
     * An unsecured action with a custom error handler.
     *
     * @return The result to send to the client.
     */
    def actionWithErrorHandler = silhouette.UnsecuredAction(errorHandler) { Ok("full.access") }

    /**
     * An unsecured request handler.
     */
    def defaultHandler = Action.async { implicit request =>
      silhouette.UnsecuredRequestHandler { _ =>
        Future.successful(HandlerResult(Ok, Some("data")))
      }.map {
        case HandlerResult(r, Some(data)) => Ok(data)
        case HandlerResult(r, None)       => Forbidden
      }
    }

    /**
     * Method to test the `exceptionHandler` method of the [[UnsecuredErrorHandler]].
     *
     * @param f The future to recover from.
     * @param request The request header.
     * @return The result to send to the client.
     */
    def recover(f: Future[Result])(implicit request: RequestHeader): Future[Result] = {
      f.recoverWith(silhouette.UnsecuredAction.requestHandler.errorHandler.exceptionHandler)
    }
  }
}
