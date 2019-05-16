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
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{ ExecutionContextProvider, ExtractableRequest }
import com.mohiva.play.silhouette.api.{ AuthInfo, Logger, LoginInfo, Provider }
import com.mohiva.play.silhouette.impl.exceptions.{ IdentityNotFoundException, InvalidTOTPCodeException }
import com.mohiva.play.silhouette.impl.providers.TOTPProvider._

import scala.concurrent.Future

/**
 * TODO:
 * what about QR code?
 */
case class TOTPInfo(sharedKey: String) extends AuthInfo

/**
 * The base interface for all TOTP (Time-based One-time Password) providers.
 */
trait TOTPProvider extends Provider with ExecutionContextProvider with Logger {

  /**
   * The auth info repository.
   */
  protected val authInfoRepository: AuthInfoRepository

  /**
   * Starts the authentication process.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The login info if the authentication was successful, otherwise a failure.
   */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[LoginInfo] = {
    (
      request.extractString(providerKeyParam),
      request.extractString(verificationCodeParam)) match {
        case (Some(providerKey), Some(verificationCode)) =>
          val loginInfo = LoginInfo(id, providerKey)
          authInfoRepository.find[TOTPInfo](loginInfo).flatMap {
            case Some(authInfo) =>
              if (isVerificationCodeValid(authInfo.sharedKey, verificationCode)) {
                Future.successful(loginInfo)
              } else throw new InvalidTOTPCodeException(VerificationCodeDoesNotMatch.format(id))
            case None => throw new IdentityNotFoundException(TOTPInfoNotFound.format(id, providerKey))
          }
        case _ => throw new ProviderException(
          IncorrectRequest.format(id, requiredParams.mkString(","))
        )
      }
  }

  /**
   * Indicates if verification code is valid for related shared key
   *
   * @param sharedKey TOTP shared key accociated with user
   * @param verificationCode Verification code, presumably valid at this moment
   * @return True if the given verification code is valid, false otherwise.
   */
  protected def isVerificationCodeValid(sharedKey: String, verificationCode: String): Boolean
}

object TOTPProvider {

  /**
   * Constants
   */
  val providerKeyParam = "providerKey"
  val verificationCodeParam = "verificationCode"

  val requiredParams = List(providerKeyParam, verificationCodeParam)

  /**
   * Messages
   */
  val IncorrectRequest = "[Silhouette][%s] Incorrect request. At least one of the required parameters missing: %s"
  val TOTPInfoNotFound = "[Silhouette][%s] Could not find TOTP info for given login info: %s"
  val VerificationCodeDoesNotMatch = "[Silhouette][%s] TOTP verification code does not match"
}
