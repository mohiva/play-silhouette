/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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

import play.api.GlobalSettings
import play.api.mvc.{RequestHeader, SimpleResult}
import scala.concurrent.Future
import play.api.i18n.Lang

/**
 * Can be mixed into the GlobalSettings object to define a global behaviour
 * for not-authenticated and not-authorized actions.
 */
trait SecuredSettings {
  this: GlobalSettings =>

  /**
   * Called when a user isn't authenticated.
   *
   * @param request The request header.
   * @param lang The current selected lang.
   * @return The result to send to the client.
   */
  def onNotAuthenticated(request: RequestHeader, lang: Lang): Option[Future[SimpleResult]] = None

  /**
   * Called when a user isn't authorized.
   *
   * @param request The request header.
   * @param lang The current selected lang.
   * @return The result to send to the client.
   */
  def onNotAuthorized(request: RequestHeader, lang: Lang): Option[Future[SimpleResult]] = None
}
