package com.mohiva.play.silhouette.core.utils

import play.api.test._
import org.joda.time.DateTime

/**
 * Test case for the [[com.mohiva.play.silhouette.core.utils.Clock]] class.
 */
class ClockSpec extends PlaySpecification {

  "The apply method" should {
    "return a new Clock instance" in {
      Clock() should beAnInstanceOf[Clock]
    }
  }

  "The now method" should {
    "return a new DateTime instance" in {
      Clock().now should beAnInstanceOf[DateTime]
    }
  }
}
