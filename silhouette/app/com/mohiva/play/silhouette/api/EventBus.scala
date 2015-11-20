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

import akka.event.{ SubchannelClassification, ActorEventBus, LookupClassification }
import akka.util.Subclassification
import play.api.i18n.Messages
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
 * @param messages The messages for the current language.
 * @tparam I The type of the identity.
 */
case class SignUpEvent[I <: Identity](identity: I, request: RequestHeader, messages: Messages) extends SilhouetteEvent

/**
 * An event which will be published after an identity logged in.
 *
 * @param identity The logged in identity.
 * @param request The request header for the associated request.
 * @param messages The messages for the current language.
 * @tparam I The type of the identity.
 */
case class LoginEvent[I <: Identity](identity: I, request: RequestHeader, messages: Messages) extends SilhouetteEvent

/**
 * An event which will be published after an identity logged out.
 *
 * @param identity The logged out identity.
 * @param request The request header for the associated request.
 * @param messages The messages for the current language.
 * @tparam I The type of the identity.
 */
case class LogoutEvent[I <: Identity](identity: I, request: RequestHeader, messages: Messages) extends SilhouetteEvent

/**
 * An event which will be published if a request passes authentication.
 *
 * @param identity The logged in identity.
 * @param request The request header for the associated request.
 * @param messages The messages for the current language.
 * @tparam I The type of the identity.
 */
case class AuthenticatedEvent[I <: Identity](identity: I, request: RequestHeader, messages: Messages) extends SilhouetteEvent

/**
 * An event which will be published if a request did not pass authentication.
 *
 * @param request The request header for the associated request.
 * @param messages The messages for the current language.
 */
case class NotAuthenticatedEvent(request: RequestHeader, messages: Messages) extends SilhouetteEvent

/**
 * An event which will be published if a request did not pass authorization.
 *
 * @param identity The logged in identity.
 * @param request The request header for the associated request.
 * @param messages The messages for the current language.
 * @tparam I The type of the identity.
 */
case class NotAuthorizedEvent[I <: Identity](identity: I, request: RequestHeader, messages: Messages) extends SilhouetteEvent

/**
 * An event bus implementation which uses a class based lookup classification.
 */
class EventBus extends ActorEventBus with SubchannelClassification {
  override type Classifier = Class[_ <: SilhouetteEvent]
  override type Event = SilhouetteEvent

  /**
   * The logic to form sub-class hierarchy
   */
  override protected implicit val subclassification = new Subclassification[Classifier] {
    def isEqual(x: Classifier, y: Classifier): Boolean = x == y
    def isSubclass(x: Classifier, y: Classifier): Boolean = y.isAssignableFrom(x)
  }

  /**
   * Publishes the given Event to the given Subscriber.
   *
   * @param event The Event to publish.
   * @param subscriber The Subscriber to which the Event should be published.
   */
  override protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event

  /**
   * Returns the Classifier associated with the given Event.
   *
   * @param event The event for which the Classifier should be returned.
   * @return The Classifier for the given Event.
   */
  override protected def classify(event: Event): Classifier = event.getClass
}

/**
 * A global event bus instance.
 */
object EventBus {

  /**
   * Holds the global event bus instance.
   */
  private lazy val instance = new EventBus

  /**
   * Gets a global event bus instance.
   *
   * @return A global event bus instance.
   */
  def apply() = instance
}
