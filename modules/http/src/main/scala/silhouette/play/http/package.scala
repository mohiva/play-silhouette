/**
 * Licensed to the Minutemen Group under one or more contributor license
 * agreements. See the COPYRIGHT file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silhouette.play

import play.api.mvc.Cookie.{ SameSite => PlaySameSite }
import play.api.mvc.{ Cookie => PlayCookie }
import silhouette.http.Cookie.{ SameSite => SilhouetteSameSite }
import silhouette.http.{ Cookie => SilhouetteCookie }

import scala.language.implicitConversions

/**
 * HTTP related interfaces and implementations.
 */
package object http {

  /**
   * Converts the [[play.api.mvc.Cookie.SameSite]] type to the [[silhouette.http.Cookie.SameSite]] type.
   *
   * @param sameSite The instance to convert.
   * @return The converted instance.
   */
  implicit def playSameSiteToSilhouetteSameSite(sameSite: PlaySameSite): SilhouetteSameSite = {
    sameSite match {
      case PlaySameSite.Lax    => SilhouetteSameSite.Lax
      case PlaySameSite.Strict => SilhouetteSameSite.Strict
    }
  }

  /**
   * Converts the [[silhouette.http.Cookie.SameSite]] type to [[play.api.mvc.Cookie.SameSite]] type.
   *
   * @param sameSite The instance to convert.
   * @return The converted instance.
   */
  implicit def silhouetteSameSiteToPlaySameSite(sameSite: SilhouetteSameSite): PlaySameSite = {
    sameSite match {
      case SilhouetteSameSite.Lax    => PlaySameSite.Lax
      case SilhouetteSameSite.Strict => PlaySameSite.Strict
    }
  }

  /**
   * Converts the [[play.api.mvc.Cookie]] type to the [[silhouette.http.Cookie]] type.
   *
   * @param cookie The instance to convert.
   * @return The converted instance.
   */
  implicit def playCookieToSilhouetteCookie(cookie: PlayCookie): SilhouetteCookie = {
    SilhouetteCookie(
      name = cookie.name,
      value = cookie.value,
      maxAge = cookie.maxAge,
      domain = cookie.domain,
      path = if (cookie.path.isEmpty) None else Some(cookie.path),
      secure = cookie.secure,
      httpOnly = cookie.httpOnly,
      sameSite = cookie.sameSite.map(playSameSiteToSilhouetteSameSite)
    )
  }

  /**
   * Converts the [[silhouette.http.Cookie]] type to the [[play.api.mvc.Cookie]] type.
   *
   * @param cookie The instance to convert.
   * @return The converted instance.
   */
  implicit def silhouetteCookieToPlayCookie(cookie: SilhouetteCookie): PlayCookie = {
    PlayCookie(
      name = cookie.name,
      value = cookie.value,
      maxAge = cookie.maxAge,
      domain = cookie.domain,
      path = cookie.path.getOrElse(""),
      secure = cookie.secure,
      httpOnly = cookie.httpOnly,
      sameSite = cookie.sameSite.map(silhouetteSameSiteToPlaySameSite)
    )
  }
}
