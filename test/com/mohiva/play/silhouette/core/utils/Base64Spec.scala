package com.mohiva.play.silhouette.core.utils

import play.api.libs.json.Json
import play.api.test._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.utils.Base64]] object.
 */
class Base64Spec extends PlaySpecification {

  "The `decode` method" should {
    "decode a Base64 string" in {
      Base64.decode("SGVsbG8gV29ybGQh") must be equalTo "Hello World!"
    }
  }

  "The `encode` method" should {
    "encode a string as Base64" in {
      Base64.encode("Hello World!") must be equalTo "SGVsbG8gV29ybGQh"
    }

    "encode Json as Base64" in {
      Base64.encode(Json.obj("word" -> "Hello World!")) must be equalTo "eyJ3b3JkIjoiSGVsbG8gV29ybGQhIn0="
    }
  }
}
