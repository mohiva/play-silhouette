/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.api

import play.api.i18n.Lang
import play.api.mvc.RequestHeader

/**
 * The base event.
 */
trait SilhouetteEvent

/**
 * An event which will be published after an identity signed up.
 *
 * @param identity The newly created identity.
 * @param request The request header for the associated request.
 * @param lang The lang associated for the current request.
 * @tparam I The type of the identity.
 */
case class SignUpEvent[I <: Identity](identity: I, request: RequestHeader, lang: Lang) extends SilhouetteEvent

/**
 * An event which will be published after an identity logged in.
 *
 * @param identity The logged in identity.
 * @param request The request header for the associated request.
 * @param lang The lang associated for the current request.
 * @tparam I The type of the identity.
 */
case class LoginEvent[I <: Identity](identity: I, request: RequestHeader, lang: Lang) extends SilhouetteEvent

/**
 * An event which will be published after an identity logged out.
 *
 * @param identity The logged out identity.
 * @param request The request header for the associated request.
 * @param lang The lang associated for the current request.
 * @tparam I The type of the identity.
 */
case class LogoutEvent[I <: Identity](identity: I, request: RequestHeader, lang: Lang) extends SilhouetteEvent

/**
 * An event which will be published if a request passes authentication.
 *
 * @param identity The logged in identity.
 * @param request The request header for the associated request.
 * @param lang The lang associated for the current request.
 * @tparam I The type of the identity.
 */
case class AuthenticatedEvent[I <: Identity](identity: I, request: RequestHeader, lang: Lang) extends SilhouetteEvent

/**
 * An event which will be published if a request did not pass authentication.
 *
 * @param request The request header for the associated request.
 * @param lang The lang associated for the current request.
 */
case class NotAuthenticatedEvent(request: RequestHeader, lang: Lang) extends SilhouetteEvent

/**
 * An event which will be published if a request did not pass authorization.
 *
 * @param identity The logged in identity.
 * @param request The request header for the associated request.
 * @param lang The lang associated for the current request.
 * @tparam I The type of the identity.
 */
case class NotAuthorizedEvent[I <: Identity](identity: I, request: RequestHeader, lang: Lang) extends SilhouetteEvent

/**
 * The EventBus api, used to publish authentication related events.
 */
trait EventBus {

  /**
   * Publishes the event.
   * @param event The event to publish
   */
  def publish(event: SilhouetteEvent): Unit
}
