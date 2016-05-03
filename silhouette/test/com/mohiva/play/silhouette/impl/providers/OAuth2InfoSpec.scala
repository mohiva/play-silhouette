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

package com.mohiva.play.silhouette.impl.providers

import org.joda.time.Instant
import play.api.test.PlaySpecification

class OAuth2InfoSpec extends PlaySpecification {
  "The `expired` method" should {
    val baseInstant = new Instant(0)
    val base = OAuth2Info("", None, Some(10), None, None, baseInstant)
    "return false before it expired" in {
      base.expired(new Instant(5000)) should be equalTo false
    }
    "return true after it has expired" in {
      base.expired(new Instant(15000)) should be equalTo true
    }
  }

}
