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

import javax.inject.Inject

import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{ Logger, LoginInfo, RequestProvider }
import com.mohiva.play.silhouette.impl.providers.BasicAuthProvider._
import play.api.http.HeaderNames
import play.api.mvc.{ Request, RequestHeader }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A request provider implementation which supports HTTP basic authentication.
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
class BasicAuthProvider @Inject() (
  protected val authInfoRepository: AuthInfoRepository,
  protected val passwordHasherRegistry: PasswordHasherRegistry)(implicit val executionContext: ExecutionContext)
  extends RequestProvider with PasswordProvider with Logger {

  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  override def id = ID

  /**
   * Authenticates an identity based on credentials sent in a request.
   *
   * @param request The request.
   * @tparam B The type of the body.
   * @return Some login info on successful authentication or None if the authentication was unsuccessful.
   */
  override def authenticate[B](request: Request[B]): Future[Option[LoginInfo]] = {
    getCredentials(request) match {
      case Some(credentials) =>
        val loginInfo = LoginInfo(id, credentials.identifier)
        authenticate(loginInfo, credentials.password).map {
          case Authenticated => Some(loginInfo)
          case InvalidPassword(error) =>
            logger.debug(error)
            None
          case UnsupportedHasher(error) => throw new ConfigurationException(error)
          case NotFound(error) =>
            logger.debug(error)
            None
        }
      case None => Future.successful(None)
    }
  }

  /**
   * Encodes the credentials.
   *
   * @param request Contains the colon-separated name-value pairs in clear-text string format
   * @return The users credentials as plaintext
   */
  def getCredentials(request: RequestHeader): Option[Credentials] = {
    request.headers.get(HeaderNames.AUTHORIZATION) match {
      case Some(header) if header.startsWith("Basic ") =>
        Base64.decode(header.replace("Basic ", "")).split(":", 2) match {
          case credentials if credentials.length == 2 => Some(Credentials(credentials(0), credentials(1)))
          case _                                      => None
        }
      case _ => None
    }
  }
}

/**
 * The companion object.
 */
object BasicAuthProvider {

  /**
   * The provider constants.
   */
  val ID = "basic-auth"
}
