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

import com.mohiva.play.silhouette.api.services.{ AuthenticatorService, IdentityService }
import com.mohiva.play.silhouette.api.util.ExecutionContextProvider

import scala.concurrent.ExecutionContext

/**
 * The environment type.
 *
 * Defines the [[Identity]] and [[Authenticator]] types for an environment. It is possible
 * to implement as many types as needed. This has the advantage that an application isn't
 * bound only to a single `Identity` -> `Authenticator` combination.
 *
 * To define a new environment type create a new trait with the appropriate [[Identity]] and
 * [[Authenticator]] types:
 *
 * {{{
 *   trait SessionEnv {
 *     type I = User
 *     type A = SessionAuthenticator
 *   }
 *   trait JWTEnv {
 *     type I = User
 *     type A = JWTAuthenticator
 *   }
 * }}}
 */
trait Env {
  type I <: Identity
  type A <: Authenticator
}

/**
 * Provides the components needed to handle a secured request.
 *
 * It's possible to declare different environments for different environment types. The
 * [[com.mohiva.play.silhouette.api.services.IdentityService]] and the
 * [[com.mohiva.play.silhouette.api.services.AuthenticatorService]] are bound to the appropriate types
 * defined in the environment type. But the [[EventBus]] and the list of [[RequestProvider]]
 * instances can be defined as needed for every environment type.
 */
trait Environment[E <: Env] extends ExecutionContextProvider {

  /**
   * Gets the identity service implementation.
   *
   * @return The identity service implementation.
   */
  def identityService: IdentityService[E#I]

  /**
   * Gets the authenticator service implementation.
   *
   * @return The authenticator service implementation.
   */
  def authenticatorService: AuthenticatorService[E#A]

  /**
   * Gets the list of request providers.
   *
   * @return The list of request providers.
   */
  def requestProviders: Seq[RequestProvider]

  /**
   * The event bus implementation.
   *
   * @return The event bus implementation.
   */
  def eventBus: EventBus
}

/**
 * Companion object to easily create environment instances.
 *
 * {{{
 *   Environment[SessionEnv](...)
 *   Environment[JWTEnv](...)
 * }}}
 */
object Environment {
  def apply[E <: Env](
    identityServiceImpl: IdentityService[E#I],
    authenticatorServiceImpl: AuthenticatorService[E#A],
    requestProvidersImpl: Seq[RequestProvider],
    eventBusImpl: EventBus)(implicit ec: ExecutionContext) = new Environment[E] {
    val identityService = identityServiceImpl
    val authenticatorService = authenticatorServiceImpl
    val requestProviders = requestProvidersImpl
    val eventBus = eventBusImpl
    val executionContext = ec
  }
}
