
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
