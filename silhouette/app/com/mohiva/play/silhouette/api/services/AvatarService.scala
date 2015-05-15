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
package com.mohiva.play.silhouette.api.services

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

/**
 * Service to retrieve avatar URLs from an avatar service such as Gravatar.
 */
trait AvatarService {

  /**
   * Retrieves the URL for an identifier.
   *
   * @param id The identifier for the avatar.
   * @return Maybe an avatar URL or None if no URL could be found for the given identifier.
   */
  def retrieveURL(id: String)(implicit ec: ExecutionContext): Future[Option[String]]
}
