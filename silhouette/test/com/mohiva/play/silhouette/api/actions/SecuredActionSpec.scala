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
import com.mohiva.play.silhouette.api.actions.SecuredActionSpec._
import com.mohiva.play.silhouette.api.exceptions.{ NotAuthenticatedException, NotAuthorizedException }
import com.mohiva.play.silhouette.api.services.{ AuthenticatorResult, AuthenticatorService, IdentityService }
import net.codingwell.scalaguice.ScalaModule
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag

/**
 * Test case for the [[com.mohiva.play.silhouette.api.actions.SecuredActionSpec]].
 */
class SecuredActionSpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

  "The `SecuredAction` action" should {
    "restrict access if no valid authenticator can be retrieved" in new InjectorContext {
      new WithApplication(app) with Context {
        withEvent[NotAuthenticatedEvent] {
          env.authenticatorService.retrieve(any) returns Future.successful(None)

          val result = controller.defaultAction(request)

          status(result) must equalTo(UNAUTHORIZED)
          contentAsString(result) must contain("global.not.authenticated")
          theProbe.expectMsg(500 millis, NotAuthenticatedEvent(request))
        }
      }
    }

    "restrict access and discard authenticator if an invalid authenticator can be retrieved" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator.copy(isValid = false)))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }

        withEvent[NotAuthenticatedEvent] {
          val result = controller.defaultAction(request)

          status(result) must equalTo(UNAUTHORIZED)
          contentAsString(result) must contain("global.not.authenticated")
          there was one(env.authenticatorService).discard(any, any)(any)
          theProbe.expectMsg(500 millis, NotAuthenticatedEvent(request))
        }
      }
    }

    "restrict access and discard authenticator if no identity could be found for an authenticator" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

        withEvent[NotAuthenticatedEvent] {
          val result = controller.defaultAction(request)

          status(result) must equalTo(UNAUTHORIZED)
          contentAsString(result) must contain("global.not.authenticated")
          there was one(env.authenticatorService).discard(any, any)(any)
          theProbe.expectMsg(500 millis, NotAuthenticatedEvent(request))
        }
      }
    }

    "display local not-authenticated result if user isn't authenticated[authorization and error handler]" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

        val result = controller.actionWithAuthorizationAndErrorHandler(request)

        status(result) must equalTo(UNAUTHORIZED)
        contentAsString(result) must contain("local.not.authenticated")
      }
    }

    "display local not-authenticated result if user isn't authenticated[error handler only]" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

        val result = controller.actionWithErrorHandler(request)

        status(result) must equalTo(UNAUTHORIZED)
        contentAsString(result) must contain("local.not.authenticated")
      }
    }

    "display global not-authenticated result if user isn't authenticated" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

        val result = controller.defaultAction(request)

        status(result) must equalTo(UNAUTHORIZED)
        contentAsString(result) must contain("global.not.authenticated")
      }
    }

    "restrict access and update authenticator if a user is authenticated but not authorized" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))
        authorization.isAuthorized(any, any)(any) returns Future.successful(false)

        withEvent[NotAuthorizedEvent[FakeIdentity]] {
          val result = controller.actionWithAuthorization(request)

          status(result) must equalTo(FORBIDDEN)
          contentAsString(result) must contain("global.not.authorized")
          there was one(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, NotAuthorizedEvent(identity, request))
        }
      }
    }

    "display local not-authorized result if user isn't authorized" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))
        authorization.isAuthorized(any, any)(any) returns Future.successful(false)

        val result = controller.actionWithAuthorizationAndErrorHandler(request)

        status(result) must equalTo(FORBIDDEN)
        contentAsString(result) must contain("local.not.authorized")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
      }
    }

    "display global not-authorized result if user isn't authorized" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))
        authorization.isAuthorized(any, any)(any) returns Future.successful(false)

        val result = controller.actionWithAuthorization(request)

        status(result) must equalTo(FORBIDDEN)
        contentAsString(result) must contain("global.not.authorized")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
      }
    }

    "invoke action without authorization if user is authenticated" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.defaultAction(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("full.access")
          there was one(env.authenticatorService).touch(any)
          there was one(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
      }
    }

    "invoke action with authorization if user is authenticated but not authorized" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.actionWithAuthorization(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("full.access")
          there was one(env.authenticatorService).touch(any)
          there was one(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
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

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.actionWithAuthorization(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("full.access")
          there was one(env.authenticatorService).create(any)(any)
          there was one(env.authenticatorService).init(any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
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

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.actionWithAuthorization(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("full.access")
          there was one(env.authenticatorService).touch(any)
          there was one(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
      }
    }

    "do not update an initialized authenticator if it was not touched" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Right(authenticator)
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.actionWithAuthorization(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("full.access")
          there was one(env.authenticatorService).touch(any)
          there was no(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
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

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.actionWithAuthorization(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("full.access")
          there was one(env.authenticatorService).create(any)(any)
          there was one(env.authenticatorService).init(any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
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

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.renewAction(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("renewed")
          there was one(env.authenticatorService).touch(any)
          there was one(env.authenticatorService).renew(any, any)(any)
          there was no(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
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

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.renewAction(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("renewed")
          there was one(env.authenticatorService).create(any)(any)
          there was one(env.authenticatorService).renew(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
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

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.discardAction(request)

          status(result) must equalTo(OK)
          contentAsString(result) must contain("discarded")
          there was one(env.authenticatorService).touch(any)
          there was one(env.authenticatorService).discard(any, any)(any)
          there was no(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
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

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.discardAction(request)

          status(result) must equalTo(OK)
          there was one(env.authenticatorService).create(any)(any)
          there was one(env.authenticatorService).discard(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request))
        }
      }
    }

    "handle an Ajax request" in new InjectorContext {
      new WithApplication(app) with Context {
        implicit val req = FakeRequest().withHeaders("Accept" -> "application/json")

        env.authenticatorService.retrieve(any) returns Future.successful(Some(authenticator))
        env.authenticatorService.touch(any) returns Left(authenticator)
        env.authenticatorService.update(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }
        env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

        withEvent[AuthenticatedEvent[FakeIdentity]] {
          val result = controller.defaultAction(req)

          status(result) must equalTo(OK)
          contentType(result) must beSome("application/json")
          contentAsString(result) must /("result" -> "full.access")
          there was one(env.authenticatorService).touch(any)
          there was one(env.authenticatorService).update(any, any)(any)
          theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, req))
        }
      }
    }
  }

  "The `SecuredRequestHandler`" should {
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

  "The `exceptionHandler` method of the SecuredErrorHandler" should {
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

    "translate an UnauthorizedException into a 401 Unauthorized result" in new InjectorContext {
      new WithApplication(app) with Context {
        env.authenticatorService.retrieve(any) returns Future.successful(None)
        env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
          Future.successful(AuthenticatorResult(a.asInstanceOf[Array[Any]](1).asInstanceOf[Result]))
        }

        val failed = Future.failed(new NotAuthenticatedException("Not authenticated"))
        val result = controller.recover(failed)

        status(result) must equalTo(UNAUTHORIZED)
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
    lazy val env = Environment[SecuredEnv](
      mock[IdentityService[SecuredEnv#I]],
      mock[AuthenticatorService[SecuredEnv#A]],
      Seq(),
      new EventBus
    )

    /**
     * An authorization mock.
     */
    lazy val authorization = {
      val a = mock[Authorization[SecuredEnv#I, SecuredEnv#A]]
      a.isAuthorized(any, any)(any) returns Future.successful(true)
      a
    }

    /**
     * The guice application builder.
     */
    lazy val app = new GuiceApplicationBuilder()
      .bindings(new GuiceModule)
      .overrides(bind[SecuredErrorHandler].to[GlobalSecuredErrorHandler])
      .build()

    /**
     * The guice module.
     */
    class GuiceModule extends ScalaModule {
      override def configure(): Unit = {
        bind[Environment[SecuredEnv]].toInstance(env)
        bind[Authorization[SecuredEnv#I, SecuredEnv#A]].toInstance(authorization)
        bind[Silhouette[SecuredEnv]].to[SilhouetteProvider[SecuredEnv]]
        bind[SecuredController]
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
       * The secured controller.
       */
      lazy implicit val controller = app.injector.instanceOf[SecuredController]

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
    override lazy val env = Environment[SecuredEnv](
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
object SecuredActionSpec {

  /**
   * The environment type.
   */
  trait SecuredEnv extends Env {
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
   * A simple authorization class.
   *
   * @param isAuthorized True if the access is authorized, false otherwise.
   */
  case class SimpleAuthorization(isAuthorized: Boolean = true) extends Authorization[FakeIdentity, FakeAuthenticator] {

    /**
     * Checks whether the user is authorized to execute an action or not.
     *
     * @param identity The current identity instance.
     * @param authenticator The current authenticator instance.
     * @param request The current request header.
     * @tparam B The type of the request body.
     * @return True if the user is authorized, false otherwise.
     */
    def isAuthorized[B](identity: FakeIdentity, authenticator: FakeAuthenticator)(
      implicit
      request: Request[B]): Future[Boolean] = {

      Future.successful(isAuthorized)
    }
  }

  /**
   * The global secured error handler.
   */
  class GlobalSecuredErrorHandler extends SecuredErrorHandler {

    /**
     * Called when a user is not authenticated.
     *
     * As defined by RFC 2616, the status code of the response should be 401 Unauthorized.
     *
     * @param request The request header.
     * @return The result to send to the client.
     */
    def onNotAuthenticated(implicit request: RequestHeader): Future[Result] = {
      Future.successful(Unauthorized("global.not.authenticated"))
    }

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
   * A secured controller.
   *
   * @param silhouette The Silhouette stack.
   * @param authorization An authorization implementation.
   * @param components The Play controller components.
   */
  class SecuredController @Inject() (
    silhouette: Silhouette[SecuredEnv],
    authorization: Authorization[FakeIdentity, FakeAuthenticator],
    components: ControllerComponents
  ) extends AbstractController(components) {

    /**
     * A local error handler.
     */
    lazy val errorHandler = new SecuredErrorHandler {
      override def onNotAuthenticated(implicit request: RequestHeader) = {
        Future.successful(Unauthorized("local.not.authenticated"))
      }
      override def onNotAuthorized(implicit request: RequestHeader) = {
        Future.successful(Forbidden("local.not.authorized"))
      }
    }

    /**
     * A secured action.
     *
     * @return The result to send to the client.
     */
    def defaultAction = silhouette.SecuredAction { implicit request =>
      render {
        case Accepts.Json() => Ok(Json.obj("result" -> "full.access"))
        case Accepts.Html() => Ok("full.access")
      }
    }

    /**
     * A secured action with an authorization and a custom error handler.
     *
     * @return The result to send to the client.
     */
    def actionWithAuthorizationAndErrorHandler = silhouette.SecuredAction(authorization)(errorHandler) { Ok }

    /**
     * A secured action with a custom error handler.
     *
     * @return The result to send to the client.
     */
    def actionWithErrorHandler = silhouette.SecuredAction(errorHandler) { Ok("full.access") }

    /**
     * A secured action with authorization.
     *
     * @return The result to send to the client.
     */
    def actionWithAuthorization = silhouette.SecuredAction(authorization) { Ok("full.access") }

    /**
     * A secured renew action.
     *
     * @return The result to send to the client.
     */
    def renewAction = silhouette.SecuredAction.async { implicit request =>
      silhouette.env.authenticatorService.renew(request.authenticator, Ok("renewed"))
    }

    /**
     * A secured discard action.
     *
     * @return The result to send to the client.
     */
    def discardAction = silhouette.SecuredAction.async { implicit request =>
      silhouette.env.authenticatorService.discard(request.authenticator, Ok("discarded"))
    }

    /**
     * A secured request handler.
     */
    def defaultHandler = Action.async { implicit request =>
      silhouette.SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }.map {
        case HandlerResult(r, Some(user)) => Ok(Json.toJson(user.loginInfo))
        case HandlerResult(r, None)       => Unauthorized
      }
    }

    /**
     * Method to test the `exceptionHandler` method of the [[SecuredErrorHandler]].
     *
     * @param f The future to recover from.
     * @param request The request header.
     * @return The result to send to the client.
     */
    def recover(f: Future[Result])(implicit request: RequestHeader): Future[Result] = {
      f.recoverWith(silhouette.SecuredAction.requestHandler.errorHandler.exceptionHandler)
    }
  }
}
