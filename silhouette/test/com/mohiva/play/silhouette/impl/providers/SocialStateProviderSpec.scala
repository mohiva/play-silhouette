/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.AuthInfo
import org.specs2.matcher.MatchResult
import play.api.mvc.Result

import scala.concurrent.Future

/**
 * Abstract test case for the social state providers.
 */
abstract class SocialStateProviderSpec[A <: AuthInfo, S <: SocialStateItem] extends SocialProviderSpec[A] {

  /**
   * Applies a matcher on a simple result.
   *
   * @param providerResult The result from the provider.
   * @param b              The matcher block to apply.
   * @return A specs2 match result.
   */
  def statefulResult(providerResult: Future[Either[Result, StatefulAuthInfo[A, S]]])(b: Future[Result] => MatchResult[_]) = {
    await(providerResult) must beLeft[Result].like {
      case result => b(Future.successful(result))
    }
  }

  /**
   * Applies a matcher on a stateful auth info.
   *
   * @param providerResult The result from the provider.
   * @param b              The matcher block to apply.
   * @return A specs2 match result.
   */
  def statefulAuthInfo(providerResult: Future[Either[Result, StatefulAuthInfo[A, S]]])(b: StatefulAuthInfo[A, S] => MatchResult[_]) = {
    await(providerResult) must beRight[StatefulAuthInfo[A, S]].like {
      case info => b(info)
    }
  }
}
