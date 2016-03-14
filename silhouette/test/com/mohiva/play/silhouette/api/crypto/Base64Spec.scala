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
package com.mohiva.play.silhouette.api.crypto

import play.api.libs.json.Json
import play.api.test._

/**
 * Test case for the [[Base64]] object.
 */
class Base64Spec extends PlaySpecification {

  "The `decode` method" should {
    "decode a Base64 string" in {
      Base64.decode("SGVsbG8gV29ybGQh") must be equalTo "Hello World!"
    }
  }

  "The `encode` method" should {
    "encode a string as Base64" in {
      Base64.encode("Hello World!") must be equalTo "SGVsbG8gV29ybGQh"
    }

    "encode Json as Base64" in {
      Base64.encode(Json.obj("word" -> "Hello World!")) must be equalTo "eyJ3b3JkIjoiSGVsbG8gV29ybGQhIn0="
    }
  }
}
