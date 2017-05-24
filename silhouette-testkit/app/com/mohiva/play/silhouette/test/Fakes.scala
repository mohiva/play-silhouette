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
package com.mohiva.play.silhouette.test

import java.util.UUID

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.crypto.{ Base64AuthenticatorEncoder, CookieSigner }
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.api.services.{ AuthenticatorService, IdentityService }
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.util.{ DefaultFingerprintGenerator, SecureRandomIDGenerator }
import play.api.mvc.{ DefaultCookieHeaderEncoding, DefaultSessionCookieBaker, RequestHeader }

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.runtime.universe._
import scala.util.Success

/**
 * A fake identity.
 *
 * @param loginInfo The linked login info for an identity.
 */
case class FakeIdentity(loginInfo: LoginInfo) extends Identity

/**
 * A fake identity service implementation which can handle a predefined list of identities.
 *
 * @param identities A list of (login info -> identity) pairs this service is responsible for.
 * @tparam I The type of the identity to handle.
 */
class FakeIdentityService[I <: Identity](identities: (LoginInfo, I)*) extends IdentityService[I] {

  /**
   * Retrieves an identity that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve an identity.
   * @return The retrieved identity or None if no identity could be retrieved for the given login info.
   */
  def retrieve(loginInfo: LoginInfo): Future[Option[I]] = {
    Future.successful(identities.find(_._1 == loginInfo).map(_._2))
  }
}

/**
 * A fake authenticator repository which persists authenticators in memory.
 *
 * @tparam T The type of the authenticator to handle.
 */
class FakeAuthenticatorRepository[T <: StorableAuthenticator] extends AuthenticatorRepository[T] {

  /**
   * The data store for the OAuth1 info.
   */
  var data: mutable.HashMap[String, T] = mutable.HashMap()

  /**
   * Finds the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return The found authenticator or None if no authenticator could be found for the given ID.
   */
  def find(id: String): Future[Option[T]] = {
    Future.successful(data.get(id))
  }

  /**
   * Adds a new authenticator.
   *
   * @param authenticator The authenticator to add.
   * @return The added authenticator.
   */
  def add(authenticator: T): Future[T] = {
    data += (authenticator.id -> authenticator)
    Future.successful(authenticator)
  }

  /**
   * Updates an already existing authenticator.
   *
   * @param authenticator The authenticator to update.
   * @return The updated authenticator.
   */
  def update(authenticator: T): Future[T] = {
    data += (authenticator.id -> authenticator)
    Future.successful(authenticator)
  }

  /**
   * Removes the authenticator for the given ID.
   *
   * @param id The authenticator ID.
   * @return An empty future.
   */
  def remove(id: String): Future[Unit] = {
    data -= id
    Future.successful(())
  }
}

/**
 * A fake session authenticator service.
 */
case class FakeSessionAuthenticatorService() extends SessionAuthenticatorService(
  SessionAuthenticatorSettings(),
  new DefaultFingerprintGenerator(),
  new Base64AuthenticatorEncoder,
  new DefaultSessionCookieBaker(),
  Clock())

/**
 * A fake cookie authenticator service.
 */
case class FakeCookieAuthenticatorService() extends CookieAuthenticatorService(
  CookieAuthenticatorSettings(),
  None,
  new CookieSigner {
    def sign(data: String) = data
    def extract(message: String) = Success(message)
  },
  new DefaultCookieHeaderEncoding(),
  new Base64AuthenticatorEncoder,
  new DefaultFingerprintGenerator(),
  new SecureRandomIDGenerator(),
  Clock())

/**
 * A fake bearer token authenticator service.
 */
case class FakeBearerTokenAuthenticatorService() extends BearerTokenAuthenticatorService(
  BearerTokenAuthenticatorSettings(),
  new FakeAuthenticatorRepository[BearerTokenAuthenticator],
  new SecureRandomIDGenerator(),
  Clock())

/**
 * A fake JWT authenticator service.
 */
case class FakeJWTAuthenticatorService() extends JWTAuthenticatorService(
  JWTAuthenticatorSettings(sharedSecret = UUID.randomUUID().toString),
  None,
  new Base64AuthenticatorEncoder,
  new SecureRandomIDGenerator(),
  Clock())

/**
 * A fake Dummy authenticator service.
 */
case class FakeDummyAuthenticatorService() extends DummyAuthenticatorService

/**
 * A fake authenticator service factory.
 */
object FakeAuthenticatorService {

  /**
   * Creates a new fake authenticator for the given authenticator type.
   *
   * @tparam T The type of the authenticator.
   * @return A fully configured authenticator instance.
   */
  def apply[T <: Authenticator: TypeTag](): AuthenticatorService[T] = {
    (typeOf[T] match {
      case t if t <:< typeOf[SessionAuthenticator]     => FakeSessionAuthenticatorService()
      case t if t <:< typeOf[CookieAuthenticator]      => FakeCookieAuthenticatorService()
      case t if t <:< typeOf[BearerTokenAuthenticator] => FakeBearerTokenAuthenticatorService()
      case t if t <:< typeOf[JWTAuthenticator]         => FakeJWTAuthenticatorService()
      case t if t <:< typeOf[DummyAuthenticator]       => FakeDummyAuthenticatorService()
    }).asInstanceOf[AuthenticatorService[T]]
  }
}

/**
 * A fake authenticator.
 *
 * @param loginInfo The linked login info for an identity.
 * @param id The ID of the authenticator.
 * @param isValid True if the authenticator is valid, false otherwise.
 */
case class FakeAuthenticator(loginInfo: LoginInfo, id: String = UUID.randomUUID().toString, isValid: Boolean = true)
  extends StorableAuthenticator

/**
 * A fake authenticator factory.
 */
object FakeAuthenticator {

  /**
   * Creates a new fake authenticator for the given authenticator type.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param env The Silhouette environment.
   * @param requestHeader The request header.
   * @tparam E The type of the environment,
   * @return A authenticator instance.
   */
  def apply[E <: Env](loginInfo: LoginInfo)(implicit env: Environment[E], requestHeader: RequestHeader): E#A = {
    env.authenticatorService.create(loginInfo)
  }
}

/**
 * A fake environment implementation.
 *
 * @param identities A list of (login info -> identity) pairs to return inside a Silhouette action.
 * @param requestProviders The list of request providers.
 * @param eventBus The event bus implementation.
 * @param executionContext The execution context to handle the asynchronous operations.
 * @param tt The type tag of the authenticator type.
 * @tparam E The type of the environment.
 */
case class FakeEnvironment[E <: Env](
  identities: Seq[(LoginInfo, E#I)],
  requestProviders: Seq[RequestProvider] = Seq(),
  eventBus: EventBus = EventBus()
)(
  implicit
  val executionContext: ExecutionContext,
  tt: TypeTag[E#A]
) extends Environment[E] {

  /**
   * The identity service implementation.
   */
  val identityService: IdentityService[E#I] = new FakeIdentityService[E#I](identities: _*)

  /**
   * The authenticator service implementation.
   */
  val authenticatorService: AuthenticatorService[E#A] = FakeAuthenticatorService[E#A]()
}
