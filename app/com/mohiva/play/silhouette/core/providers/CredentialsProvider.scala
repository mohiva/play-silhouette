/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.core.providers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core._
import com.mohiva.play.silhouette.core.services.AuthInfoService
import com.mohiva.play.silhouette.core.utils.{ PasswordHasher, PasswordInfo }

/**
 * A provider for authenticating with credentials.
 *
 * The provider supports the change of password hashing algorithms on the fly. Sometimes it may be possible to change
 * the hashing algorithm used by the application. But the hashes stored in the backing store can't be converted back
 * into plain text passwords, to hash them again with the new algorithm. So if a user successfully authenticates after
 * the application has changed the hashing algorithm, the provider hashes the entered password again with the new
 * algorithm and stores the auth info in the backing store.
 *
 * @param authInfoService The auth info service.
 * @param passwordHasher The default password hasher used by the application.
 * @param passwordHasherList List of password hashers supported by the application.
 */
class CredentialsProvider(
    authInfoService: AuthInfoService,
    passwordHasher: PasswordHasher,
    passwordHasherList: Seq[PasswordHasher]) extends Provider {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  def id = CredentialsProvider.Credentials

  /**
   * Authenticates a user with its credentials.
   *
   * @param credentials The credentials to authenticate with.
   * @return The login info if the authentication was successful, otherwise None.
   */
  def authenticate(credentials: Credentials): Future[Option[LoginInfo]] = {
    val loginInfo = new LoginInfo(id, credentials.identifier)
    authInfoService.findByLoginInfo[PasswordInfo](loginInfo).map {
      case Some(authInfo) => passwordHasherList.find(_.id == authInfo.hasher) match {
        case Some(hasher) if hasher.matches(authInfo, credentials.password) =>
          if (hasher != passwordHasher) {
            authInfoService.save(loginInfo, passwordHasher.hash(credentials.password))
          }
          Some(loginInfo)
        case _ => None
      }
      case None => None
    }
  }
}

/**
 * The companion object.
 */
object CredentialsProvider {
  val Credentials = "credentials"
}

/**
 * The credentials to authenticate with.
 *
 * @param identifier The unique identifier to authenticate with.
 * @param password The password to authenticate with.
 */
case class Credentials(identifier: String, password: String)
