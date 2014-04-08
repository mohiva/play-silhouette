package com.mohiva.play.silhouette.core.utils

import play.api.test._
import play.api.libs.ws.WS

/**
 * Test case for the [[com.mohiva.play.silhouette.core.utils.PlayHTTPLayer]] class.
 */
class PlayHTTPLayerSpec extends PlaySpecification {

  "The url method" should {
    "return a new WS.WSRequestHolder instance" in {
      val url = "http://silhouette.mohiva.com"
      val httpLayer = new PlayHTTPLayer
      val requestHolder = httpLayer.url(url)

      requestHolder should beAnInstanceOf[WS.WSRequestHolder]
      requestHolder.url must be equalTo url
    }
  }
}
