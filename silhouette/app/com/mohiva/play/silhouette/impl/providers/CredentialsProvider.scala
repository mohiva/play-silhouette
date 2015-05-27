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

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{ ExecutionContextProvider, Credentials, PasswordHasher, PasswordInfo }
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
 * @param passwordHasher The default password hasher used by the application.
 * @param passwordHasherList List of password hasher supported by the application.
 * @param executionContext The execution context to handle the asynchronous operations.
 */
class CredentialsProvider(
  authInfoRepository: AuthInfoRepository,
  passwordHasher: PasswordHasher,
  passwordHasherList: Seq[PasswordHasher])(implicit val executionContext: ExecutionContext)
  extends Provider with ExecutionContextProvider {

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
    val loginInfo = LoginInfo(id, credentials.identifier)
    authInfoRepository.find[PasswordInfo](loginInfo).flatMap {
      case Some(authInfo) => passwordHasherList.find(_.id == authInfo.hasher) match {
        case Some(hasher) if hasher.matches(authInfo, credentials.password) =>
          if (hasher != passwordHasher) {
            authInfoRepository.update(loginInfo, passwordHasher.hash(credentials.password)).map(_ => loginInfo)
          } else {
            Future.successful(loginInfo)
          }
        case Some(hasher) => throw new InvalidPasswordException(InvalidPassword.format(id))
        case None => throw new ConfigurationException(UnsupportedHasher.format(
          id, authInfo.hasher, passwordHasherList.map(_.id).mkString(", ")
        ))
      }
      case None => throw new IdentityNotFoundException(UnknownCredentials.format(id))
    }
  }
}

/**
 * The companion object.
 */
object CredentialsProvider {

  /**
   * The error messages.
   */
  val UnknownCredentials = "[Silhouette][%s] Could not find auth info for given credentials"
  val InvalidPassword = "[Silhouette][%s] Passwords does not match"
  val UnsupportedHasher = "[Silhouette][%s] Stored hasher ID `%s` isn't contained in the list of supported hasher: %s"

  /**
   * The provider constants.
   */
  val ID = "credentials"
}
