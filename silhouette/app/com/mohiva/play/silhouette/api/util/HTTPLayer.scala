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
package com.mohiva.play.silhouette.api.util

import play.api.libs.ws._

import scala.concurrent.ExecutionContext

/**
 * A trait which provides a mockable implementation for the HTTP layer.
 */
trait HTTPLayer extends ExecutionContextProvider {

  /**
   * The type of the request.
   */
  type Request <: WSRequest

  /**
   * Prepare a new request. You can then construct it by chaining calls.
   *
   * @param url The URL to request.
   */
  def url(url: String): Request
}

/**
 * Implementation of the HTTP layer which uses the Play web service implementation.
 *
 * It makes no sense to move the HTTPLayer implementation to the contrib package, because the complete
 * Silhouette module is bound to Play's HTTP implementation. So this layer exists only for mocking purpose.
 *
 * @param client Play's WS client implementation.
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class PlayHTTPLayer(client: WSClient)(implicit val executionContext: ExecutionContext) extends HTTPLayer {

  /**
   * The type of the request.
   */
  type Request = WSRequest

  /**
   * Prepare a new request. You can then construct it by chaining calls.
   *
   * @param url The URL to request.
   */
  def url(url: String): Request = client.url(url)
}

/**
 * A mockable WS request.
 *
 * @see https://github.com/playframework/play-ws/issues/108
 */
trait MockWSRequest extends WSRequest {
  override type Self = WSRequest
  override type Response = WSResponse
}

/**
 * A mockable HTTP layer.
 */
trait MockHTTPLayer extends HTTPLayer {

  /**
   * The type of the request.
   */
  type Request = MockWSRequest

  /**
   * Prepare a new request. You can then construct it by chaining calls.
   *
   * @param url The URL to request.
   */
  def url(url: String): Request
}
