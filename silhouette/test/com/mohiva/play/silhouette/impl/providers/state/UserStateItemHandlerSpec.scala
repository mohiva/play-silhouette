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
package com.mohiva.play.silhouette.impl.providers.state

import com.mohiva.play.silhouette.impl.providers.SocialStateItem
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.{ Format, Json }
import play.api.test.{ FakeRequest, PlaySpecification }
import play.api.libs.concurrent.Execution.Implicits._

/**
 *  Test case for the [[UserStateItemHandler]] class.
 */
class UserStateItemHandlerSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `item` method" should {
    "return userState" in new Context {
      await(userStateHandler.item) must be(userState)
    }
  }

  "The `canHandle` method" should {
    "return `Some[SocialStateItem]` if it can handle the given `SocialStateItem`" in new Context {
      userStateHandler.canHandle(userState) must beSome[SocialStateItem]
    }

    "return `None` if it can't handle the given `SocialStateItem`" in new Context {
      userStateHandler.canHandle(csrfState) must beNone
    }
  }

  "The `canHandle` method" should {
    "return true if it can handle the given `ItemStructure`" in new Context {
      implicit val request = FakeRequest()
      userStateHandler.canHandle(itemStructure) must beTrue
    }

    "return false if it can't handle the given `ItemStructure`" in new Context {
      implicit val request = FakeRequest()
      userStateHandler.canHandle(itemStructure.copy(id = "non-user-state")) must beFalse
    }
  }

  "The `serialize` method" should {
    "serialize `UserState` to `ItemStructure`" in new Context {
      userStateHandler.serialize(userState) must beAnInstanceOf[ItemStructure]
    }
  }

  "The `unserialize` method" should {
    "unserialize `ItemStructure` to `UserState`" in new Context {
      implicit val request = FakeRequest()
      await(userStateHandler.unserialize(itemStructure)) must beAnInstanceOf[UserState]
    }
  }

  trait Context extends Scope {

    /**
     * An example usage of UserState where state is of type Map[String, String]
     * @param state
     */
    case class UserState(state: Map[String, String]) extends SocialStateItem

    /**
     * Format to serialize the UserState
     */
    implicit val userStateFormat: Format[UserState] = Json.format[UserState]

    /**
     * An instance of UserState
     */
    val userState = UserState(Map("path" -> "/login"))

    /**
     * Serialized type of UserState
     */
    val itemStructure = ItemStructure("user-state", Json.toJson(userState))

    /**
     * Csrf State value
     */
    val csrfToken = "csrfToken"

    /**
     * An instance of CsrfState
     */
    val csrfState = CsrfState(csrfToken)

    /**
     * An instance of User State Handler
     */
    val userStateHandler = new UserStateItemHandler(userState)
  }
}
