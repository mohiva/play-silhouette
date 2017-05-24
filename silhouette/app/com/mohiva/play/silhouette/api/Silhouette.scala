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
package com.mohiva.play.silhouette.api

import javax.inject.Inject

import com.mohiva.play.silhouette.api.actions._
import play.api.mvc.AnyContent

/**
 * The Silhouette stack.
 *
 * Inject an instance of this trait into your controller to provide all the Silhouette actions.
 *
 * @tparam E The type of the environment.
 */
trait Silhouette[E <: Env] {

  /**
   * The Silhouette environment.
   */
  val env: Environment[E]

  /**
   * The secured action stack.
   */
  val securedAction: SecuredAction

  /**
   * The unsecured action stack.
   */
  val unsecuredAction: UnsecuredAction

  /**
   * The user aware action stack.
   */
  val userAwareAction: UserAwareAction

  /**
   * Provides the secured action implementation.
   *
   * @return The secured action implementation.
   */
  def SecuredAction: SecuredActionBuilder[E, AnyContent] = securedAction(env)

  /**
   * Provides the secured request handler implementation.
   *
   * @return The secured request handler implementation.
   */
  def SecuredRequestHandler: SecuredRequestHandlerBuilder[E] = securedAction.requestHandler(env)

  /**
   * Provides the unsecured action implementation.
   *
   * @return The unsecured action implementation.
   */
  def UnsecuredAction: UnsecuredActionBuilder[E, AnyContent] = unsecuredAction(env)

  /**
   * Provides the unsecured request handler implementation.
   *
   * @return The unsecured request handler implementation.
   */
  def UnsecuredRequestHandler: UnsecuredRequestHandlerBuilder[E] = unsecuredAction.requestHandler(env)

  /**
   * Provides the user-aware action implementation.
   *
   * @return The user-aware action implementation.
   */
  def UserAwareAction: UserAwareActionBuilder[E, AnyContent] = userAwareAction(env)

  /**
   * Provides the user-aware request handler implementation.
   *
   * @return The user-aware request handler implementation.
   */
  def UserAwareRequestHandler: UserAwareRequestHandlerBuilder[E] = userAwareAction.requestHandler(env)
}

/**
 * Provides the Silhouette stack.
 *
 * @param env               The Silhouette environment.
 * @param securedAction     The secured action stack.
 * @param userAwareAction   The user aware action stack.
 * @tparam E The type of the environment.
 */
class SilhouetteProvider[E <: Env] @Inject() (
  val env: Environment[E],
  val securedAction: SecuredAction,
  val unsecuredAction: UnsecuredAction,
  val userAwareAction: UserAwareAction
) extends Silhouette[E]
