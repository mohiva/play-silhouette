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

import com.mohiva.silhouette.services.{AuthenticatorService, IdentityService}

/**
 * The environment needed to instantiate a Silhouette controller.
 */
trait Environment[I <: Identity, T <: Authenticator] {

  /**
   * Gets the identity service implementation.
   *
   * @return The identity service implementation.
   */
  def identityService: IdentityService[I]

  /**
   * Gets the authenticator service implementation.
   *
   * @return The authenticator service implementation.
   */
  def authenticatorService: AuthenticatorService[T]

  /**
   * Gets the list of authentication providers.
   *
   * @return The list of authentication providers.
   */
  def providers: Map[String, Provider]

  /**
   * The event bus implementation.
   *
   * @return The event bus implementation.
   */
  def eventBus: EventBus
}

/**
 * The companion object.
 */
object Environment {
  def apply[I <: Identity, T <: Authenticator](
    identityServiceImpl: IdentityService[I],
    authenticatorServiceImpl: AuthenticatorService[T],
    providersImpl: Map[String, Provider],
    eventBusImpl: EventBus) = new Environment[I, T] {
    val identityService = identityServiceImpl
    val authenticatorService = authenticatorServiceImpl
    val providers = providersImpl
    val eventBus = eventBusImpl
  }
}
