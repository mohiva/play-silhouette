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
package com.mohiva.play.silhouette.impl.util

import akka.event.{ ActorEventBus, SubchannelClassification }
import akka.util.Subclassification
import com.mohiva.play.silhouette.api.{ EventBus, SilhouetteEvent }

/**
 * An event bus implementation using Akka which uses a class based lookup classification.
 */
class AkkaEventBus extends ActorEventBus with EventBus with SubchannelClassification {
  type Classifier = Class[_ <: SilhouetteEvent]
  type Event = SilhouetteEvent

  /**
   * The logic to form sub-class hierarchy
   */
  protected implicit val subclassification = new Subclassification[Classifier] {
    def isEqual(x: Classifier, y: Classifier): Boolean = x == y
    def isSubclass(x: Classifier, y: Classifier): Boolean = y.isAssignableFrom(x)
  }

  /**
   * Publishes the given Event to the given Subscriber.
   *
   * @param event The Event to publish.
   * @param subscriber The Subscriber to which the Event should be published.
   */
  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event

  /**
   * Returns the Classifier associated with the given Event.
   *
   * @param event The event for which the Classifier should be returned.
   * @return The Classifier for the given Event..
   */
  protected def classify(event: Event): Classifier = event.getClass
}

/**
 * A global event bus instance.
 */
object AkkaEventBus {

  /**
   * Holds the global event bus instance.
   */
  private lazy val instance = new AkkaEventBus

  /**
   * Gets a global event bus instance.
   *
   * @return A global event bus instance.
   */
  def apply() = instance
}
