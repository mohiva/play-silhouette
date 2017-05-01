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

import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.util.SecureRandomIDGenerator]] class.
 */
class SecureRandomIDGeneratorSpec extends PlaySpecification {

  "The generator" should {
    "return a 128 byte length secure random number" in {
      val generator = new SecureRandomIDGenerator()
      val id = await(generator.generate)

      id must have size (128 * 2)
      id must beMatching("[a-f0-9]+")
    }

    "return a 265 byte length secure random number" in {
      val generator = new SecureRandomIDGenerator(256)
      val id = await(generator.generate)

      id must have size (256 * 2)
      id must beMatching("[a-f0-9]+")
    }
  }
}
