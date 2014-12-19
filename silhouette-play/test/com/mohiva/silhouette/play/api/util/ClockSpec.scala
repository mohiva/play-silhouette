package com.mohiva.play.silhouette.api.util

import com.mohiva.silhouette.util.Clock
import org.joda.time.DateTime
import play.api.test._

/**
 * Test case for the [[Clock]] class.
 */
class ClockSpec extends PlaySpecification {

  "The `apply` method" should {
    "return a new Clock instance" in {
      Clock() should beAnInstanceOf[Clock]
    }
  }

  "The `now` method" should {
    "return a new DateTime instance" in {
      Clock().now should beAnInstanceOf[DateTime]
    }
  }
}
