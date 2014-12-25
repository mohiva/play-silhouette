package com.mohiva.play.silhouette.api.util

import org.specs2.specification.Scope
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Request
import play.api.test._

/**
 * Test case for the [[com.mohiva.play.silhouette.api.util.RequestExtractor]] object.
 */
class RequestExtractorSpec extends PlaySpecification {

  /**
   * Some custom extractors to test if an extractor could be overridden.
   */
  object extractors {

    /**
     * Tries to extract the value from Json body.
     */
    implicit val jsonExtractor = new RequestExtractor[JsValue] {
      def extractString(name: String)(implicit request: Request[JsValue]) = {
        request.body.\("test").\(name).asOpt[String]
      }
    }
  }

  "The `anyContentAsEmptyExtractor`" should {
    "extract a value from query string" in new Context {
      implicit val request = FakeRequest("GET", "?code=value")

      extract("code") must beSome("value")
    }

    "return None if no value could be found in query string" in new Context {
      implicit val request = FakeRequest()

      extract("code") must beNone
    }
  }

  "The `anyContentAsFormUrlEncodedExtractor`" should {
    "extract a value from query string" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withFormUrlEncodedBody(("none", "value"))

      extract("code") must beSome("value")
    }

    "extract a value from body" in new Context {
      implicit val request = FakeRequest().withFormUrlEncodedBody(("code", "value"))

      extract("code") must beSome("value")
    }

    "return None if no value could be found in query string or body" in new Context {
      implicit val request = FakeRequest().withFormUrlEncodedBody(("none", "value"))

      extract("code") must beNone
    }
  }

  "The `anyContentAsJsonExtractor`" should {
    "extract a value from query string" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withJsonBody(Json.obj("none" -> "value"))

      extract("code") must beSome("value")
    }

    "extract a value from body" in new Context {
      implicit val request = FakeRequest().withJsonBody(Json.obj("code" -> "value"))

      extract("code") must beSome("value")
    }

    "return None if no value could be found in query string or body" in new Context {
      implicit val request = FakeRequest().withJsonBody(Json.obj("none" -> "value"))

      extract("code") must beNone
    }
  }

  "The `anyContentAsXmlExtractor`" should {
    "extract a value from query string" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withXmlBody(<none>value</none>)

      extract("code") must beSome("value")
    }

    "extract a value from body" in new Context {
      implicit val request = FakeRequest().withXmlBody(<code>value</code>)

      extract("code") must beSome("value")
    }

    "return None if no value could be found in query string or body" in new Context {
      implicit val request = FakeRequest().withXmlBody(<none>value</none>)

      extract("code") must beNone
    }
  }

  "The `formUrlEncodedExtractor`" should {
    "extract a value from query string" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withBody(Map("none" -> Seq("value")))

      extract("code") must beSome("value")
    }

    "extract a value from body" in new Context {
      implicit val request = FakeRequest().withBody(Map("code" -> Seq("value")))

      extract("code") must beSome("value")
    }

    "return None if no value could be found in query string or body" in new Context {
      implicit val request = FakeRequest().withBody(Map("none" -> Seq("value")))

      extract("code") must beNone
    }
  }

  "The `jsonExtractor`" should {
    "extract a value from query string" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withBody(Json.obj("none" -> "value"))

      extract("code") must beSome("value")
    }

    "extract a value from body" in new Context {
      implicit val request = FakeRequest().withBody(Json.obj("code" -> "value"))

      extract("code") must beSome("value")
    }

    "return None if no value could be found in query string or body" in new Context {
      implicit val request = FakeRequest().withBody(Json.obj("none" -> "value"))

      extract("code") must beNone
    }
  }

  "The `xmlExtractor`" should {
    "extract a value from query string" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withBody(<none>value</none>)

      extract("code") must beSome("value")
    }

    "extract a value from body" in new Context {
      implicit val request = FakeRequest().withBody(<code>value</code>)

      extract("code") must beSome("value")
    }

    "return None if no value could be found in query string or body" in new Context {
      implicit val request = FakeRequest().withBody(<none>value</none>)

      extract("code") must beNone
    }
  }

  "An extractor" should {
    "be overridden" in new Context {
      import extractors._

      implicit val request = FakeRequest().withBody(Json.obj("test" -> Json.obj("code" -> "value")))

      extract("code") must beSome("value")
    }
  }

  /**
   * The context.
   */
  class Context extends Scope {

    /**
     * Extracts a value from request.
     *
     * @param name The name of the value to extract.
     * @param request The request from which the value should be extracted.
     * @tparam B The type of the request body.
     * @return The extracted value or None if the value couldn't be extracted.
     */
    def extract[B](name: String)(implicit request: ExtractableRequest[B]): Option[String] = {
      request.extractString(name)
    }
  }
}
