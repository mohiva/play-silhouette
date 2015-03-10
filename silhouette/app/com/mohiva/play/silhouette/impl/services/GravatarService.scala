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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.impl.services

import java.security.MessageDigest

import com.mohiva.play.silhouette.api.Logger
import com.mohiva.play.silhouette.api.services.AvatarService
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.services.GravatarService._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

/**
 * Retrieves avatar URLs from the Gravatar service.
 *
 * @param httpLayer The HTTP layer implementation.
 */
class GravatarService(httpLayer: HTTPLayer) extends AvatarService with Logger {

  /**
   * Retrieves the URL for the given email address.
   *
   * @param email The email address for the avatar.
   * @return Maybe an avatar URL or None if no avatar could be found for the given email address.
   */
  def retrieveURL(email: String): Future[Option[String]] = {
    hash(email) match {
      case Some(hash) =>
        val url = URL.format(hash)
        httpLayer.url(url).get().map(_.status match {
          case 200 => Some(url)
          case code =>
            logger.info("[Silhouette] Gravatar API returns status code: " + code)
            None
        }).recover {
          case e =>
            logger.info("[Silhouette] Error invoking gravatar", e)
            None
        }
      case None => Future.successful(None)
    }
  }

  /**
   * Builds the hash for the given email address.
   *
   * @param email The email address to build the hash for.
   * @return Maybe a hash for the given email address or None if the email address is empty.
   */
  private def hash(email: String): Option[String] = {
    val s = email.trim.toLowerCase
    if (s.length > 0) {
      Some(MessageDigest.getInstance(MD5).digest(s.getBytes).map("%02x".format(_)).mkString)
    } else {
      None
    }
  }
}

/**
 * The companion object.
 */
object GravatarService {
  val URL = "http://www.gravatar.com/avatar/%s?d=404"
  val MD5 = "MD5"
}
