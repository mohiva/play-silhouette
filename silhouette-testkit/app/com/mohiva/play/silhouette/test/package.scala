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
package com.mohiva.play.silhouette

import com.mohiva.play.silhouette.api._
import play.api.mvc.Request
import play.api.test.FakeRequest

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.{ implicitConversions, postfixOps }

/**
 * Test helpers to test a Silhouette application.
 */
package object test {

  /**
   * Resolves a future by waiting of the result.
   *
   * @param f The future to block.
   * @tparam T The type contained in the future.
   * @return The value contained in the future.
   */
  implicit protected[test] def await[T](f: Future[T]): T = Await.result(f, 60 seconds)

  /**
   * Provides a method which add an authenticator to a fake request.
   *
   * @param f A fake request instance.
   * @tparam A The type of the body.
   */
  implicit class FakeRequestWithAuthenticator[A](f: FakeRequest[A]) {
    implicit val request = f

    /**
     * Creates a fake request with an embedded authenticator.
     *
     * @param authenticator The authenticator to embed into the request.
     * @param env The Silhouette environment.
     * @tparam E The type of the environment.
     * @return A fake request.
     */
    def withAuthenticator[E <: Env](authenticator: E#A)(implicit env: Environment[E]): FakeRequest[A] = {
      implicit val ec = env.executionContext
      val rh = env.authenticatorService.init(authenticator).map(v => env.authenticatorService.embed(v, f))

      new FakeRequest(Request.apply(rh, f.body))
    }

    /**
     * Creates a fake request with an embedded authenticator.
     *
     * @param loginInfo The login info for which the new authenticator should be created.
     * @param env The Silhouette environment.
     * @tparam E The type of the environment.
     * @return A fake request.
     */
    def withAuthenticator[E <: Env](loginInfo: LoginInfo)(implicit env: Environment[E]): FakeRequest[A] = {
      withAuthenticator(FakeAuthenticator[E](loginInfo))
    }
  }
}
