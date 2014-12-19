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
package com.mohiva.silhouette

import scala.concurrent.Future

/**
 * A marker interface for all providers.
 */
trait Provider {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id: String
}

/**
 * A provider which can be hooked into a request.
 *
 * It scans the request for credentials and returns the login info for it.
 */
trait RequestProvider extends Provider {

  /**
   * Authenticates an identity based on credentials sent in a request.
   *
   * Silhouette supports chaining of request providers. So if more as one request provider is defined
   * it tries to authenticate until one provider returns an identity. To control the behaviour of the
   * chaining you can use the return type by this method.
   *
   * None - If returning None, then the next provider in the chain will be executed.
   * Some(identity) - If returning some identity, then this provider will be used for authentication.
   * Exception - Throwing an exception breaks the chain. The error handler will also handle this exception.
   *
   * @param request The request header.
   * @return Some login info on successful authentication or None if the authentication was unsuccessful.
   */
  def authenticate(request: RequestHeader): Future[Option[LoginInfo]]
}
