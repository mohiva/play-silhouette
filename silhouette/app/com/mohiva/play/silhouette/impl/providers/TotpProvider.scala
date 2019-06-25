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
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.providers.PasswordProvider.HasherIsNotRegistered
import com.mohiva.play.silhouette.impl.providers.TotpProvider._

import scala.concurrent.Future

/**
 * TOTP authentication information intended to be stored in an authentication repository.
 *
 * @param sharedKey The key associated to an user that together with a verification
 *                  code enables authentication.
 * @param scratchCodes A sequence of hashed scratch (or recovery) codes, which can be
 *                     used each once and as alternative to verification codes.
 */
case class TotpInfo(sharedKey: String, scratchCodes: Seq[PasswordInfo]) extends AuthInfo

/**
 * TOTP authentication credentials data including plain recovery codes and URL to the
 * QR-code for first-time activation of the TOTP.
 *
 * @param totpInfo The TOTP authentication info that will be persisted in an
 *                 authentication repository.
 * @param scratchCodesPlain A sequence of scratch codes in plain text. This variant
 *                          is provided for the user to secure save the first time.
 * @param qrUrl The QR-code that matches this shared key for first time activation
 */
case class TotpCredentials(totpInfo: TotpInfo, scratchCodesPlain: Seq[String], qrUrl: String)

/**
 * The base interface for all TOTP (Time-based One-time Password) providers.
 */
trait TotpProvider extends Provider with ExecutionContextProvider with Logger {
  /**
   * The Password hasher registry to use.
   */
  val passwordHasherRegistry: PasswordHasherRegistry

  /**
   * Returns TotpInfo containing the credentials data including sharedKey and scratch codes.
   *
   * @param providerKey A unique key which identifies a user on this provider (userID, email, ...).
   * @param issuer The issuer name. This parameter cannot contain the colon character.
   * @return TotpInfo containing  the credentials data including sharedKey and scratch codes.
   */
  def createCredentials(providerKey: String, issuer: Option[String] = None): TotpCredentials

  /**
   * Returns some login info when the TOTP authentication with verification code was successful,
   * None otherwise.
   *
   * @param sharedKey A unique key which identifies a user on this provider (userID, email, ...).
   * @param verificationCode the verification code generated using TOTP.
   * @return Some login info if the authentication was successful, None otherwise.
   */
  def authenticate(sharedKey: String, verificationCode: String): Future[Option[LoginInfo]] = {
    Future(
      if (isVerificationCodeValid(sharedKey, verificationCode)) {
        Some(LoginInfo(ID, sharedKey))
      } else {
        logger.debug(VerificationCodeDoesNotMatch)
        None
      }
    )
  }

  /**
   * Authenticate the user using a TOTP scratch (or recovery) code. This method will
   * check each of the previously hashed scratch codes and find the first one that
   * matches the one entered by the user. The one found is removed from `totpInfo` and returned
   * for easy client-side bookkeeping.
   *
   * @param totpInfo The original TOTP info containing the hashed scratch codes.
   * @param plainScratchCode The plain scratch code entered by the user.
   * @return Some tuple consisting of (`PasswordInfo`, `TotpInfo`) if the authentication was successful, None otherwise.
   */
  def authenticate(totpInfo: TotpInfo, plainScratchCode: String): Future[Option[(PasswordInfo, TotpInfo)]] = Future {
    Option(totpInfo).flatMap { totpInfo =>
      Option(plainScratchCode).flatMap {
        case plainScratchCode: String if plainScratchCode.nonEmpty =>
          val found: Option[PasswordInfo] = totpInfo.scratchCodes.find { scratchCode =>
            passwordHasherRegistry.find(scratchCode) match {
              case Some(hasher) => hasher.matches(scratchCode, plainScratchCode)
              case None =>
                logger.error(HasherIsNotRegistered.format(id, scratchCode.hasher, passwordHasherRegistry.all.map(_.id).mkString(", ")))
                false
            }
          }

          found.map { deleted =>
            deleted -> totpInfo.copy(scratchCodes = totpInfo.scratchCodes.filterNot(_ == deleted))
          }
        case _ => None
      }
    }
  }

  /**
   * Returns true in case the verification code is valid for given shared key, false otherwise.
   *
   * @param sharedKey The TOTP shared key associated with the user.
   * @param verificationCode The verification code, presumably valid at this moment.
   * @return true in case the verification code is valid for given shared key, false otherwise.
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
  val ScratchCodesMustBeClearedOut = "[Silhouette][%s] TOTP plain scratch codes must be cleared out"
}