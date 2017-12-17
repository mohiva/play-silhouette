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
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{ ExecutionContextProvider, PasswordHasherRegistry, PasswordInfo }
import com.mohiva.play.silhouette.api.{ LoginInfo, Provider }
import com.mohiva.play.silhouette.impl.providers.PasswordProvider._

import scala.concurrent.Future

/**
 * Base provider which provides shared functionality to authenticate with a password.
 */
trait PasswordProvider extends Provider with ExecutionContextProvider {

  /**
   * The authentication state.
   */
  sealed trait State

  /**
   * Indicates that the authentication was successful.
   */
  case object Authenticated extends State

  /**
   * Indicates that the entered password doesn't match with the stored one.
   */
  case class InvalidPassword(error: String) extends State

  /**
   * Indicates that the stored password cannot be checked with the registered hashers.
   */
  case class UnsupportedHasher(error: String) extends State

  /**
   * Indicates that no password info was stored for the login info.
   */
  case class NotFound(error: String) extends State

  /**
   * The auth info repository.
   */
  protected val authInfoRepository: AuthInfoRepository

  /**
   * The password hashers used by the application.
   */
  protected val passwordHasherRegistry: PasswordHasherRegistry

  /**
   * Authenticates a user
   *
   * @param loginInfo The login info to search the password info for.
   * @param password The user password to authenticate with.
   * @return The authentication state.
   */
  def authenticate(loginInfo: LoginInfo, password: String): Future[State] = {
    authInfoRepository.find[PasswordInfo](loginInfo).flatMap {
      case Some(passwordInfo) => passwordHasherRegistry.find(passwordInfo) match {
        case Some(hasher) if hasher.matches(passwordInfo, password) =>
          if (passwordHasherRegistry.isDeprecated(hasher) || hasher.isDeprecated(passwordInfo).contains(true)) {
            authInfoRepository.update(loginInfo, passwordHasherRegistry.current.hash(password)).map { _ =>
              Authenticated
            }
          } else {
            Future.successful(Authenticated)
          }
        case Some(hasher) => Future.successful(InvalidPassword(PasswordDoesNotMatch.format(id)))
        case None => Future.successful(UnsupportedHasher(HasherIsNotRegistered.format(
          id, passwordInfo.hasher, passwordHasherRegistry.all.map(_.id).mkString(", ")
        )))
      }
      case None => Future.successful(NotFound(PasswordInfoNotFound.format(id, loginInfo)))
    }
  }
}

/**
 * The companion object.
 */
object PasswordProvider {

  /**
   * The error messages.
   */
  val PasswordDoesNotMatch = "[Silhouette][%s] Password does not match"
  val HasherIsNotRegistered = "[Silhouette][%s] Stored hasher ID `%s` isn't registered as supported hasher: %s"
  val PasswordInfoNotFound = "[Silhouette][%s] Could not find password info for given login info: %s"
}
