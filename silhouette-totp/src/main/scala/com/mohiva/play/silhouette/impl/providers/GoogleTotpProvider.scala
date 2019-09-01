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

import com.mohiva.play.silhouette.ScalaCompat.JavaConverters._
import com.mohiva.play.silhouette.api.util.{ PasswordHasherRegistry, PasswordInfo, _ }
import com.mohiva.play.silhouette.api.{ AuthInfo, LoginInfo, _ }
import com.mohiva.play.silhouette.impl.providers.GoogleTotpProvider._
import com.mohiva.play.silhouette.impl.providers.PasswordProvider._
import com.warrenstrange.googleauth.{ GoogleAuthenticator, GoogleAuthenticatorQRGenerator }
import javax.inject.Inject

import scala.concurrent.{ ExecutionContext, Future }

/**
 * TOTP authentication information intended to be stored in an authentication repository.
 *
 * @param sharedKey    The key associated to an user that together with a verification
 *                     code enables authentication.
 * @param scratchCodes A sequence of hashed scratch (or recovery) codes, which can be
 *                     used each once and as alternative to verification codes.
 */
case class GoogleTotpInfo(sharedKey: String, scratchCodes: Seq[PasswordInfo]) extends AuthInfo

/**
 * TOTP authentication credentials data including plain recovery codes and URL to the
 * QR-code for first-time activation of the TOTP.
 *
 * @param totpInfo          The TOTP authentication info that will be persisted in an
 *                          authentication repository.
 * @param scratchCodesPlain A sequence of scratch codes in plain text. This variant
 *                          is provided for the user to secure save the first time.
 * @param qrUrl             The QR-code that matches this shared key for first time activation
 */
case class GoogleTotpCredentials(totpInfo: GoogleTotpInfo, scratchCodesPlain: Seq[String], qrUrl: String)

/**
 * Google's TOTP authentication concrete provider implementation.
 *
 * @param injectedPasswordHasherRegistry used to hash the scratch (or recovery) codes.
 * @param executionContext               the execution context.
 */
class GoogleTotpProvider @Inject() (injectedPasswordHasherRegistry: PasswordHasherRegistry)(
  implicit
  val executionContext: ExecutionContext
) extends Provider with ExecutionContextProvider with Logger {

  /**
   * Returns the provider ID.
   *
   * @return the provider ID.
   */
  override def id: String = ID

  /**
   * The Password hasher registry to use
   */
  val passwordHasherRegistry: PasswordHasherRegistry = injectedPasswordHasherRegistry

  /**
   * Returns true when the verification code is valid for the related shared key, false otherwise.
   *
   * @param sharedKey        TOTP shared key associated with the user.
   * @param verificationCode Verification code, presumably valid at this moment.
   * @return true when the verification code is valid for the related shared key, false otherwise.
   */
  protected def isVerificationCodeValid(sharedKey: String, verificationCode: String): Boolean = {
    try {
      googleAuthenticator.authorize(sharedKey, verificationCode.toInt)
    } catch {
      case e: IllegalArgumentException =>
        logger.debug(e.getMessage)
        false
    }
  }

  /**
   * Returns the generated TOTP credentials including the shared key along with hashed scratch codes
   * for safe storage, plain text scratch codes for first time use and the url to the QR activation code.
   *
   * @param accountName A unique key which identifies a user on this provider (userID, email, ...).
   * @param issuer      The issuer name. This parameter cannot contain the colon
   * @return The generated TOTP credentials including the shared key, scratch codes and qr url.
   */
  def createCredentials(accountName: String, issuer: Option[String] = None): GoogleTotpCredentials = {
    val credentials = googleAuthenticator.createCredentials()
    val qrUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(issuer.orNull, accountName, credentials)
    val currentHasher = passwordHasherRegistry.current
    val scratchCodesPlain = credentials.getScratchCodes.asScala.map(_.toString).toSeq
    val hashedScratchCodes = scratchCodesPlain.map { scratchCode =>
      currentHasher.hash(scratchCode)
    }

    GoogleTotpCredentials(GoogleTotpInfo(credentials.getKey, hashedScratchCodes), scratchCodesPlain, qrUrl)
  }

  /**
   * Returns some login info when the TOTP authentication with verification code was successful,
   * None otherwise.
   *
   * @param sharedKey        A unique key which identifies a user on this provider (userID, email, ...).
   * @param verificationCode the verification code generated using TOTP.
   * @return Some login info if the authentication was successful, None otherwise.
   */
  def authenticate(sharedKey: String, verificationCode: String): Future[Option[LoginInfo]] = {
    Future(
      if (isVerificationCodeValid(sharedKey, verificationCode)) {
        Some(LoginInfo(id, sharedKey))
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
   * @param totpInfo         The original TOTP info containing the hashed scratch codes.
   * @param plainScratchCode The plain scratch code entered by the user.
   * @return Some tuple consisting of (`PasswordInfo`, `TotpInfo`) if the authentication was successful, None otherwise.
   */
  def authenticate(totpInfo: GoogleTotpInfo, plainScratchCode: String): Future[Option[(PasswordInfo, GoogleTotpInfo)]] = Future {
    if (plainScratchCode.nonEmpty) {
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
    } else None
  }
}

/**
 * The companion object.
 */
object GoogleTotpProvider {

  /**
   * Actual Google authenticator provider.
   */
  private val googleAuthenticator = new GoogleAuthenticator()

  /**
   * The provider Id.
   */
  val ID = "googleTotp"

  /**
   * Messages
   */
  val VerificationCodeDoesNotMatch = "[Silhouette][%s] TOTP verification code doesn't match"
}
