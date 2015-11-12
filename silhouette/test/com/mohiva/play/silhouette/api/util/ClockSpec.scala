/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.api.util

import org.joda.time.DateTime
import play.api.test._

/**
 * Test case for the [[com.mohiva.play.silhouette.api.util.Clock]] class.
 */
class ClockSpec extends PlaySpecification {

  "The `apply` method" should {
    "return a new Clock instance" in {
      Clock() should beAnInstanceOf[Clock]
    }
  }

  "The `now` method" should {
    "return a new DateTime instance" in {
      Clock().now should beAnInstanceOf[DateTime]
    }
  }
}
