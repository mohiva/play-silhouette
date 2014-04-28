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
package com.mohiva.play.silhouette.core.providers

import scala.reflect.ClassTag
import scala.concurrent.Future
import play.api.mvc.SimpleResult
import play.api.test.PlaySpecification
import org.specs2.matcher.{ MatchResult, JsonMatchers }
import org.specs2.mock.Mockito
import com.mohiva.play.silhouette.core.services.AuthInfo

/**
 * Test case for the [[com.mohiva.play.silhouette.core.providers.OAuth1Provider]] class.
 *
 * These tests will be additionally executed before every OAuth1 provider spec.
 */
abstract class ProviderSpec[A <: AuthInfo] extends PlaySpecification with Mockito with JsonMatchers {

  /**
   * The provider result.
   */
  type ProviderResult = Future[Either[SimpleResult, SocialProfile[A]]]

  /**
   * Applies a matcher on a simple result.
   *
   * @param providerResult The result from the provider.
   * @param b The matcher block to apply.
   * @return A specs2 match result.
   */
  def result(providerResult: ProviderResult)(b: Future[SimpleResult] => MatchResult[_]) = {
    await(providerResult) must beLeft[SimpleResult].like {
      case simpleResult => b(Future.successful(simpleResult))
    }
  }

  /**
   * Applies a matcher on a social profile.
   *
   * @param providerResult The result from the provider.
   * @param b The matcher block to apply.
   * @return A specs2 match result.
   */
  def profile(providerResult: ProviderResult)(b: SocialProfile[A] => MatchResult[_]) = {
    await(providerResult) must beRight[SocialProfile[A]].like {
      case socialProfile => b(socialProfile)
    }
  }

  /**
   * Matches a partial function against a failure message.
   *
   * This method checks if an exception was thrown in a future.
   * @see https://groups.google.com/d/msg/specs2-users/MhJxnvyS1_Q/FgAK-5IIIhUJ
   *
   * @param providerResult The result from the provider.
   * @param f A matcher function.
   * @return A specs2 match result.
   */
  def failed[E <: Throwable: ClassTag](providerResult: ProviderResult)(f: => PartialFunction[Throwable, MatchResult[_]]) = {
    implicit class Rethrow(t: Throwable) {
      def rethrow = { throw t; t }
    }

    lazy val result = await(providerResult.failed)

    result must not(throwAn[E])
    result.rethrow must throwAn[E].like(f)
  }
}
