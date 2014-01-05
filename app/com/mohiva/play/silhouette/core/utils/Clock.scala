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
package com.mohiva.play.silhouette.core.utils

import org.joda.time.DateTime

/**
 * A trait which provides a mockable implementation for a DateTime instance.
 */
trait Clock {

  /**
   * Gets the current DateTime.
   *
   * @return the current DateTime.
   */
  def now: DateTime
}

/**
 * Creates a clock implementation.
 */
object Clock {

  /**
   * Gets a Clock implementation.
   *
   * @return A Clock implementation.
   */
  def apply() = new Clock {
    def now = DateTime.now
  }
}
