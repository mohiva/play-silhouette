package com.mohiva.play.silhouette.test

import java.util.UUID

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.services.{ AuthenticatorService, IdentityService }
import com.mohiva.play.silhouette.api.util.Clock
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.daos.AuthenticatorDAO
import com.mohiva.play.silhouette.impl.util.{ DefaultFingerprintGenerator, SecureRandomIDGenerator }
import play.api.mvc.RequestHeader

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.runtime.universe._

/**
 * A fake identity.
 *
 * @param loginInfo The linked login info for an identity.
 */
case class FakeIdentity(loginInfo: LoginInfo) extends Identity

/**
 * A fake identity service implementation which can handle a predefined list of identities.
 *
 * @param identities The list of identities this service is responsible for.
 * @tparam I The type of the identity to handle.
 */
class FakeIdentityService[I <: Identity](identities: I*) extends IdentityService[I] {

  /**
   * Retrieves an identity that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve an identity.
   * @return The retrieved identity or None if no identity could be retrieved for the given login info.
   */
  def retrieve(loginInfo: LoginInfo): Future[Option[I]] = {
    Future.successful(identities.find(_.loginInfo == loginInfo))
  }
}

/**
 * A fake authenticator DAO which stores authenticators in memory.
 *
 * @tparam T The type of the authenticator to handle.
 */
class FakeAuthenticatorDAO[T <: StorableAuthenticator] extends AuthenticatorDAO[T] {

  /**
   * The data store for the OAuth1 info.
   */
  var data: mutable.HashMap[String, T] = mutable.HashMap()

  /**
   * Saves the authenticator.
   *
   * @param authenticator The authenticator to save.
   * @return The saved auth authenticator.
   */
  def save(authenticator: T): Future[T] = {
    data += (authenticator.id -> authenticator)
    Future.successful(authenticator)
  }

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
  new SessionAuthenticatorSettings(),
  new DefaultFingerprintGenerator(),
  Clock())

/**
 * A fake cookie authenticator service.
 */
case class FakeCookieAuthenticatorService() extends CookieAuthenticatorService(
  new CookieAuthenticatorSettings(),
  new FakeAuthenticatorDAO[CookieAuthenticator],
  new DefaultFingerprintGenerator(),
  new SecureRandomIDGenerator(),
  Clock())

/**
 * A fake bearer token authenticator service.
 */
case class FakeBearerTokenAuthenticatorService() extends BearerTokenAuthenticatorService(
  new BearerTokenAuthenticatorSettings(),
  new FakeAuthenticatorDAO[BearerTokenAuthenticator],
  new SecureRandomIDGenerator(),
  Clock())

/**
 * A fake JWT authenticator service.
 */
case class FakeJWTAuthenticatorService() extends JWTAuthenticatorService(
  new JWTAuthenticatorSettings(sharedSecret = UUID.randomUUID().toString),
  None,
  new SecureRandomIDGenerator(),
  Clock())

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
      case t if t <:< typeOf[SessionAuthenticator] => FakeSessionAuthenticatorService()
      case t if t <:< typeOf[CookieAuthenticator] => FakeCookieAuthenticatorService()
      case t if t <:< typeOf[BearerTokenAuthenticator] => FakeBearerTokenAuthenticatorService()
      case t if t <:< typeOf[JWTAuthenticator] => FakeJWTAuthenticatorService()
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
   * @tparam T The type of the authenticator,
   * @return A authenticator instance.
   */
  def apply[T <: Authenticator](loginInfo: LoginInfo)(implicit env: Environment[_, T], requestHeader: RequestHeader): T = {
    env.authenticatorService.create(loginInfo)
  }
}

/**
 * A fake environment implementation.
 *
 * @param identity The identity to return inside a Silhouette action.
 * @tparam I The type of the identity.
 * @tparam T The type of the authenticator.
 */
case class FakeEnvironment[I <: Identity, T <: Authenticator: TypeTag](identity: I) extends Environment[I, T] {

  /**
   * Gets the identity service implementation.
   *
   * @return The identity service implementation.
   */
  val identityService: IdentityService[I] = new FakeIdentityService[I](identity)

  /**
   * Gets the authenticator service implementation.
   *
   * @return The authenticator service implementation.
   */
  val authenticatorService: AuthenticatorService[T] = FakeAuthenticatorService[T]()

  /**
   * Gets the list of authentication providers.
   *
   * @return The list of authentication providers.
   */
  val providers: Map[String, Provider] = Map()

  /**
   * The event bus implementation.
   *
   * @return The event bus implementation.
   */
  val eventBus: EventBus = EventBus()
}
