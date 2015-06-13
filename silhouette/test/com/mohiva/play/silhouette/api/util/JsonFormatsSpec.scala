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
package com.mohiva.play.silhouette.api.util

import com.mohiva.play.silhouette.api.util.JsonFormats._
import org.specs2.matcher.JsonMatchers
import play.api.libs.json.Json
import play.api.test._

import scala.concurrent.duration._

/**
 * Test case for the [[com.mohiva.play.silhouette.api.util.JsonFormats]] class.
 */
class JsonFormatsSpec extends PlaySpecification with JsonMatchers {

  "A implicit `FiniteDurationFormat` object" should {
    "convert a FiniteDuration in seconds to Json" in {
      val json = Json.obj("value" -> 10.seconds)

      json.toString() must /("value" -> 10)
    }

    "convert a FiniteDuration in minutes to Json" in {
      val json = Json.obj("value" -> 10.minutes)

      json.toString() must /("value" -> (10 * 60))
    }

    "convert Json into a FiniteDuration in seconds" in {
      val json = Json.obj("value" -> 10.seconds)

      (json \ "value").as[FiniteDuration] must be equalTo 10.seconds
    }

    "convert Json into a FiniteDuration in minutes" in {
      val json = Json.obj("value" -> 10.minutes)

      (json \ "value").as[FiniteDuration] must be equalTo 10.minutes
    }
  }
}
