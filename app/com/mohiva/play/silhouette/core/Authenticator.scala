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
package com.mohiva.play.silhouette.core

import org.joda.time.DateTime
import play.api.Play.current
import play.api.Play
import play.api.mvc.{DiscardingCookie, Cookie}
import Authenticator._

/**
 * An authenticator tracks an authenticated user.
 *
 * @param id The authenticator ID.
 * @param identityID The user ID.
 * @param creationDate The creation timestamp.
 * @param lastUsedDate The last used timestamp.
 * @param expirationDate The expiration time.
 */
case class Authenticator(
  id: String,
  identityID: IdentityId,
  creationDate: DateTime,
  lastUsedDate: DateTime,
  expirationDate: DateTime) {

  /**
   * Creates a cookie representing this authenticator.
   *
   * @return A cookie instance.
   */
  def toCookie = Cookie(
    name = cookieName,
    value = id,
    maxAge = if (makeTransient) Transient else Some(absoluteTimeoutInSeconds),
    path = cookiePath,
    domain = cookieDomain,
    secure = cookieSecure,
    httpOnly = cookieHttpOnly
  )

  /**
   * Checks if the authenticator has expired. This is an absolute timeout since the creation of
   * the authenticator.
   *
   * @return True if the authenticator has expired, false otherwise.
   */
  def expired = expirationDate.isBeforeNow

  /**
   * Checks if the time elapsed since the last time the authenticator was used is longer than
   * the maximum idle timeout specified in the properties.
   *
   * @return True if the authenticator timed out, false otherwise.
   */
  def timedOut = lastUsedDate.plusMinutes(Authenticator.idleTimeout).isBeforeNow

  /**
   * Checks if the authenticator isn't expired and isn't timed out.
   *
   * @return True if the authenticator isn't expired and isn't timed out.
   */
  def isValid = !expired && !timedOut

  /**
   * Updates the last used timestamp (note that this does not save it in the store).
   *
   * @return A new authenticator instance with the new timestamp.
   */
  def touch = this.copy(lastUsedDate = DateTime.now())
}

/**
 * The companion object.
 */
object Authenticator {
  // property keys
  val CookieNameKey = "silhouette.cookie.name"
  val CookiePathKey = "silhouette.cookie.path"
  val CookieDomainKey = "silhouette.cookie.domain"
  val CookieHttpOnlyKey = "silhouette.cookie.httpOnly"
  val ApplicationContext = "application.context"
  val IdleTimeoutKey = "silhouette.cookie.idleTimeoutInMinutes"
  val AbsoluteTimeoutKey = "silhouette.cookie.absoluteTimeoutInMinutes"
  val TransientKey = "silhouette.cookie.makeTransient"

  // default values
  val DefaultCookieName = "id"
  val DefaultCookiePath = "/"
  val DefaultCookieHttpOnly = true
  val Transient = None
  val DefaultIdleTimeout = 30
  val DefaultAbsoluteTimeout = 12 * 60

  lazy val cookieName = Play.application.configuration.getString(CookieNameKey).getOrElse(DefaultCookieName)
  lazy val cookiePath = Play.application.configuration.getString(CookiePathKey).getOrElse(
    Play.configuration.getString(ApplicationContext).getOrElse(DefaultCookiePath)
  )
  lazy val cookieDomain = Play.application.configuration.getString(CookieDomainKey)
  lazy val cookieSecure = IdentityProvider.sslEnabled
  lazy val cookieHttpOnly = Play.application.configuration.getBoolean(CookieHttpOnlyKey).getOrElse(DefaultCookieHttpOnly)
  lazy val idleTimeout = Play.application.configuration.getInt(IdleTimeoutKey).getOrElse(DefaultIdleTimeout)
  lazy val absoluteTimeout = Play.application.configuration.getInt(AbsoluteTimeoutKey).getOrElse(DefaultAbsoluteTimeout)
  lazy val absoluteTimeoutInSeconds = absoluteTimeout * 60
  lazy val makeTransient = Play.application.configuration.getBoolean(TransientKey).getOrElse(true)

  val discardingCookie = DiscardingCookie(cookieName, cookiePath, cookieDomain, cookieSecure)
}
