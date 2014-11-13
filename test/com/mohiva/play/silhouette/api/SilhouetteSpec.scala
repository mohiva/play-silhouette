/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.api

import akka.actor.{ Actor, Props }
import akka.testkit.TestProbe
import com.mohiva.play.silhouette.api.exceptions.{ AccessDeniedException, AuthenticationException }
import com.mohiva.play.silhouette.api.services.{ AuthenticatorService, IdentityService }
import com.mohiva.play.silhouette.test.{ FakeAuthenticator, FakeIdentity }
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.GlobalSettings
import play.api.i18n.{ Lang, Messages }
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{ Action, RequestHeader, Result }
import play.api.test.{ FakeApplication, FakeRequest, PlaySpecification, WithApplication }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
 * Test case for the [[com.mohiva.play.silhouette.api.Silhouette]] base controller.
 */
class SilhouetteSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `SecuredAction` action" should {
    "restrict access if no valid authenticator can be retrieved" in new WithDefaultGlobal {
      withEvent[NotAuthenticatedEvent] {
        env.authenticatorService.retrieve returns Future.successful(None)

        val controller = new SecuredController(env)
        val result = controller.securedAction(request)

        status(result) must equalTo(UNAUTHORIZED)
        contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
        theProbe.expectMsg(500 millis, NotAuthenticatedEvent(request, lang))
      }
    }

    "restrict access and discard authenticator if an invalid authenticator can be retrieved" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator.copy(isValid = false)))
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }

      withEvent[NotAuthenticatedEvent] {
        val controller = new SecuredController(env)
        val result = controller.securedAction(request)

        status(result) must equalTo(UNAUTHORIZED)
        contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
        there was one(env.authenticatorService).discard(any, any)(any)
        theProbe.expectMsg(500 millis, NotAuthenticatedEvent(request, lang))
      }
    }

    "restrict access and discard authenticator if no identity could be found for an authenticator" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      withEvent[NotAuthenticatedEvent] {
        val controller = new SecuredController(env)
        val result = controller.securedAction(request)

        status(result) must equalTo(UNAUTHORIZED)
        contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
        there was one(env.authenticatorService).discard(any, any)(any)
        theProbe.expectMsg(500 millis, NotAuthenticatedEvent(request, lang))
      }
    }

    "display local not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env) {
        override def notAuthenticated(request: RequestHeader): Option[Future[Result]] = {
          Some(Future.successful(Unauthorized("local.not.authenticated")))
        }
      }

      val result = controller.securedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("local.not.authenticated")
    }

    "display global not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.securedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("global.not.authenticated")
    }

    "display fallback message if user isn't authenticated and fallback methods aren't implemented" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.securedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "restrict access and update authenticator if a user is authenticated but not authorized" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      withEvent[NotAuthorizedEvent[FakeIdentity]] {
        val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false))
        val result = controller.securedActionWithAuthorization(request)

        status(result) must equalTo(FORBIDDEN)
        contentAsString(result) must contain(Messages("silhouette.not.authorized"))
        there was one(env.authenticatorService).update(any, any)(any)
        theProbe.expectMsg(500 millis, NotAuthorizedEvent(identity, request, lang))
      }
    }

    "display local not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false)) {
        override def notAuthorized(request: RequestHeader): Option[Future[Result]] = {
          Some(Future.successful(Forbidden("local.not.authorized")))
        }
      }

      val result = controller.securedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("local.not.authorized")
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).update(any, any)(any)
    }

    "display global not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false))
      val result = controller.securedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("global.not.authorized")
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).update(any, any)(any)
    }

    "display fallback message if user isn't authorized and fallback methods aren't implemented" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env, SimpleAuthorization(isAuthorized = false))
      val result = controller.securedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authorized"))
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).update(any, any)(any)
    }

    "invoke action without authorization if user is authenticated" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      withEvent[AuthenticatedEvent[FakeIdentity]] {
        val controller = new SecuredController(env)
        val result = controller.securedAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("full.access")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
        theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request, lang))
      }
    }

    "invoke action with authorization if user is authenticated but not authorized" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      withEvent[AuthenticatedEvent[FakeIdentity]] {
        val controller = new SecuredController(env)
        val result = controller.securedActionWithAuthorization(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("full.access")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
        theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request, lang))
      }
    }

    "do not update the authenticator if it was not touched" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Right(authenticator)
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      withEvent[AuthenticatedEvent[FakeIdentity]] {
        val controller = new SecuredController(env)
        val result = controller.securedActionWithAuthorization(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("full.access")
        there was one(env.authenticatorService).touch(any)
        there was no(env.authenticatorService).update(any, any)(any)
        theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request, lang))
      }
    }

    "renew an authenticator" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.renew(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      withEvent[AuthenticatedEvent[FakeIdentity]] {
        val controller = new SecuredController(env)
        val result = controller.securedRenewAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("renewed")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).renew(any, any)(any)
        there was no(env.authenticatorService).update(any, any)(any)
        theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request, lang))
      }
    }

    "discard an authenticator" in new WithSecuredGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      withEvent[AuthenticatedEvent[FakeIdentity]] {
        val controller = new SecuredController(env)
        val result = controller.securedDiscardAction(request)

        status(result) must equalTo(OK)
        contentAsString(result) must contain("discarded")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).discard(any, any)(any)
        there was no(env.authenticatorService).update(any, any)(any)
        theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, request, lang))
      }
    }

    "handle an Ajax request" in new WithSecuredGlobal {
      implicit val req = FakeRequest().withHeaders("Accept" -> "application/json")

      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      withEvent[AuthenticatedEvent[FakeIdentity]] {
        val controller = new SecuredController(env)
        val result = controller.securedAction(req)

        status(result) must equalTo(OK)
        contentType(result) must beSome("application/json")
        contentAsString(result) must /("result" -> "full.access")
        there was one(env.authenticatorService).touch(any)
        there was one(env.authenticatorService).update(any, any)(any)
        theProbe.expectMsg(500 millis, AuthenticatedEvent(identity, req, lang))
      }
    }
  }

  "The `SecureRequestHandler`" should {
    "return status 401 if authentication was not successful" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.securedRequestHandler(request)

      status(result) must equalTo(UNAUTHORIZED)
      there was no(env.authenticatorService).touch(any)
      there was no(env.authenticatorService).update(any, any)(any)
    }

    "return the user if authentication was successful" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.securedRequestHandler(request)

      status(result) must equalTo(OK)
      contentAsString(result) must */("providerID" -> "test") and */("providerKey" -> "1")
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).update(any, any)(any)
    }
  }

  "The `UserAwareAction` action" should {
    "invoke action without identity and authenticator if no authenticator could be found" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("without.identity.and.authenticator"))
    }

    "invoke action without identity and authenticator if invalid authenticator was found" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator.copy(isValid = false)))
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("without.identity.and.authenticator"))
      there was one(env.authenticatorService).discard(any, any)(any)
    }

    "invoke action with valid authenticator if no identity could be found" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("without.identity.and.with.authenticator"))
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).update(any, any)(any)
    }

    "invoke action with authenticator and identity" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("with.identity.and.authenticator"))
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).update(any, any)(any)
    }

    "do not update the authenticator if it was not touched" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Right(authenticator)
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("with.identity.and.authenticator"))
      there was one(env.authenticatorService).touch(any)
      there was no(env.authenticatorService).update(any, any)(any)
    }

    "renew an authenticator" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.renew(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.userAwareRenewAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("renewed"))
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).renew(any, any)(any)
      there was no(env.authenticatorService).update(any, any)(any)
    }

    "discard an authenticator" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.userAwareDiscardAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("discarded"))
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).discard(any, any)(any)
      there was no(env.authenticatorService).update(any, any)(any)
    }
  }

  "The `UserAwareRequestHandler`" should {
    "return status 401 if authentication was not successful" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)

      val controller = new SecuredController(env)
      val result = controller.userAwareRequestHandler(request)

      status(result) must equalTo(UNAUTHORIZED)
      there was no(env.authenticatorService).touch(any)
      there was no(env.authenticatorService).update(any, any)(any)
    }

    "return the user if authentication was successful" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(Some(authenticator))
      env.authenticatorService.touch(any) returns Left(authenticator)
      env.authenticatorService.update(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }
      env.identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(env)
      val result = controller.userAwareRequestHandler(request)

      status(result) must equalTo(OK)
      contentAsString(result) must */("providerID" -> "test") and */("providerKey" -> "1")
      there was one(env.authenticatorService).touch(any)
      there was one(env.authenticatorService).update(any, any)(any)
    }
  }

  "The `exceptionHandler` method" should {
    "translate an AccessDeniedException into a 403 Forbidden result" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }

      val controller = new SecuredController(env)
      val failed = Future.failed(new AccessDeniedException("Access denied"))
      val result = controller.recover(failed)

      status(result) must equalTo(FORBIDDEN)
    }

    "translate an AuthenticationException into a 401 Unauthorized result" in new WithDefaultGlobal {
      env.authenticatorService.retrieve returns Future.successful(None)
      env.authenticatorService.discard(any, any)(any) answers { (a, m) =>
        a.asInstanceOf[Array[Any]](1).asInstanceOf[Future[Result]]
      }

      val controller = new SecuredController(env)
      val failed = Future.failed(new AuthenticationException("Not authenticated"))
      val result = controller.recover(failed)

      status(result) must equalTo(UNAUTHORIZED)
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {
    self: WithApplication =>

    /**
     * The Silhouette environment.
     */
    lazy val env = Environment[FakeIdentity, FakeAuthenticator](
      mock[IdentityService[FakeIdentity]],
      mock[AuthenticatorService[FakeAuthenticator]],
      Map(),
      new EventBus
    )

    /**
     * An identity.
     */
    lazy val identity = new FakeIdentity(LoginInfo("test", "1"))

    /**
     * An authenticator.
     */
    lazy val authenticator = new FakeAuthenticator(LoginInfo("test", "1"))

    /**
     * A fake request.
     */
    lazy implicit val request = FakeRequest()

    /**
     * A language.
     */
    lazy val lang = Lang.defaultLang

    /**
     * The Play actor system.
     */
    lazy implicit val system = Akka.system

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

      env.eventBus.subscribe(listener, ct.runtimeClass)

      block
    }
  }

  /**
   * Runs a fake application with a secured global object.
   */
  class WithSecuredGlobal extends WithApplication(FakeApplication(withGlobal = Some(new GlobalSettings with SecuredSettings {

    /**
     * Called when a user isn't authenticated.
     *
     * @param request The request header.
     * @param lang The current selected lang.
     * @return The result to send to the client.
     */
    override def onNotAuthenticated(request: RequestHeader, lang: Lang) = {
      Some(Future.successful(Unauthorized("global.not.authenticated")))
    }

    /**
     * Called when a user isn't authorized.
     *
     * @param request The request header.
     * @param lang The current selected lang.
     * @return The result to send to the client.
     */
    override def onNotAuthorized(request: RequestHeader, lang: Lang) = {
      Some(Future.successful(Forbidden("global.not.authorized")))
    }

  }))) with Context

  /**
   * Runs a fake application with a default global object.
   */
  class WithDefaultGlobal extends WithApplication(FakeApplication()) with Context

  /**
   * A secured controller.
   *
   * @param env The silhouette environment.
   * @param authorization An authorization implementation.
   */
  class SecuredController(
    val env: Environment[FakeIdentity, FakeAuthenticator],
    val authorization: Authorization[FakeIdentity] = SimpleAuthorization())
    extends Silhouette[FakeIdentity, FakeAuthenticator] {

    /**
     * A secured action.
     *
     * @return The result to send to the client.
     */
    def securedAction = SecuredAction { implicit request =>
      render {
        case Accepts.Json() => Ok(Json.obj("result" -> "full.access"))
        case Accepts.Html() => Ok("full.access")
      }
    }

    /**
     * A secured action with authorization.
     *
     * @return The result to send to the client.
     */
    def securedActionWithAuthorization = SecuredAction(authorization) { implicit request: SecuredRequest[_] =>
      Ok("full.access")
    }

    /**
     * A secured renew action.
     *
     * @return The result to send to the client.
     */
    def securedRenewAction = SecuredAction.async { implicit request =>
      request.authenticator.renew(Future.successful(Ok("renewed")))
    }

    /**
     * A secured discard action.
     *
     * @return The result to send to the client.
     */
    def securedDiscardAction = SecuredAction.async { implicit request =>
      request.authenticator.discard(Future.successful(Ok("discarded")))
    }

    /**
     * A secured request handler.
     */
    def securedRequestHandler = Action.async { implicit request =>
      SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }.map {
        case HandlerResult(r, Some(user)) => Ok(Json.toJson(user.loginInfo))
        case HandlerResult(r, None) => Unauthorized
      }
    }

    /**
     * A user aware action.
     *
     * @return The result to send to the client.
     */
    def userAwareAction = UserAwareAction { implicit request =>
      if (request.identity.isDefined && request.authenticator.isDefined) {
        Ok("with.identity.and.authenticator")
      } else if (request.authenticator.isDefined) {
        Ok("without.identity.and.with.authenticator")
      } else {
        Ok("without.identity.and.authenticator")
      }
    }

    /**
     * A renew action.
     *
     * @return The result to send to the client.
     */
    def userAwareRenewAction = UserAwareAction.async { implicit request =>
      request.authenticator match {
        case Some(a) => a.renew(Future.successful(Ok("renewed")))
        case None => Future.successful(Ok("not.renewed"))
      }
    }

    /**
     * A discard action.
     *
     * @return The result to send to the client.
     */
    def userAwareDiscardAction = UserAwareAction.async { implicit request =>
      request.authenticator match {
        case Some(a) => a.discard(Future.successful(Ok("discarded")))
        case None => Future.successful(Ok("not.discarded"))
      }
    }

    /**
     * A user aware request handler.
     */
    def userAwareRequestHandler = Action.async { implicit request =>
      UserAwareRequestHandler { userAwareRequest =>
        Future.successful(HandlerResult(Ok, userAwareRequest.identity))
      }.map {
        case HandlerResult(r, Some(user)) => Ok(Json.toJson(user.loginInfo))
        case HandlerResult(r, None) => Unauthorized
      }
    }

    /**
     * Method to test the `exceptionHandler` method of the Silhouette controller.
     *
     * @param f The future to recover from.
     * @param request The request header.
     * @return The result to send to the client.
     */
    def recover(f: Future[Result])(implicit request: RequestHeader): Future[Result] = {
      f.recoverWith(exceptionHandler)
    }
  }

  /**
   * A simple authorization class.
   *
   * @param isAuthorized True if the access is authorized, false otherwise.
   */
  case class SimpleAuthorization(isAuthorized: Boolean = true) extends Authorization[FakeIdentity] {

    /**
     * Checks whether the user is authorized to execute an action or not.
     *
     * @param identity The identity to check for.
     * @param request The current request header.
     * @param lang The current lang.
     * @return True if the user is authorized, false otherwise.
     */
    def isAuthorized(identity: FakeIdentity)(implicit request: RequestHeader, lang: Lang): Boolean = isAuthorized
  }
}
