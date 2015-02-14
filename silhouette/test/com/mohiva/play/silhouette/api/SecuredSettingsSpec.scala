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

import play.api.GlobalSettings
import play.api.i18n.Lang
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }

/**
 * Test case for the [[com.mohiva.play.silhouette.api.SecuredSettings]] trait.
 */
class SecuredSettingsSpec extends PlaySpecification {

  "The `SecuredSettings` implementation" should {
    "return None as default value for onNotAuthenticated method " in new WithApplication() {
      val settings = new SecuredSettings with GlobalSettings {}
      settings.onNotAuthenticated(FakeRequest(GET, "/"), Lang("en-US")) should beNone
    }

    "return None as default value for onNotAuthenticated method " in new WithApplication() {
      val settings = new SecuredSettings with GlobalSettings {}
      settings.onNotAuthorized(FakeRequest(GET, "/"), Lang("en-US")) should beNone
    }
  }
}
