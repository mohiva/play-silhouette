/**
 * Copyright 2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 * Copyright 2014 Christian Kaps (christian.kaps at mohiva dot com)
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
 *
 * This file contains source code from the Secure Social project:
 * http://securesocial.ws/
 */
package com.mohiva.play.silhouette.core.providers

import play.api.libs.ws.WS
import play.api.{Application, Logger}
import play.api.libs.json.JsObject
import com.mohiva.play.silhouette.core._


/**
 * A Vk provider
 */
class VkProvider(application: Application) extends OAuth2Provider(application) {
  val GetProfilesApi = "https://api.vk.com/method/getProfiles?fields=uid,first_name,last_name,photo&access_token="
  val Response = "response"
  val Id = "uid"
  val FirstName = "first_name"
  val LastName = "last_name"
  val Photo = "photo"
  val Error = "error"
  val ErrorCode = "error_code"
  val ErrorMessage = "error_msg"

  def fillProfile(user: SocialUser) = {
    val accessToken = user.oAuth2Info.get.accessToken
    val promise = WS.url(GetProfilesApi + accessToken).get()

    try {
      val response = awaitResult(promise)
      val json = response.json
      (json \ Error).asOpt[JsObject] match {
        case Some(error) =>
          val message = (error \ ErrorMessage).as[String]
          val errorCode = (error \ ErrorCode).as[Int]
          Logger.error("[silhouette] error retrieving profile information from Vk. Error code = %s, message = %s"
            .format(errorCode, message))
          throw new AuthenticationException()
        case _ =>
          val me = (json \ Response).apply(0)
          val userId = (me \ Id).as[Long]
          val firstName = (me \ FirstName).as[String]
          val lastName = (me \ LastName).as[String]
          val avatarUrl = (me \ Photo).asOpt[String]
          user.copy(
            identityId = IdentityId(userId.toString, id),
            firstName = firstName,
            lastName = lastName,
            fullName = firstName + " " + lastName,
            avatarUrl = avatarUrl,
            email = None
          )
      }
    } catch {
      case e: Exception => {
        Logger.error("[silhouette] error retrieving profile information from VK", e)
        throw new AuthenticationException()
      }
    }
  }

  def id = VkProvider.Vk
}

object VkProvider {
  val Vk = "vk"
}
