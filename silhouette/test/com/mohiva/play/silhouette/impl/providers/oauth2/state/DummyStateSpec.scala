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
package com.mohiva.play.silhouette.impl.providers.oauth2.state

import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.mvc.Results
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.providers.oauth2.state.DummyState]] class.
 */
class DummyStateSpec extends PlaySpecification with Mockito with JsonMatchers {

  "The `isExpired` method of the state" should {
    "return false" in new Context {
      state.isExpired must beFalse
    }
  }

  "The `serialize` method of the state" should {
    "return an empty string" in new Context {
      state.serialize must be equalTo ""
    }
  }

  "The `build` method of the provider" should {
    "return a new state" in new Context {
      implicit val req = FakeRequest()

      await(provider.build) must be equalTo state
    }
  }

  "The `validate` method of the provider" should {
    "return the state if it's valid" in new WithApplication with Context {
      implicit val req = FakeRequest()

      await(provider.validate("test")) must be equalTo state
    }
  }

  "The `publish` method of the provider" should {
    "return the original result" in new Context {
      implicit val req = FakeRequest(GET, "/")
      val result = Results.Status(200)

      provider.publish(result, state) must be equalTo result
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The provider implementation to test.
     */
    lazy val provider = new DummyStateProvider()

    /**
     * A state to test.
     */
    lazy val state = DummyState()
  }
}
