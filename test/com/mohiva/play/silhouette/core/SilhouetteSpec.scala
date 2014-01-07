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

import org.joda.time.DateTime
import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import com.mohiva.play.silhouette.core.services.{AuthenticatorService, IdentityService}
import scala.concurrent.Future
import play.api.test.{WithApplication, FakeRequest, PlaySpecification}
import play.api.GlobalSettings
import play.api.i18n.{Messages, Lang}
import play.api.mvc.{Cookie, RequestHeader, SimpleResult}
import play.api.mvc.Results._
import play.api.test.FakeApplication
import play.api.libs.json.Json

/**
 * Test case for the [[com.mohiva.play.silhouette.core.Silhouette]] base controller.
 */
class SilhouetteSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The SecuredAction action" should {
    "restrict access if no authenticator cookie exists" in new WithDefaultGlobal {
      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest())

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "restrict access if no authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "restrict access if an expired authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator.copy(
        expirationDate = DateTime.now.minusMinutes(1)
      )))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "restrict access if a timed out authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator.copy(
        lastUsedDate = DateTime.now.minusMinutes(Authenticator.idleTimeout + 1)
      )))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "delete authenticator if an invalid authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator.copy(
        expirationDate = DateTime.now.minusMinutes(1)
      )))

      val controller = new SecuredController(identityService, authenticatorService)
      await(controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID))))

      one(authenticatorService).deleteByID(authenticatorID)
    }

    "restrict access if no identity could be found for an authenticator" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "touch an authenticator if an identity could be found for it" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      await(controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID))))

      one(authenticatorService).update(any)
    }

    "display local not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService) {
        override def notAuthenticated(request: RequestHeader): Option[Future[SimpleResult]] = {
          Some(Future.successful(Forbidden("local.not.authenticated")))
        }
      }

      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("local.not.authenticated")
    }

    "display global not-authenticated result if user isn't authenticated" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain("global.not.authenticated")
    }

    "display fallback message if user isn't authenticated and fallback methods aren't implemented" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("silhouette.not.authenticated"))
    }

    "display local not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService, SimpleAuthorization(isAuthorized = false)) {
        override def notAuthorized(request: RequestHeader): Option[Future[SimpleResult]] = {
          Some(Future.successful(Unauthorized("local.not.authorized")))
        }
      }

      val result = controller.protectedActionWithAuthorization(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("local.not.authorized")
    }

    "display global not-authorized result if user isn't authorized" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService, SimpleAuthorization(isAuthorized = false))
      val result = controller.protectedActionWithAuthorization(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain("global.not.authorized")
    }

    "display fallback message if user isn't authorized and fallback methods aren't implemented" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService, SimpleAuthorization(isAuthorized = false))
      val result = controller.protectedActionWithAuthorization(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(UNAUTHORIZED)
      contentAsString(result) must contain(Messages("silhouette.not.authorized"))
    }

    "invoke action without authorization if user is authorized" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(OK)
      contentAsString(result) must contain("full.access")
    }

    "invoke action with authorization if user is authorized" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedActionWithAuthorization(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(OK)
      contentAsString(result) must contain("full.access")
    }

    "discard authentication cookie" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(OK)
      cookies(result).get(authenticatorID) should beNone
    }

    "handle an Ajax request" in new WithSecuredGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.protectedAction(FakeRequest()
        .withCookies(Cookie(Authenticator.cookieName, authenticatorID))
        .withHeaders("Accept" -> "application/json")
      )

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsString(result) must /("result" -> "full.access")
    }
  }

  "The UserAwareAction action" should {
    "restrict access if no authenticator cookie exists" in new WithDefaultGlobal {
      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(FakeRequest())

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "restrict access if no authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "restrict access if an expired authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator.copy(
        expirationDate = DateTime.now.minusMinutes(1)
      )))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "restrict access if a timed out authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator.copy(
        lastUsedDate = DateTime.now.minusMinutes(Authenticator.idleTimeout + 1)
      )))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "delete authenticator if an invalid authenticator is stored for the cookie" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator.copy(
        expirationDate = DateTime.now.minusMinutes(1)
      )))

      val controller = new SecuredController(identityService, authenticatorService)
      await(controller.userAwareAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID))))

      one(authenticatorService).deleteByID(authenticatorID)
    }

    "restrict access if no identity could be found for an authenticator" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(None)

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(FORBIDDEN)
      contentAsString(result) must contain(Messages("not.authenticated"))
    }

    "touch an authenticator if an identity could be found for it" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      await(controller.userAwareAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID))))

      one(authenticatorService).update(any)
    }

    "grant access if an identity could be found" in new WithDefaultGlobal {
      authenticatorService.findByID(authenticatorID) returns Future.successful(Some(authenticator))
      identityService.findByID(identity.identityId) returns Future.successful(Some(identity))

      val controller = new SecuredController(identityService, authenticatorService)
      val result = controller.userAwareAction(FakeRequest().withCookies(Cookie(Authenticator.cookieName, authenticatorID)))

      status(result) must equalTo(OK)
      contentAsString(result) must contain(Messages("full.access"))
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The identity service implementation.
     */
    lazy val identityService: IdentityService[SocialUser] = mock[IdentityService[SocialUser]]

    /**
     * The authenticator service implementation.
     */
    lazy val authenticatorService: AuthenticatorService = mock[AuthenticatorService]

    /**
     * The authenticator ID.
     */
    lazy val authenticatorID = "test-authenticator-id"

    /**
     * An identity.
     */
    lazy val identity = new SocialUser(
      identityId = IdentityId("1", "test"),
      firstName = "Christian",
      lastName = "Kaps",
      fullName = "Christian Kaps",
      email = None,
      avatarUrl = None,
      authMethod = AuthenticationMethod.OAuth1
    )

    /**
     * An authenticator.
     */
    lazy val authenticator = new Authenticator(
      id = authenticatorID,
      identityID = IdentityId("1", "test"),
      creationDate = DateTime.now,
      lastUsedDate = DateTime.now,
      expirationDate = DateTime.now.plusMinutes(12 * 60)
    )
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
      Some(Future.successful(Forbidden("global.not.authenticated")))
    }

    /**
     * Called when a user isn't authorized.
     *
     * @param request The request header.
     * @param lang The current selected lang.
     * @return The result to send to the client.
     */
    override def onNotAuthorized(request: RequestHeader, lang: Lang) = {
      Some(Future.successful(Unauthorized("global.not.authorized")))
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
      val identityService: IdentityService[SocialUser],
      val authenticatorService: AuthenticatorService,
      val authorization: Authorization[SocialUser] =  SimpleAuthorization()
    )
    extends Silhouette[SocialUser] {

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
        Forbidden("not.authenticated")
      }
    }
  }

  /**
   * A simple authorization class.
   *
   * @param isAuthorized True if the access is authorized, false otherwise.
   */
  case class SimpleAuthorization(isAuthorized: Boolean = true) extends Authorization[SocialUser] {

    /**
     * Checks whether the user is authorized to execute an action or not.
     *
     * @param identity The identity to check for.
     * @return True if the user is authorized, false otherwise.
     */
    def isAuthorized(identity: SocialUser): Boolean = isAuthorized
  }
}
