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
package com.mohiva.play.silhouette.core

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import scala.concurrent.Future
import play.api.test.{ WithApplication, FakeRequest, PlaySpecification }
import play.api.GlobalSettings
import play.api.i18n.{ Messages, Lang }
import play.api.mvc.{ RequestHeader, SimpleResult }
import play.api.mvc.Results._
import play.api.test.FakeApplication
import play.api.libs.json.Json
import com.mohiva.play.silhouette.core.services.{ AuthenticatorService, IdentityService }

/**
 * Test case for the [[com.mohiva.play.silhouette.core.Silhouette]] base controller.
 */
class SilhouetteSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The SecuredAction action" should {
    "restrict access if no authenticator can be retrieved" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(None)
      authenticatorService.discard(any) answers { r => r.asInstanceOf[SimpleResult] }

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "restrict access if no identity could be found for an authenticator" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      authenticatorService.discard(any) answers { r => r.asInstanceOf[SimpleResult] }
      identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "update an authenticator if an identity could be found for it" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      authenticatorService.discard(any) answers { r => r.asInstanceOf[SimpleResult] }
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      await(controller.protectedAction(request))

      there was one(authenticatorService).update(any)
    }

    "display local not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      authenticatorService.discard(any) answers { r => r.asInstanceOf[SimpleResult] }
      identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService) {
        override def notAuthenticated(request: RequestHeader): Option[Future[SimpleResult]] = {
          Some(Future.successful(Unauthorized("local.not.authenticated")))
        }
      }

      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("local.not.authenticated")
    }

    "display global not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      authenticatorService.discard(any) answers { r => r.asInstanceOf[SimpleResult] }
      identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("global.not.authenticated")
    }

    "display fallback message if user isn't authenticated and fallback methods aren't implemented" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      authenticatorService.discard(any) answers { r => r.asInstanceOf[SimpleResult] }
      identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "display local not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService, SimpleAuthorization(isAuthorized = false)) {
        override def notAuthorized(request: RequestHeader): Option[Future[SimpleResult]] = {
          Some(Future.successful(Forbidden("local.not.authorized")))
        }
      }

      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("local.not.authorized")
    }

    "display global not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService, SimpleAuthorization(isAuthorized = false))
      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("global.not.authorized")
    }

    "display fallback message if user isn't authorized and fallback methods aren't implemented" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService, SimpleAuthorization(isAuthorized = false))
      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authorized"))
    }

    "invoke action without authorization if user is authenticated" in new WithSecuredGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain("full.access")
    }

    "invoke action with authorization if user is authenticated but not authorized" in new WithSecuredGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedActionWithAuthorization(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain("full.access")
    }

    "discard authentication cookie if user isn't authenticated" in new WithSecuredGlobal {
      authenticatorService.retrieve returns Future.successful(None)
      authenticatorService.discard(any) answers { r => r.asInstanceOf[SimpleResult] }

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(request)

      status(result) must equalTo(UNAUTHORIZED)

      there was one(authenticatorService).discard(any)
    }

    "handle an Ajax request" in new WithSecuredGlobal {
      implicit val req = FakeRequest().withHeaders("Accept" -> "application/json")

      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(req)

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsString(result) must /("result" -> "full.access")
    }
  }

  "The UserAwareAction action" should {
    "restrict access if no authenticator could be found" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "restrict access if no identity could be found for an authenticator" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "update an authenticator if an identity could be found for it" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      await(controller.userAwareAction(request))

      there was one(authenticatorService).update(any)
    }

    "grant access if an identity could be found" in new WithDefaultGlobal {
      authenticatorService.retrieve returns Future.successful(Some(authenticator))
      identityService.retrieve(identity.loginInfo) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("full.access"))
    }
  }

  /**
   * A test identity.
   *
   * @param loginInfo The linked login info.
   */
  case class TestIdentity(loginInfo: LoginInfo) extends Identity

  /**
   * A test authenticator.
   *
   * @param loginInfo The linked login info.
   */
  case class TestAuthenticator(loginInfo: LoginInfo) extends Authenticator

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The identity service implementation.
     */
    lazy val identityService: IdentityService[TestIdentity] = mock[IdentityService[TestIdentity]]

    /**
     * The authenticator service implementation.
     */
    lazy val authenticatorService: AuthenticatorService[TestAuthenticator] = mock[AuthenticatorService[TestAuthenticator]]

    /**
     * An identity.
     */
    lazy val identity = new TestIdentity(LoginInfo("test", "1"))

    /**
     * An authenticator.
     */
    lazy val authenticator = new TestAuthenticator(LoginInfo("test", "1"))

    /**
     * A fake request.
     */
    lazy implicit val request = FakeRequest()
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
   * @param identityService The identity service implementation.
   * @param authenticatorService The authenticator service implementation.
   */
  class SecuredController(
    val identityService: IdentityService[TestIdentity],
    val authenticatorService: AuthenticatorService[TestAuthenticator],
    val authorization: Authorization[TestIdentity] = SimpleAuthorization())
      extends Silhouette[TestIdentity, TestAuthenticator] {

    /**
     * A protected action.
     *
     * @return The result to send to the client.
     */
    def protectedAction = SecuredAction { implicit request =>
      render {
        case Accepts.Json() => Ok(Json.obj("result" -> "full.access"))
        case Accepts.Html() => Ok("full.access")
      }
    }

    /**
     * A protected action with authorization.
     *
     * @return The result to send to the client.
     */
    def protectedActionWithAuthorization = SecuredAction(authorization) { implicit request: SecuredRequest[_] =>
      Ok("full.access")
    }

    /**
     * A user aware action.
     *
     * @return The result to send to the client.
     */
    def userAwareAction = UserAwareAction { implicit request =>
      if (request.identity.isDefined) {
        Ok("full.access")
      } else {
        Unauthorized("not.authenticated")
      }
    }
  }

  /**
   * A simple authorization class.
   *
   * @param isAuthorized True if the access is authorized, false otherwise.
   */
  case class SimpleAuthorization(isAuthorized: Boolean = true) extends Authorization[TestIdentity] {

    /**
     * Checks whether the user is authorized to execute an action or not.
     *
     * @param identity The identity to check for.
     * @return True if the user is authorized, false otherwise.
     */
    def isAuthorized(identity: TestIdentity): Boolean = isAuthorized
  }
}
