package com.mohiva.play.silhouette.test

import com.mohiva.play.silhouette.api.{ Silhouette, Environment, LoginInfo }
import com.mohiva.play.silhouette.impl.authenticators._
import org.specs2.matcher.JsonMatchers
import play.api.libs.json.Json
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

/**
 * Test case for the [[com.mohiva.play.silhouette.test]] helpers.
 */
class FakesSpec extends PlaySpecification with JsonMatchers {

  "The `retrieve` method of the `FakeIdentityService`" should {
    "return the identity for the given login info" in {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      val service = new FakeIdentityService[FakeIdentity](identity)

      await(service.retrieve(loginInfo)) must beSome(identity)
    }

    "return None if no identity could be found for the given login info" in {
      val loginInfo = LoginInfo("test", "test")
      val service = new FakeIdentityService[FakeIdentity]()

      await(service.retrieve(loginInfo)) must beNone
    }
  }

  "The `save` method of the `FakeAuthenticatorDAO`" should {
    "save an authenticator" in {
      val loginInfo = LoginInfo("test", "test")
      val authenticator = new FakeAuthenticator(loginInfo)
      val dao = new FakeAuthenticatorDAO[FakeAuthenticator]()

      await(dao.save(authenticator)) must be equalTo authenticator
    }
  }

  "The `find` method of the `FakeAuthenticatorDAO`" should {
    "return an authenticator for the given ID" in {
      val loginInfo = LoginInfo("test", "test")
      val authenticator = new FakeAuthenticator(loginInfo, "test")
      val dao = new FakeAuthenticatorDAO[FakeAuthenticator]()

      await(dao.save(authenticator))

      await(dao.find("test")) must beSome(authenticator)
    }

    "return None if no authenticator could be found for the given ID" in {
      val dao = new FakeAuthenticatorDAO[FakeAuthenticator]()

      await(dao.find("test")) must beNone
    }
  }

  "The `remove` method of the `FakeAuthenticatorDAO`" should {
    "remove an authenticator" in {
      val loginInfo = LoginInfo("test", "test")
      val authenticator = new FakeAuthenticator(loginInfo, "test")
      val dao = new FakeAuthenticatorDAO[FakeAuthenticator]()

      await(dao.save(authenticator))
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
  }

  "The `FakeAuthenticator` factory" should {
    "return a `SessionAuthenticator`" in {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, SessionAuthenticator](identity)
      implicit val request = FakeRequest()

      FakeAuthenticator[SessionAuthenticator](loginInfo) must beAnInstanceOf[SessionAuthenticator]
    }

    "return a `CookieAuthenticator`" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
      implicit val request = FakeRequest()

      FakeAuthenticator[CookieAuthenticator](loginInfo) must beAnInstanceOf[CookieAuthenticator]
    }

    "return a `BearerTokenAuthenticator`" in {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, BearerTokenAuthenticator](identity)
      implicit val request = FakeRequest()

      FakeAuthenticator[BearerTokenAuthenticator](loginInfo) must beAnInstanceOf[BearerTokenAuthenticator]
    }

    "return a `JWTAuthenticator`" in {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, JWTAuthenticator](identity)
      implicit val request = FakeRequest()

      FakeAuthenticator[JWTAuthenticator](loginInfo) must beAnInstanceOf[JWTAuthenticator]
    }
  }

  "The `securedAction` method of the `SecuredController`" should {
    "return a 401 status code if no authenticator was found" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
      val request = FakeRequest()

      val controller = new SecuredController(env)
      val result = controller.securedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
    }

    "return a 401 status code if authenticator but no identity was found" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
      val request = FakeRequest().withAuthenticator[CookieAuthenticator](LoginInfo("invalid", "invalid"))

      val controller = new SecuredController(env)
      val result = controller.securedAction(request)

      status(result) must equalTo(UNAUTHORIZED)
    }

    "return a 200 status code if authenticator and identity was found" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
      val request = FakeRequest().withAuthenticator[CookieAuthenticator](loginInfo)

      val controller = new SecuredController(env)
      val result = controller.securedAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must */("providerID" -> "test") and */("providerKey" -> "test")
    }
  }

  "The `userAwareAction` method of the `SecuredController`" should {
    "return a 401 status code if no authenticator was found" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
      val request = FakeRequest()

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(UNAUTHORIZED)
    }

    "return a 401 status code if authenticator but no identity was found" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
      val request = FakeRequest().withAuthenticator[CookieAuthenticator](LoginInfo("invalid", "invalid"))

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(UNAUTHORIZED)
    }

    "return a 200 status code if authenticator and identity was found" in new WithApplication {
      val loginInfo = LoginInfo("test", "test")
      val identity = FakeIdentity(loginInfo)
      implicit val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
      val request = FakeRequest().withAuthenticator(loginInfo)

      val controller = new SecuredController(env)
      val result = controller.userAwareAction(request)

      status(result) must equalTo(OK)
      contentAsString(result) must */("providerID" -> "test") and */("providerKey" -> "test")
    }
  }

  /**
   * A secured controller implementation.
   *
   * @param env The Silhouette environment.
   */
  class SecuredController(
    val env: Environment[FakeIdentity, CookieAuthenticator])
    extends Silhouette[FakeIdentity, CookieAuthenticator] {

    /**
     * A secured action.
     *
     * @return The result to send to the client.
     */
    def securedAction = SecuredAction { implicit request =>
      Ok(Json.toJson(request.identity.loginInfo))
    }

    /**
     * A user aware action.
     *
     * @return The result to send to the client.
     */
    def userAwareAction = UserAwareAction { implicit request =>
      request.identity match {
        case Some(identity) => Ok(Json.toJson(identity.loginInfo))
        case None => Unauthorized
      }
    }
  }
}
