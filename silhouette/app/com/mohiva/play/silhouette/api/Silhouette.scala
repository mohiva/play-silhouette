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
import play.api.mvc.{ AnyContent, BodyParser, BodyParsers }

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
   * The default Play body parser.
   */
  val defaultBodyParser: BodyParsers.Default

  /**
   * Provides the secured action implementation with the given body parser.
   *
   * @param parser The body parser.
   * @tparam B The type of the request body.
   * @return The secured action implementation.
   */
  def SecuredAction[B](parser: BodyParser[B]): SecuredActionBuilder[E, B] = securedAction(env, parser)

  /**
   * Provides the secured action implementation with the default body parser.
   *
   * @return The secured action implementation.
   */
  def SecuredAction: SecuredActionBuilder[E, AnyContent] = securedAction(env, defaultBodyParser)

  /**
   * Provides the secured request handler implementation.
   *
   * @return The secured request handler implementation.
   */
  def SecuredRequestHandler: SecuredRequestHandlerBuilder[E] = securedAction.requestHandler(env)

  /**
   * Provides the unsecured action implementation with the given body parser.
   *
   * @param parser The body parser.
   * @tparam B The type of the request body.
   * @return The unsecured action implementation.
   */
  def UnsecuredAction[B](parser: BodyParser[B]): UnsecuredActionBuilder[E, B] = unsecuredAction(env, parser)

  /**
   * Provides the unsecured action implementation with the default body parser.
   *
   * @return The unsecured action implementation.
   */
  def UnsecuredAction: UnsecuredActionBuilder[E, AnyContent] = unsecuredAction(env, defaultBodyParser)

  /**
   * Provides the unsecured request handler implementation.
   *
   * @return The unsecured request handler implementation.
   */
  def UnsecuredRequestHandler: UnsecuredRequestHandlerBuilder[E] = unsecuredAction.requestHandler(env)

  /**
   * Provides the user-aware action implementation with the given body parser.
   *
   * @param parser The body parser.
   * @tparam B The type of the request body.
   * @return The user-aware action implementation.
   */
  def UserAwareAction[B](parser: BodyParser[B]): UserAwareActionBuilder[E, B] = userAwareAction(env, parser)

  /**
   * Provides the user-aware action implementation with the default body parser.
   *
   * @return The user-aware action implementation.
   */
  def UserAwareAction: UserAwareActionBuilder[E, AnyContent] = userAwareAction(env, defaultBodyParser)

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
 * @param defaultBodyParser The default Play body parser.
 * @tparam E The type of the environment.
 */
class SilhouetteProvider[E <: Env] @Inject() (
  val env: Environment[E],
  val securedAction: SecuredAction,
  val unsecuredAction: UnsecuredAction,
  val userAwareAction: UserAwareAction,
  val defaultBodyParser: BodyParsers.Default)
  extends Silhouette[E]
