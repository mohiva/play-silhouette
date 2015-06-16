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

import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import org.joda.time.DateTime
import play.api.test.PlaySpecification

import scala.concurrent.duration._

/**
 * Test case for the [[com.mohiva.play.silhouette.api.Authenticator]] class.
 */
class AuthenticatorSpec extends PlaySpecification {

  "The + method of the RichDateTime class" should {
    "add a second to a DateTime instance" in {
      new DateTime(2015, 6, 16, 19, 46, 0) + 1.second must be equalTo new DateTime(2015, 6, 16, 19, 46, 1)
    }

    "add a minute to a DateTime instance" in {
      new DateTime(2015, 6, 16, 19, 46, 0) + 1.minute must be equalTo new DateTime(2015, 6, 16, 19, 47, 0)
    }

    "add an hour to a DateTime instance" in {
      new DateTime(2015, 6, 16, 19, 46, 0) + 1.hour must be equalTo new DateTime(2015, 6, 16, 20, 46, 0)
    }

    "subtract a second from a DateTime instance" in {
      new DateTime(2015, 6, 16, 19, 46, 0) - 1.second must be equalTo new DateTime(2015, 6, 16, 19, 45, 59)
    }

    "subtract a minute from a DateTime instance" in {
      new DateTime(2015, 6, 16, 19, 46, 0) - 1.minute must be equalTo new DateTime(2015, 6, 16, 19, 45, 0)
    }

    "subtract an hour from a DateTime instance" in {
      new DateTime(2015, 6, 16, 19, 46, 0) - 1.hour must be equalTo new DateTime(2015, 6, 16, 18, 46, 0)
    }
  }
}
