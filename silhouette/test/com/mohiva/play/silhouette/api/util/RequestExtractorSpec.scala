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

import org.specs2.specification.Scope
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ AnyContentAsEmpty, Request }
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
      def extractString(name: String, parts: Option[Parts] = None)(implicit request: Request[JsValue]) = {
        request.body.\("test").\(name).asOpt[String]
      }
    }
  }

  "The `anyContent`" should {
    "extract a value from query string if all parts are allowed" in new Context {
      implicit val request = FakeRequest("GET", "?code=value")

      extract("code") must beSome("value")
    }

    "extract a value from query string if part is allowed" in new Context {
      implicit val request = FakeRequest("GET", "?code=value")

      extract("code", Some(Seq(RequestPart.QueryString))) must beSome("value")
    }

    "do not extract a value from query string if part isn't allowed" in new Context {
      implicit val request = FakeRequest("GET", "?code=value")

      extract("code", Some(Seq())) must beNone
    }

    "extract a value from headers if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withHeaders("code" -> "value")

      extract("code") must beSome("value")
    }

    "extract a value from headers if part is allowed" in new Context {
      implicit val request = FakeRequest().withHeaders("code" -> "value")

      extract("code", Some(Seq(RequestPart.Headers))) must beSome("value")
    }

    "do not extract a value from headers if part isn't allowed" in new Context {
      implicit val request = FakeRequest().withHeaders("code" -> "value")

      extract("code", Some(Seq())) must beNone
    }

    "extract a value from tags if all parts are allowed" in new Context {
      implicit val request = FakeRequest.apply(Map("code" -> "value"))

      extract("code") must beSome("value")
    }

    "extract a value from tags if part is allowed" in new Context {
      implicit val request = FakeRequest.apply(Map("code" -> "value"))

      extract("code", Some(Seq(RequestPart.Attributes))) must beSome("value")
    }

    "do not extract a value from tags if part isn't allowed" in new Context {
      implicit val request = FakeRequest.apply(Map("code" -> "value"))

      extract("code", Some(Seq())) must beNone
    }

    "return None if no value could be found in default parts" in new Context {
      implicit val request = FakeRequest.apply("GET", "?none=value", Map("none" -> "value"))
        .withHeaders("none" -> "value")

      extract("code") must beNone
    }

    "extract a value from URL encoded body if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withFormUrlEncodedBody(("code", "value"))

      extract("code") must beSome("value")
    }

    "extract a value from URL encoded body if part is allowed" in new Context {
      implicit val request = FakeRequest().withFormUrlEncodedBody(("code", "value"))

      extract("code", Some(Seq(RequestPart.FormUrlEncodedBody))) must beSome("value")
    }

    "do not extract a value from URL encoded body if part isn't allowed" in new Context {
      implicit val request = FakeRequest().withFormUrlEncodedBody(("code", "value"))

      extract("code", Some(Seq())) must beNone
    }

    "return None if no value could be found in default parts or URL encoded body" in new Context {
      implicit val request = FakeRequest.apply("GET", "?none=value", Map("none" -> "value"))
        .withHeaders("none" -> "value")
        .withFormUrlEncodedBody(("none", "value"))

      extract("code") must beNone
    }

    "extract a value from Json body if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withJsonBody(Json.obj("code" -> "value"))

      extract("code") must beSome("value")
    }

    "extract a value from Json body if part is allowed" in new Context {
      implicit val request = FakeRequest().withJsonBody(Json.obj("code" -> "value"))

      extract("code", Some(Seq(RequestPart.JsonBody))) must beSome("value")
    }

    "do not extract a value from Json body if part isn't allowed" in new Context {
      implicit val request = FakeRequest().withJsonBody(Json.obj("code" -> "value"))

      extract("code", Some(Seq())) must beNone
    }

    "return None if no value could be found in default parts or Json body" in new Context {
      implicit val request = FakeRequest.apply("GET", "?none=value", Map("none" -> "value"))
        .withHeaders("none" -> "value")
        .withJsonBody(Json.obj("none" -> "value"))

      extract("code") must beNone
    }

    "extract a value from XML body if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withXmlBody(<code>value</code>)

      extract("code") must beSome("value")
    }

    "extract a value from XML body if part is allowed" in new Context {
      implicit val request = FakeRequest().withXmlBody(<code>value</code>)

      extract("code", Some(Seq(RequestPart.XMLBody))) must beSome("value")
    }

    "do not extract a value from XML body if part isn't allowed" in new Context {
      implicit val request = FakeRequest().withXmlBody(<code>value</code>)

      extract("code", Some(Seq())) must beNone
    }

    "return None if no value could be found in default parts or XML body" in new Context {
      implicit val request = FakeRequest.apply("GET", "?none=value", Map("none" -> "value"))
        .withHeaders("none" -> "value")
        .withXmlBody(<none>value</none>)

      extract("code") must beNone
    }
  }

  "The `formUrlEncodedExtractor`" should {
    "extract a value from query string if all parts are allowed" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withBody(Map("none" -> Seq("value")))

      extract("code") must beSome("value")
    }

    "extract a value from headers if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withHeaders("code" -> "value").withBody(Map("none" -> Seq("value")))

      extract("code") must beSome("value")
    }

    "extract a value from tags if all parts are allowed" in new Context {
      implicit val request = FakeRequest.apply(Map("code" -> "value")).withBody(Map("none" -> Seq("value")))

      extract("code") must beSome("value")
    }

    "extract a value from body if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withBody(Map("code" -> Seq("value")))

      extract("code") must beSome("value")
    }

    "return None if no value could be found in default parts or body" in new Context {
      implicit val request = FakeRequest.apply("GET", "?none=value", Map("none" -> "value"))
        .withHeaders("none" -> "value")
        .withBody(Map("none" -> Seq("value")))

      extract("code") must beNone
    }
  }

  "The `jsonExtractor`" should {
    "extract a value from query string if all parts are allowed" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withBody(Json.obj("none" -> "value"))

      extract("code") must beSome("value")
    }

    "extract a value from headers if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withHeaders("code" -> "value").withBody(Json.obj("none" -> "value"))

      extract("code") must beSome("value")
    }

    "extract a value from tags if all parts are allowed" in new Context {
      implicit val request = FakeRequest.apply(Map("code" -> "value")).withBody(Json.obj("none" -> "value"))

      extract("code") must beSome("value")
    }

    "extract a value from body if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withBody(Json.obj("code" -> "value"))

      extract("code") must beSome("value")
    }

    "return None if no value could be found in default parts or body" in new Context {
      implicit val request = FakeRequest.apply("GET", "?none=value", Map("none" -> "value"))
        .withHeaders("none" -> "value")
        .withBody(Json.obj("none" -> "value"))

      extract("code") must beNone
    }
  }

  "The `xmlExtractor`" should {
    "extract a value from query string if all parts are allowed" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withBody(<none>value</none>)

      extract("code") must beSome("value")
    }

    "extract a value from headers if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withHeaders("code" -> "value").withBody(<none>value</none>)

      extract("code") must beSome("value")
    }

    "extract a value from tags if all parts are allowed" in new Context {
      implicit val request = FakeRequest.apply(Map("code" -> "value")).withBody(<none>value</none>)

      extract("code") must beSome("value")
    }

    "extract a value from body if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withBody(<code>value</code>)

      extract("code") must beSome("value")
    }

    "return None if no value could be found in default parts or body" in new Context {
      implicit val request = FakeRequest.apply("GET", "?none=value", Map("none" -> "value"))
        .withHeaders("none" -> "value")
        .withBody(<none>value</none>)

      extract("code") must beNone
    }
  }

  "The `anyExtractor`" should {
    "extract a value from query string if all parts are allowed" in new Context {
      implicit val request = FakeRequest("GET", "?code=value").withBody("text")

      extract("code") must beSome("value")
    }

    "extract a value from headers if all parts are allowed" in new Context {
      implicit val request = FakeRequest().withHeaders("code" -> "value").withBody("text")

      extract("code") must beSome("value")
    }

    "extract a value from tags if all parts are allowed" in new Context {
      implicit val request = FakeRequest.apply(Map("code" -> "value")).withBody("text")

      extract("code") must beSome("value")
    }
  }

  "An extractor" should {
    "be overridden" in new Context {
      import extractors._

      implicit val request = FakeRequest().withBody(Json.obj("test" -> Json.obj("code" -> "value")))

      extract("code") must beSome("value")
    }
  }

  "The `ExtractableRequest`" should {
    "be converted from explicit request" in new Context {
      def test[B](request: ExtractableRequest[B]) = request

      val request = FakeRequest().withBody(<none>value</none>)

      test(request) must beAnInstanceOf[ExtractableRequest[_]]
    }

    "be converted from implicit request" in new Context {
      def test[B](implicit request: ExtractableRequest[B]) = request

      implicit val request = FakeRequest().withBody(<none>value</none>)

      test must beAnInstanceOf[ExtractableRequest[_]]
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
     * @param parts The request parts from which a value can be extracted.
     * @param request The request from which the value should be extracted.
     * @tparam B The type of the request body.
     * @return The extracted value or None if the value couldn't be extracted.
     */
    def extract[B](name: String, parts: Option[Seq[RequestPart.Value]] = None)(
      implicit
      request: ExtractableRequest[B]): Option[String] = {

      request.extractString(name, parts)
    }

    /**
     * Provides some additional helper utilities to build FakeRequest values.
     *
     * @param r The request to adds the helpers to.
     */
    implicit class RichFakeRequest(r: FakeRequest.type) {
      def apply(method: String, path: String, tags: Map[String, String]): FakeRequest[AnyContentAsEmpty.type] = {
        FakeRequest(method, path, FakeHeaders(), AnyContentAsEmpty, tags = tags)
      }

      def apply(tags: Map[String, String]): FakeRequest[AnyContentAsEmpty.type] = {
        FakeRequest("GET", "/", FakeHeaders(), AnyContentAsEmpty, tags = tags)
      }
    }
  }
}
