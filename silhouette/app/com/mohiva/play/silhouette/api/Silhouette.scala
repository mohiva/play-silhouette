
package com.mohiva.play.silhouette.api

import javax.inject.Inject

import com.mohiva.play.silhouette.api.actions._

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
   * The user aware action stack.
   */
  val userAwareAction: UserAwareAction

  /**
   * Provides the secured action implementation.
   *
   * @return The secured action implementation.
   */
  def SecuredAction: SecuredActionBuilder[E] = securedAction(env)

  /**
   * Provides the secured request handler implementation.
   *
   * @return The secured request handler implementation.
   */
  def SecuredRequestHandler: SecuredRequestHandlerBuilder[E] = securedAction.requestHandler(env)

  /**
   * Provides the user-aware action implementation.
   *
   * @return The user-aware action implementation.
   */
  def UserAwareAction: UserAwareActionBuilder[E] = userAwareAction(env)

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
 * @param env The Silhouette environment.
 * @param securedAction The secured action stack.
 * @param userAwareAction The user aware action stack.
 * @tparam E The type of the environment.
 */
class SilhouetteProvider[E <: Env] @Inject() (
  val env: Environment[E],
  val securedAction: SecuredAction,
  val userAwareAction: UserAwareAction)
  extends Silhouette[E]
