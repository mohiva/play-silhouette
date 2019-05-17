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
import com.mohiva.play.silhouette.impl.providers.TotpProvider._

import scala.concurrent.Future

/**
 * TOTP credentials data.
 *
 * @param sharedKey The key shared assciated with the user.
 * @param scratchCodes The list of scratch codes, which can be used instead of verification codes
 * @param qrUrl The QR-code which contains shared key
 */
case class TotpInfo(sharedKey: String, scratchCodes: List[String], qrUrl: String) extends AuthInfo

/**
 * The base interface for all TOTP (Time-based One-time Password) providers.
 */
trait TotpProvider extends Provider with ExecutionContextProvider with Logger {
  /**
   * Generate shared key used together with verification code in TOTP-authentication
   *
   * @param providerKey A unique key which identifies a user on this provider (userID, email, ...).
   * @param issuer The issuer name. This parameter cannot contain the colon
   * @return The unique shared key
   */
  def createCredentials(providerKey: String, issuer: Option[String] = None): TotpInfo

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
   * @param sharedKey The TOTP shared key associated with the user.
   * @param verificationCode The verification code, presumably valid at this moment.
   * @return True if the given verification code is valid, false otherwise.
   */
  protected def isVerificationCodeValid(sharedKey: String, verificationCode: String): Boolean
}

object TotpProvider {
  /**
   * Constants
   */
  val sharedKeyParam = "sharedKey"
  val verificationCodeParam = "verificationCode"
  val requiredParams = List(sharedKeyParam, verificationCodeParam)

  /**
   * The provider Id.
   */
  val ID = "totp"

  /**
   * Messages
   */
  val IncorrectRequest = "[Silhouette][%s] Incorrect request. At least one of the required parameters missing: %s"
  val VerificationCodeDoesNotMatch = "[Silhouette][%s] TOTP verification code doesn't match"
}
