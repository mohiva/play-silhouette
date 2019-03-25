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

import com.mohiva.play.silhouette.api.crypto.Signer
import com.mohiva.play.silhouette.api.util.IDGenerator
import com.mohiva.play.silhouette.impl.providers.SocialStateItem
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import com.mohiva.play.silhouette.impl.providers.state.CsrfStateItemHandler._
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.mvc.{ Cookie, Results }
import play.api.test.{ FakeRequest, PlaySpecification }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

/**
 *  Test case for the [[CsrfStateItemHandler]] class.
 */
class CsrfStateItemHandlerSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `item` method" should {
    "return the CSRF state item" in new Context {
      idGenerator.generate returns Future.successful(csrfToken)

      await(csrfStateItemHandler.item) must be equalTo csrfStateItem
    }
  }

  "The `canHandle` method" should {
    "return the same item if it can handle the given item" in new Context {
      csrfStateItemHandler.canHandle(csrfStateItem) must beSome(csrfStateItem)
    }

    "should return `None` if it can't handle the given item" in new Context {
      val nonCsrfState = mock[SocialStateItem].smart

      csrfStateItemHandler.canHandle(nonCsrfState) must beNone
    }
  }

  "The `canHandle` method" should {
    "return false if the give item is for another handler" in new Context {
      val nonCsrfItemStructure = mock[ItemStructure].smart
      nonCsrfItemStructure.id returns "non-csrf-item"

      implicit val request = FakeRequest()
      csrfStateItemHandler.canHandle(nonCsrfItemStructure) must beFalse
    }

    "return false if client state doesn't match the item state" in new Context {
      implicit val request = FakeRequest().withCookies(cookie("invalid-token"))
      csrfStateItemHandler.canHandle(csrfItemStructure) must beFalse
    }

    "return true if it can handle the given `ItemStructure`" in new Context {
      implicit val request = FakeRequest().withCookies(cookie(csrfStateItem.token))
      csrfStateItemHandler.canHandle(csrfItemStructure) must beTrue
    }
  }

  "The `serialize` method" should {
    "return a serialized value of the state item" in new Context {
      csrfStateItemHandler.serialize(csrfStateItem).asString must be equalTo csrfItemStructure.asString
    }
  }

  "The `unserialize` method" should {
    "unserialize the state item" in new Context {
      implicit val request = FakeRequest()

      await(csrfStateItemHandler.unserialize(csrfItemStructure)) must be equalTo csrfStateItem
    }
  }

  "The `publish` method" should {
    "publish the state item to the client" in new Context {
      implicit val request = FakeRequest()
      val result = csrfStateItemHandler.publish(csrfStateItem, Results.Ok)

      cookies(Future.successful(result)).get(settings.cookieName) must beSome(cookie(csrfToken))
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The ID generator implementation.
     */
    val idGenerator = mock[IDGenerator].smart

    /**
     * The settings.
     */
    val settings = CsrfStateSettings()

    /**
     * The signer implementation.
     *
     * The signer returns the same value as passed to the methods. This is enough for testing.
     */
    val signer = {
      val c = mock[Signer].smart
      c.sign(any()) answers { p: Any => p.asInstanceOf[String] }
      c.extract(any()) answers { p: Any => Success(p.asInstanceOf[String]) }
      c
    }

    /**
     * A CSRF token.
     */
    val csrfToken = "csrfToken"

    /**
     * A CSRF state item.
     */
    val csrfStateItem = CsrfStateItem(csrfToken)

    /**
     * The serialized type of the CSRF state item.
     */
    val csrfItemStructure = ItemStructure(ID, Json.toJson(csrfStateItem))

    /**
     * An instance of the CSRF state item handler.
     */
    val csrfStateItemHandler = new CsrfStateItemHandler(settings, idGenerator, signer)

    /**
     * A helper method to create a cookie.
     *
     * @param value The cookie value.
     * @return A cookie instance with the given value.
     */
    def cookie(value: String): Cookie = Cookie(
      name = settings.cookieName,
      value = signer.sign(value),
      maxAge = Some(settings.expirationTime.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie,
      sameSite = settings.sameSite
    )
  }
}
