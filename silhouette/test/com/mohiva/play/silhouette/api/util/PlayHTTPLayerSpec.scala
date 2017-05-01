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

import play.api.libs.ws.{ WSClient, WSRequest }
import play.api.test._

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[com.mohiva.play.silhouette.api.util.PlayHTTPLayer]] class.
 */
class PlayHTTPLayerSpec extends PlaySpecification {

  "The `url` method" should {
    "return a new WS.WSRequest instance" in new WithApplication {
      val url = "http://silhouette.mohiva.com"
      val client = app.injector.instanceOf[WSClient]
      val httpLayer = new PlayHTTPLayer(client)
      val requestHolder = httpLayer.url(url)

      requestHolder should beAnInstanceOf[WSRequest]
      requestHolder.url must be equalTo url
    }
  }
}
