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

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.util.ExecutionContextProvider
import com.mohiva.play.silhouette.impl.providers.TotpProvider._

import scala.concurrent.Future

/**
 * TOTP authentication information.
 *
 * @param sharedKey The shared key which is used together with verification code in TOTP-authentication
 * @param scratchCodes The list of scratch codes, which can be used instead of verification codes
 */
case class TotpInfo(sharedKey: String, scratchCodes: Seq[String]) extends AuthInfo

/**
 * TOTP authentication credentials data.
 *
 * @param totpInfo The TOTP authentication info
 * @param qrUrl The QR-code that matches this shared key
 */
case class TotpCredentials(totpInfo: TotpInfo, qrUrl: String)

/**
 * The base interface for all TOTP (Time-based One-time Password) providers.
 */
trait TotpProvider extends Provider with ExecutionContextProvider with Logger {
  /**
   * Generate shared key used together with verification code in TOTP-authentication
   *
   * @param providerKey A unique key which identifies a user on this provider (userID, email, ...).
   * @param issuer The issuer name. This parameter cannot contain the colon character.
   * @return TotpInfo contaning the credentials data including sharedKey and scratch codes.
   */
  def createCredentials(providerKey: String, issuer: Option[String] = None): TotpCredentials

  /**
   * Starts the authentication process.
   *
   * @param sharedKey A unique key which identifies a user on this provider (userID, email, ...).
   * @param verificationCode the verification code generated using TOTP.
   * @return Some login info if the authentication was successful, none otherwise.
   */
  def authenticate(sharedKey: String, verificationCode: String): Future[Option[LoginInfo]] = {
    Future(
      isVerificationCodeValid(sharedKey, verificationCode) match {
        case true => Some(LoginInfo(ID, sharedKey))
        case _ => {
          logger.debug(VerificationCodeDoesNotMatch)
          None
        }
      }
    )
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
   * The provider Id.
   */
  val ID = "totp"

  /**
   * Messages
   */
  val VerificationCodeDoesNotMatch = "[Silhouette][%s] TOTP verification code doesn't match"
}
