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
package com.mohiva.play.silhouette.impl.providers.totp

import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.totp.GoogleTotpProvider._
import com.warrenstrange.googleauth.{ GoogleAuthenticator, GoogleAuthenticatorQRGenerator }
import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

/**
 * Google's TOTP authentication concrete provider implementation.
  *
 * @param injectedPasswordHasherRegistry used to hash the scratch (or recovery) codes.
 * @param executionContext the execution context.
 */
class GoogleTotpProvider @Inject() (injectedPasswordHasherRegistry: PasswordHasherRegistry)(implicit val executionContext: ExecutionContext) extends TotpProvider {
  /**
   * Returns the provider ID.
   *
   * @return the provider ID.
   */
  override def id: String = ID

  /**
   * The Password hasher registry to use
   */
  override val passwordHasherRegistry = injectedPasswordHasherRegistry

  /**
   * Returns true when the verification code is valid for the related shared key, false otherwise.
   *
   * @param sharedKey TOTP shared key associated with the user.
   * @param verificationCode Verification code, presumably valid at this moment.
   * @return true when the verification code is valid for the related shared key, false otherwise.
   */
  override protected def isVerificationCodeValid(sharedKey: String, verificationCode: String): Boolean = {
    Option(sharedKey).map {
      case sharedKey: String if sharedKey.nonEmpty => {
        Option(verificationCode).map {
          case verificationCode: String if verificationCode.nonEmpty && verificationCode.forall(_.isDigit) => {
            try {
              googleAuthenticator.authorize(sharedKey, verificationCode.toInt)
            } catch {
              case e: IllegalArgumentException => {
                logger.debug(e.getMessage)
                false
              }
            }
          }
          case verificationCode: String if verificationCode.nonEmpty => {
            logger.debug(VerificationCodeMustBeANumber.format(id))
            false
          }
          case _ => {
            logger.debug(VerificationCodeMustNotBeNullOrEmpty.format(id))
            false
          }
        }.getOrElse(false)
      }
      case _ => {
        logger.debug(SharedKeyMustNotBeNullOrEmpty.format(id))
        false
      }
    }.getOrElse(false)
  }

  /**
   * Returns the generated TOTP credentials including the shared key along with hashed scratch codes
   * for safe storage, plain text scratch codes for first time use and the url to the QR activation code.
   *
   * @param accountName A unique key which identifies a user on this provider (userID, email, ...).
   * @param issuer The issuer name. This parameter cannot contain the colon
   * @return the generated TOTP credentials including the shared key, scratch codes and qr url.
   */
  override def createCredentials(accountName: String, issuer: Option[String]): TotpCredentials = {
    val credentials = googleAuthenticator.createCredentials()
    val qrUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(issuer.orNull, accountName, credentials)
    val currentHasher = passwordHasherRegistry.current
    val scratchCodesPlain = credentials.getScratchCodes.asScala.map(_.toString)
    val hashedScratchCodes = scratchCodesPlain.map { scratchCode =>
      currentHasher.hash(scratchCode)
    }
    TotpCredentials(TotpInfo(credentials.getKey, hashedScratchCodes), scratchCodesPlain, qrUrl)
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
  val ID = TotpProvider.ID

  /**
   * Messages
   */
  val SharedKeyMustNotBeNullOrEmpty = "[Silhouette][%s] shared key must not be null or empty"
  val VerificationCodeMustNotBeNullOrEmpty = "[Silhouette][%s] verification code must not be null or empty"
  val VerificationCodeMustBeANumber = "[Silhouette][%s] Google's verification code must be a number"
}
