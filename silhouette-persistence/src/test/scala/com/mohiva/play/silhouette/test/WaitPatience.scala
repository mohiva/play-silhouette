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
package com.mohiva.play.silhouette.test

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers.FutureMatchable
import org.specs2.matcher.Matcher

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Helper to wait with patience to a result.
 *
 * This is needed to prevent the tests for timeouts.
 */
trait WaitPatience {

  def retries = 10

  def timeout = 1.second

  implicit class WaitWithPatienceFutureMatchable[T](m: Matcher[T])(implicit ee: ExecutionEnv) extends FutureMatchable[T](m)(ee) {
    def awaitWithPatience: Matcher[Future[T]] = {
      await(retries, timeout)
    }
  }
}
