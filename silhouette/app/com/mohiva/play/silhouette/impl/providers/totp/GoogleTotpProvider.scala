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

import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.totp.GoogleTotpProvider._
import com.warrenstrange.googleauth.{ GoogleAuthenticator, GoogleAuthenticatorQRGenerator }
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._

/**
 * Google's TOTP authentication concrete provider implementation.
 */
class GoogleTotpProvider @Inject() (implicit val executionContext: ExecutionContext) extends TotpProvider {
  /**
   * Gets the provider ID.
   *
   * @return The provider ID.
   */
  override def id: String = ID

  /**
   * Indicates whether verification code is valid for the related shared key.
   *
   * @param sharedKey TOTP shared key associated with the user.
   * @param verificationCode Verification code, presumably valid at this moment.
   * @return True if the given verification code is valid, false otherwise.
   */
  override protected def isVerificationCodeValid(sharedKey: String, verificationCode: String): Boolean = {
    if (verificationCode == null || verificationCode.isEmpty) {
      logger.debug(VerificationCodeMustNotBeNullOrEmpty.format(id))
      false
    } else {
      if (verificationCode.forall(_.isDigit)) {
        try {
          googleAuthenticator.authorize(sharedKey, verificationCode.toInt)
        } catch {
          case e: IllegalArgumentException => {
            logger.debug(e.getMessage)
            false
          }
        }
      } else {
        logger.debug(VerificationCodeMustBeANumber.format(id))
        false
      }
    }
  }

  /**
   * Generates the shared key used together with verification code in TOTP-authentication.
   *
   * @param accountName A unique key which identifies a user on this provider (userID, email, ...).
   * @param issuer The issuer name. This parameter cannot contain the colon
   * @return The totp credentials including the shared key, scratch codes and qr url.
   */
  override def createCredentials(accountName: String, issuer: Option[String]): TotpCredentials = {
    val credentials = googleAuthenticator.createCredentials()
    val qrUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(issuer.orNull, accountName, credentials)
    val scratchCodes = credentials.getScratchCodes.asScala.map(_.toString).toSet
    TotpCredentials(TotpInfo(credentials.getKey, scratchCodes), qrUrl)
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
  val VerificationCodeMustNotBeNullOrEmpty = "[Silhouette][%s] verification code must not be null or empty"
  val VerificationCodeMustBeANumber = "[Silhouette][%s] Google's verification code must be a number"
}
