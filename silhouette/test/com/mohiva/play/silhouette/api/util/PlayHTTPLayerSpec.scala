package com.mohiva.play.silhouette.api.util

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSRequest
import play.api.test._

/**
 * Test case for the [[com.mohiva.play.silhouette.api.util.PlayHTTPLayer]] class.
 */
class PlayHTTPLayerSpec extends PlaySpecification {

  "The `url` method" should {
    "return a new WS.WSRequest instance" in new WithApplication {
      val url = "http://silhouette.mohiva.com"
      val httpLayer = new PlayHTTPLayer
      val requestHolder = httpLayer.url(url)

      requestHolder should beAnInstanceOf[WSRequest]
      requestHolder.url must be equalTo url
    }
  }
}
