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
import play.api.mvc.Request
import play.api.{ Configuration, Environment => PlayEnv }
import silhouette.provider.RequestProvider
import silhouette.{ ExecutionContextProvider, Identity, LoginInfo }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Provides the components needed to handle the silhouette actions.
 *
 * @tparam I The type of the identity.
 */
trait Environment[I <: Identity] extends ExecutionContextProvider {

  /**
   * A functions that retrieves the identity for the given [[LoginInfo]].
   */
  val identityReader: LoginInfo => Future[Option[I]]

  /**
   * The list of request providers.
   */
  val requestProviders: List[RequestProvider[Request[_], I]]

  /**
   * The event bus implementation.
   */
  val eventBus: EventBus
}

/**
 * Provides the [[Environment]].
 *
 * {{{
 *   EnvironmentProvider[FooUser](...)
 *   EnvironmentProvider[BarUser](...)
 * }}}
 *
 * @param identityReader   A functions that retrieves the identity for the given [[LoginInfo]].
 * @param requestProviders The list of request providers.
 * @param eventBus         The event bus implementation.
 * @tparam I The type of the identity.
 */
case class EnvironmentProvider[I <: Identity] @Inject() (
  identityReader: LoginInfo => Future[Option[I]],
  requestProviders: List[RequestProvider[Request[_], I]],
  eventBus: EventBus
)(
  implicit
  val ec: ExecutionContext
) extends Environment[I]

/**
 * Play module to provide the environment for the given identity.
 *
 * @tparam I The type of the identity.
 */
class EnvironmentModule[I <: Identity] extends Module {
  def bindings(environment: PlayEnv, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[Environment[I]].to[EnvironmentProvider[I]]
    )
  }
}

/**
 * Injection helper for the environment.
 *
 * @tparam I The type of the identity.
 */
trait EnvironmentComponents[I <: Identity] extends ExecutionContextProvider {
  def identityReader: LoginInfo => Future[Option[I]]
  def requestProviders: List[RequestProvider[Request[_], I]]
  def eventBus: EventBus

  lazy val environment: Environment[I] = EnvironmentProvider(
    identityReader,
    requestProviders,
    eventBus
  )
}
