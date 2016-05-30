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

import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.providers.{ OAuth2State, OAuth2StateProvider }
import play.api.mvc.Result

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A dummy state which can be used to avoid state validation. This can be useful if the state
 * should be validated on client side.
 */
case class DummyState() extends OAuth2State {
  override def isExpired = false
}

/**
 * Handles the dummy state.
 */
class DummyStateProvider extends OAuth2StateProvider {

  /**
   * The type of the state implementation.
   */
  override type State = DummyState

  /**
   * Builds the state.
   *
   * @param request The request.
   * @param ec The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The build state.
   */
  override def build[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[DummyState] =
    Future.successful(DummyState())

  /**
   * Returns always a valid state avoid authentication errors.
   *
   * @param request The request.
   * @param ec The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return Always a valid state avoid authentication errors.
   */
  override def validate[B](implicit request: ExtractableRequest[B], ec: ExecutionContext) =
    Future.successful(DummyState())

  /**
   * Returns the original result.
   *
   * @param result The result to send to the client.
   * @param state The state to publish.
   * @param request The request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def publish[B](result: Result, state: State)(implicit request: ExtractableRequest[B]) = result

  /**
   * Returns a serialized value of the state.
   *
   * @param state The state to serialize.
   * @return A serialized value of the state.
   */
  override def serialize(state: State) = ""
}
