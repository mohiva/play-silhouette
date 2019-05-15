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

import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{ExecutionContextProvider, ExtractableRequest}
import com.mohiva.play.silhouette.api.{AuthInfo, Logger, LoginInfo, Provider}

import scala.concurrent.Future

/**
  * TODO:
  * what about QR code?
  * need tests
  */
case class TOTPInfo(ticket: String) extends AuthInfo

/**
  * The base interface for all TOTP (Time-based One-time Password) providers.
  */
trait TOTPProvider extends Provider with ExecutionContextProvider with Logger {

  /**
    * Constants
    */
  sealed val providerKeyParam = "providerKey"
  sealed val sharedKeyParam = "sharedKey"
  sealed val verificationCodeParam = "verificationCode"

  /**
    * The auth info repository.
    */
  protected val authInfoRepository: AuthInfoRepository

  /**
    * TODO:
    */
  def authenticate[B]()(implicit request: ExtractableRequest[B]): Future[Option[LoginInfo]] = {
    (request.extractString(providerKeyParam),
      request.extractString(sharedKeyParam),
      request.extractString(verificationCodeParam)) match {
      case (Some(providerKey), Some(sharedKey), Some(verificationCode)) =>
        val loginInfo = LoginInfo(id, providerKey)
        authInfoRepository.find[TOTPInfo](loginInfo).flatMap {
          case Some(_) =>
            if (isVerificationCodeValid(sharedKey, verificationCode)) {
              Future.successful(Some(loginInfo))
            } else Future.successful(None)
          case None => Future.successful(None)
        }
      case _ => Future.successful(None)
    }
  }

  /**
    * TODO:
    */
  protected def isVerificationCodeValid(sharedKey: String, verificationCode: String): Boolean
}
