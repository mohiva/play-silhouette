/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.impl.providers

import javax.inject.Inject

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.exceptions.{ IdentityNotFoundException, InvalidPasswordException }
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider._

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A provider for authenticating with credentials.
 *
 * The provider supports the change of password hashing algorithms on the fly. Sometimes it may be possible to change
 * the hashing algorithm used by the application. But the hashes stored in the backing store can't be converted back
 * into plain text passwords, to hash them again with the new algorithm. So if a user successfully authenticates after
 * the application has changed the hashing algorithm, the provider hashes the entered password again with the new
 * algorithm and stores the auth info in the backing store.
 *
 * @param authInfoRepository The auth info repository.
 * @param passwordHasherRegistry The password hashers used by the application.
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class CredentialsProvider @Inject() (
  protected val authInfoRepository: AuthInfoRepository,
  protected val passwordHasherRegistry: PasswordHasherRegistry)(implicit val executionContext: ExecutionContext)
  extends PasswordProvider {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  override def id = ID

  /**
   * Authenticates a user with its credentials.
   *
   * @param credentials The credentials to authenticate with.
   * @return The login info if the authentication was successful, otherwise a failure.
   */
  def authenticate(credentials: Credentials): Future[LoginInfo] = {
    loginInfo(credentials).flatMap { loginInfo =>
      authenticate(loginInfo, credentials.password).map {
        case Authenticated            => loginInfo
        case InvalidPassword(error)   => throw new InvalidPasswordException(error)
        case UnsupportedHasher(error) => throw new ConfigurationException(error)
        case NotFound(error)          => throw new IdentityNotFoundException(error)
      }
    }
  }

  /**
   * Gets the login info for the given credentials.
   *
   * Override this method to manipulate the creation of the login info from the credentials.
   *
   * By default the credentials provider creates the login info with the identifier entered
   * in the form. For some cases this may not be enough. It could also be possible that a login
   * form allows a user to log in with either a username or an email address. In this case
   * this method should be overridden to provide a unique binding, like the user ID, for the
   * entered form values.
   *
   * @param credentials The credentials to authenticate with.
   * @return The login info created from the credentials.
   */
  def loginInfo(credentials: Credentials): Future[LoginInfo] = Future.successful(LoginInfo(id, credentials.identifier))
}

/**
 * The companion object.
 */
object CredentialsProvider {

  /**
   * The provider constants.
   */
  val ID = "credentials"
}
