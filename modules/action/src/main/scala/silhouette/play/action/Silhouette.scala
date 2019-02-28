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

import javax.inject.Inject
import silhouette.Identity

/**
 * The Silhouette stack.
 *
 * Inject an instance of this trait into your controller to provide all the Silhouette actions.
 *
 * @tparam I The type of the identity.
 * @tparam B The type of the request body.
 */
trait Silhouette[I <: Identity, B] {

  /**
   * The Silhouette environment.
   */
  val env: Environment[I]

  /**
   * The secured action stack.
   */
  protected val securedAction: SecuredAction[B]

  /**
   * The unsecured action stack.
   */
  protected val unsecuredAction: UnsecuredAction[B]

  /**
   * The user aware action stack.
   */
  protected val userAwareAction: UserAwareAction[B]

  // scalastyle:off method.name
  /**
   * Provides the secured action implementation.
   *
   * @return The secured action implementation.
   */
  def SecuredAction: SecuredActionBuilder[I, B] = securedAction(env)

  /**
   * Provides the secured request handler implementation.
   *
   * @return The secured request handler implementation.
   */
  def SecuredRequestHandler: SecuredRequestHandlerBuilder[I] = securedAction.requestHandler(env)

  /**
   * Provides the unsecured action implementation.
   *
   * @return The unsecured action implementation.
   */
  def UnsecuredAction: UnsecuredActionBuilder[I, B] = unsecuredAction(env)

  /**
   * Provides the unsecured request handler implementation.
   *
   * @return The unsecured request handler implementation.
   */
  def UnsecuredRequestHandler: UnsecuredRequestHandlerBuilder[I] = unsecuredAction.requestHandler(env)

  /**
   * Provides the user-aware action implementation.
   *
   * @return The user-aware action implementation.
   */
  def UserAwareAction: UserAwareActionBuilder[I, B] = userAwareAction(env)

  /**
   * Provides the user-aware request handler implementation.
   *
   * @return The user-aware request handler implementation.
   */
  def UserAwareRequestHandler: UserAwareRequestHandlerBuilder[I] = userAwareAction.requestHandler(env)
  // scalastyle:on method.name
}

/**
 * Provides the Silhouette stack.
 *
 * @param env             The Silhouette environment.
 * @param securedAction   The secured action stack.
 * @param userAwareAction The user aware action stack.
 * @tparam I The type of the identity.
 * @tparam B The type of the body.
 */
class SilhouetteProvider[I <: Identity, B] @Inject() (
  val env: Environment[I],
  val securedAction: SecuredAction[B],
  val unsecuredAction: UnsecuredAction[B],
  val userAwareAction: UserAwareAction[B]
) extends Silhouette[I, B]
