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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.{ ExecutionContextProvider, ExtractableRequest }
import com.mohiva.play.silhouette.api.{ AuthInfo, Logger, Provider }
import com.mohiva.play.silhouette.impl.providers.TOTPProvider._
import scala.concurrent.Future

/**
 * TODO: implement
 * what about QR code?
 */
case class TOTPInfo(sharedKey: String) extends AuthInfo

/**
 * The base interface for all TOTP (Time-based One-time Password) providers.
 */
trait TOTPProvider extends Provider with ExecutionContextProvider with Logger {
  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The login info if the authentication was successful, otherwise a failure.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Boolean] = {
    (request.extractString(sharedKeyParam), request.extractString(verificationCodeParam)) match {
      case (Some(sharedKey), Some(verificationCode)) =>
        Future(isVerificationCodeValid(sharedKey, verificationCode))
      case _ => throw new ProviderException(
        IncorrectRequest.format(id, requiredParams.mkString(","))
      )
    }
  }

  /**
   * Indicates if verification code is valid for given shared key
   *
   * @param sharedKey TOTP shared key associated with the user.
   * @param verificationCode Verification code, presumably valid at this moment.
   * @return True if the given verification code is valid, false otherwise.
   */
  protected def isVerificationCodeValid(sharedKey: String, verificationCode: String): Boolean
}

object TOTPProvider {
  /**
   * Constants
   */
  val sharedKeyParam = "sharedKey"
  val verificationCodeParam = "verificationCode"

  val requiredParams = List(sharedKeyParam, verificationCodeParam)

  /**
   * Messages
   */
  val IncorrectRequest = "[Silhouette][%s] Incorrect request. At least one of the required parameters missing: %s"
  val VerificationCodeDoesNotMatch = "[Silhouette][%s] TOTP verification code doesn't match"
}
