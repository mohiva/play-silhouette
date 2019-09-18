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
import play.api.inject.{ Binding, Module }
import play.api.mvc.AnyContent
import play.api.{ Configuration, Environment => PlayEnv }
import silhouette.Identity

/**
 * The Silhouette stack.
 *
 * Inject an instance of this trait into your controller to provide all the Silhouette actions.
 *
 * @tparam I The type of the identity.
 */
trait Silhouette[I <: Identity] {

  /**
   * The Silhouette environment.
   */
  val env: Environment[I]

  /**
   * The secured action.
   */
  protected val securedAction: SecuredAction[AnyContent]

  /**
   * The unsecured action.
   */
  protected val unsecuredAction: UnsecuredAction[AnyContent]

  /**
   * The user aware action.
   */
  protected val userAwareAction: UserAwareAction[AnyContent]

  /**
   * The global secured error handler.
   */
  protected val securedErrorHandler: SecuredErrorHandler[I]

  /**
   * The global unsecured error handler.
   */
  protected val unsecuredErrorHandler: UnsecuredErrorHandler[I]

  // scalastyle:off method.name
  /**
   * Provides the secured action implementation.
   *
   * @return The secured action implementation.
   */
  def SecuredAction: SecuredActionBuilder[I, AnyContent] =
    securedAction(env, securedErrorHandler)

  /**
   * Provides the secured request handler implementation.
   *
   * @return The secured request handler implementation.
   */
  def SecuredRequestHandler: SecuredRequestHandlerBuilder[I] =
    securedAction.requestHandler(env, securedErrorHandler)

  /**
   * Provides the unsecured action implementation.
   *
   * @return The unsecured action implementation.
   */
  def UnsecuredAction: UnsecuredActionBuilder[I, AnyContent] =
    unsecuredAction(env, unsecuredErrorHandler)

  /**
   * Provides the unsecured request handler implementation.
   *
   * @return The unsecured request handler implementation.
   */
  def UnsecuredRequestHandler: UnsecuredRequestHandlerBuilder[I] =
    unsecuredAction.requestHandler(env, unsecuredErrorHandler)

  /**
   * Provides the user-aware action implementation.
   *
   * @return The user-aware action implementation.
   */
  def UserAwareAction: UserAwareActionBuilder[I, AnyContent] =
    userAwareAction(env)

  /**
   * Provides the user-aware request handler implementation.
   *
   * @return The user-aware request handler implementation.
   */
  def UserAwareRequestHandler: UserAwareRequestHandlerBuilder[I] =
    userAwareAction.requestHandler(env)
  // scalastyle:on method.name
}

/**
 * Provides the [[Silhouette]] stack.
 *
 * @param env                   The Silhouette environment.
 * @param securedAction         The secured action.
 * @param unsecuredAction       The unsecured action.
 * @param userAwareAction       The user aware action.
 * @param securedErrorHandler   The global secured error handler.
 * @param unsecuredErrorHandler The global unsecured error handler.
 * @tparam I The type of the identity.
 */
case class SilhouetteProvider[I <: Identity] @Inject() (
  env: Environment[I],
  securedAction: SecuredAction[AnyContent],
  unsecuredAction: UnsecuredAction[AnyContent],
  userAwareAction: UserAwareAction[AnyContent],
  securedErrorHandler: SecuredErrorHandler[I],
  unsecuredErrorHandler: UnsecuredErrorHandler[I]
) extends Silhouette[I]

/**
 * Play module to provide the Silhouette stack for the given identity.
 *
 * @tparam I The type of the identity.
 */
class SilhouetteModule[I <: Identity] extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[Environment[I]].to[EnvironmentProvider[I]]
    )
  }
}

/**
 * Injection helper for the Silhouette stack.
 *
 * @tparam I The type of the identity.
 */
trait SilhouetteComponents[I <: Identity]
  extends EnvironmentComponents[I]
  with SecuredActionComponents
  with UnsecuredActionComponents
  with UserAwareActionComponents
  with SecuredErrorHandlerComponents[I]
  with UnsecuredErrorHandlerComponents[I] {

  lazy val silhouette: Silhouette[I] = SilhouetteProvider(
    environment,
    securedAction,
    unsecuredAction,
    userAwareAction,
    securedErrorHandler,
    unsecuredErrorHandler
  )
}
