package com.mohiva.play.silhouette.impl.providers.state

import com.mohiva.play.silhouette.impl.providers.SocialStateItem
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{ Format, Json }
import play.api.test.{ FakeRequest, PlaySpecification }
import play.api.libs.concurrent.Execution.Implicits._

class UserStateItemHandlerSpec extends PlaySpecification with Mockito with JsonMatchers {

  "item method of UserStateItemHandler" should {
    "return userState" in new Context {
      await(userStateHandler.item) must be(userState)
    }
  }

  "canHandle method of UserStateItemHandler" should {
    "should return Some[SocialStateItem] if it can handle the given SocialStateItem." in new Context {
      userStateHandler.canHandle(userState) must beSome[SocialStateItem]
    }
  }

  "canHandle method of UserStateItemHandler" should {
    "should return None if it can't handle the given SocialStateItem." in new Context {
      userStateHandler.canHandle(csrfState) must beNone
    }
  }

  "canHandle method of UserStateItemHandler" should {
    "should return true if it can handle the given ItemStructure." in new Context {
      implicit val request = FakeRequest()
      userStateHandler.canHandle(itemStructure) must beTrue
    }
  }

  "canHandle method of UserStateItemHandler" should {
    "should return false if it can't handle the given ItemStructure." in new Context {
      implicit val request = FakeRequest()
      userStateHandler.canHandle(itemStructure.copy(id = "non-user-state")) must beFalse
    }
  }

  "serialize method of UserStateItemHandler" should {
    "serialize userState to ItemStructure" in new Context {
      userStateHandler.serialize(userState) must beAnInstanceOf[ItemStructure]
    }
  }

  "unserialize method of UserStateItemHandler" should {
    "unserialize ItemStructure to UserState" in new Context {
      implicit val request = FakeRequest()
      await(userStateHandler.unserialize(itemStructure)) must beAnInstanceOf[UserState]
    }
  }

  trait Context extends Scope {

    case class UserState(state: Map[String, String]) extends SocialStateItem

    implicit val userStateFormat: Format[UserState] = Json.format[UserState]
    val userState = UserState(Map("path" -> "/login"))

    val itemStructure = ItemStructure("user-state", Json.toJson(userState))

    val csrfToken = "csrfToken"
    val csrfState = CsrfState(csrfToken)

    val userStateHandler = new UserStateItemHandler(userState)
  }
}
