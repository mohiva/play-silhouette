/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.impl.util

import akka.util.Crypt
import com.mohiva.play.silhouette.api.util.FingerprintGenerator
import play.api.http.HeaderNames._
import play.api.mvc.RequestHeader

/**
 * A generator which creates a SHA1 fingerprint from `User-Agent` and `Accept` headers and if defined the
 * remote address of the user.
 *
 * @param includeRemoteAddress Indicates if the remote address should be included into the fingerprint.
 */
class DefaultFingerprintGenerator(includeRemoteAddress: Boolean = false) extends FingerprintGenerator {

  /**
   * Generates a fingerprint from request.
   *
   * @param request The request header.
   * @return The generated fingerprint.
   */
  def generate(implicit request: RequestHeader) = {
    Crypt.sha1(new StringBuilder()
      .append(request.headers.get(USER_AGENT).getOrElse("")).append(":")
      .append(request.headers.get(ACCEPT).getOrElse("")).append(":")
      .append(if (includeRemoteAddress) request.remoteAddress else "")
      .toString()
    )
  }
}
