/**
 * Licensed to the Minutemen Group under one or more contributor license
 * agreements. See the COPYRIGHT file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silhouette.play.action

import play.api.mvc.Request
import silhouette.provider.RequestProvider
import silhouette.{ Credentials, ExecutionContextProvider, Identity, LoginInfo }

import scala.concurrent.Future

/**
 * Provides the components needed to handle a secured request.
 *
 * @tparam I The type of the identity.
 */
trait Environment[I <: Identity] extends ExecutionContextProvider {

  /**
   * A functions that retrieves the identity for the given [[LoginInfo]].
   *
   * @return A functions that retrieves the identity for the given [[LoginInfo]].
   */
  def identityReader: LoginInfo => Future[Option[I]]

  /**
   * Gets the list of request providers.
   *
   * @return The list of request providers.
   */
  def requestProviders[B]: List[RequestProvider[Request[B], I, Credentials]]

  /**
   * The event bus implementation.
   *
   * @return The event bus implementation.
   */
  def eventBus: EventBus
}
