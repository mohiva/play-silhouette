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
import com.mohiva.play.silhouette.impl.providers.state.UserStateItemHandler._
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.test.{ FakeRequest, PlaySpecification }

import scala.concurrent.ExecutionContext.Implicits.global

/**
 *  Test case for the [[UserStateItemHandler]] class.
 */
class UserStateItemHandlerSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `item` method" should {
    "return the user state item" in new Context {
      await(userStateItemHandler.item) must be equalTo userStateItem
    }
  }

  "The `canHandle` method" should {
    "return the same item if it can handle the given item" in new Context {
      userStateItemHandler.canHandle(userStateItem) must beSome(userStateItem)
    }

    "should return `None` if it can't handle the given item" in new Context {
      val nonUserState = mock[SocialStateItem].smart

      userStateItemHandler.canHandle(nonUserState) must beNone
    }
  }

  "The `canHandle` method" should {
    "return false if the give item is for another handler" in new Context {
      val nonUserItemStructure = mock[ItemStructure].smart
      nonUserItemStructure.id returns "non-user-item"

      implicit val request = FakeRequest()
      userStateItemHandler.canHandle(nonUserItemStructure) must beFalse
    }

    "return true if it can handle the given `ItemStructure`" in new Context {
      implicit val request = FakeRequest()
      userStateItemHandler.canHandle(userItemStructure) must beTrue
    }
  }

  "The `serialize` method" should {
    "return a serialized value of the state item" in new Context {
      userStateItemHandler.serialize(userStateItem).asString must be equalTo userItemStructure.asString
    }
  }

  "The `unserialize` method" should {
    "unserialize the state item" in new Context {
      implicit val request = FakeRequest()

      await(userStateItemHandler.unserialize(userItemStructure)) must be equalTo userStateItem
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * A user state item.
     */
    val userStateItem = UserStateItem(Map("path" -> "/login"))

    /**
     * The serialized type of the user state item.
     */
    val userItemStructure = ItemStructure(ID, Json.toJson(userStateItem))

    /**
     * An instance of the user state item handler.
     */
    val userStateItemHandler = new UserStateItemHandler[UserStateItem](userStateItem)
  }
}
