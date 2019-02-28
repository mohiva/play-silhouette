/**
 * Licensed to the Minutemen Group under one or more contributor license
 * agreements. See the COPYRIGHT file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silhouette.play.http

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.mvc.request.RequestTarget
import play.api.mvc.{ Cookie => PlayCookie, _ }
import play.api.test.FakeRequest
import silhouette.crypto.Hash
import silhouette.crypto.Hash._
import silhouette.http._

/**
 * Test case for the [[PlayRequestPipeline]] class.
 */
class PlayRequestPipelineSpec extends Specification {

  "The `headers` method" should {
    "return all headers" in new Context {
      requestPipeline.headers must be equalTo Seq(
        Header("TEST1", "value1", "value2"),
        Header("TEST2", "value1")
      )
    }
  }

  "The `header` method" should {
    "return the list of header values" in new Context {
      requestPipeline.header("TEST1") must beSome(Header("TEST1", "value1", "value2"))
    }

    "return an empty list if no header with the given name was found" in new Context {
      requestPipeline.header("TEST3") must beNone
    }
  }

  "The `withHeaders` method" should {
    "append a new header" in new Context {
      requestPipeline.withHeaders(Header("TEST3", "value1")).headers must be equalTo Seq(
        Header("TEST1", "value1", "value2"),
        Header("TEST2", "value1"),
        Header("TEST3", "value1")
      )
    }

    "append multiple headers" in new Context {
      requestPipeline.withHeaders(Header("TEST3", "value1"), Header("TEST4", "value1")).headers must be equalTo Seq(
        Header("TEST1", "value1", "value2"),
        Header("TEST2", "value1"),
        Header("TEST3", "value1"),
        Header("TEST4", "value1")
      )
    }

    "append multiple headers with the same name" in new Context {
      requestPipeline.withHeaders(Header("TEST3", "value1"), Header("TEST3", "value2", "value3")).headers must
        be equalTo Seq(
          Header("TEST1", "value1", "value2"),
          Header("TEST2", "value1"),
          Header("TEST3", "value1", "value2", "value3")
        )
    }

    "override an existing header" in new Context {
      requestPipeline.withHeaders(Header("TEST2", "value2"), Header("TEST2", "value3")).headers must be equalTo Seq(
        Header("TEST1", "value1", "value2"),
        Header("TEST2", "value2", "value3")
      )
    }

    "override multiple existing headers" in new Context {
      requestPipeline.withHeaders(Header("TEST1", "value3"), Header("TEST2", "value2")).headers must be equalTo Seq(
        Header("TEST1", "value3"),
        Header("TEST2", "value2")
      )
    }
  }

  "The `cookies` method" should {
    "return all cookies" in new Context {
      requestPipeline.cookies must be equalTo Seq(
        PlayCookie("test1", "value1"),
        PlayCookie("test2", "value2")
      )
    }
  }

  "The `cookie` method" should {
    "return some cookie for the given name" in new Context {
      requestPipeline.cookie("test1") must beSome(playCookieToSilhouetteCookie(PlayCookie("test1", "value1")))
    }

    "return None if no cookie with the given name was found" in new Context {
      requestPipeline.cookie("test3") must beNone
    }
  }

  "The `withCookies` method" should {
    "append a new cookie" in new Context {
      requestPipeline.withCookies(PlayCookie("test3", "value3")).cookies must be equalTo Seq(
        PlayCookie("test1", "value1"),
        PlayCookie("test2", "value2"),
        PlayCookie("test3", "value3")
      )
    }

    "override an existing cookie" in new Context {
      requestPipeline.withCookies(PlayCookie("test1", "value3")).cookies must be equalTo Seq(
        PlayCookie("test1", "value3"),
        PlayCookie("test2", "value2")
      )
    }

    "use the last cookie if multiple cookies with the same name are given" in new Context {
      requestPipeline.withCookies(
        PlayCookie("test1", "value3"),
        PlayCookie("test1", "value4")
      ).cookies must be equalTo Seq(
          PlayCookie("test1", "value4"),
          PlayCookie("test2", "value2")
        )
    }
  }

  "The `rawQueryString` method" should {
    "return the raw query string" in new Context {
      requestPipeline.rawQueryString must be equalTo "test1=value1&test1=value2&test2=value1"
    }

    "be URL encoded" in new Context {
      requestPipeline.withQueryParams("test=3" -> "value=4").rawQueryString must be equalTo
        "test1=value1&test1=value2&test2=value1&test%3D3=value%3D4"
    }
  }

  "The `queryParams` method" should {
    "return all query params" in new Context {
      requestPipeline.queryParams must be equalTo Map(
        "test1" -> Seq("value1", "value2"),
        "test2" -> Seq("value1")
      )
    }
  }

  "The `queryParam` method" should {
    "return the list of query params" in new Context {
      requestPipeline.queryParam("test1") must be equalTo Seq("value1", "value2")
    }

    "return an empty list if no query param with the given name was found" in new Context {
      requestPipeline.queryParam("test3") must beEmpty
    }
  }

  "The `withQueryParams` method" should {
    "append a new query param" in new Context {
      requestPipeline.withQueryParams("test3" -> "value1").queryParams must be equalTo Map(
        "test1" -> Seq("value1", "value2"),
        "test2" -> Seq("value1"),
        "test3" -> Seq("value1")
      )
    }

    "append multiple query params" in new Context {
      requestPipeline.withQueryParams("test3" -> "value1", "test4" -> "value1").queryParams must be equalTo Map(
        "test1" -> Seq("value1", "value2"),
        "test2" -> Seq("value1"),
        "test3" -> Seq("value1"),
        "test4" -> Seq("value1")
      )
    }

    "append multiple query params with the same name" in new Context {
      requestPipeline.withQueryParams("test3" -> "value1", "test3" -> "value2").queryParams must be equalTo Map(
        "test1" -> Seq("value1", "value2"),
        "test2" -> Seq("value1"),
        "test3" -> Seq("value1", "value2")
      )
    }

    "override an existing query param" in new Context {
      requestPipeline.withQueryParams("test2" -> "value2", "test2" -> "value3").queryParams must be equalTo Map(
        "test1" -> Seq("value1", "value2"),
        "test2" -> Seq("value2", "value3")
      )
    }

    "override multiple existing query params" in new Context {
      requestPipeline.withQueryParams("test1" -> "value3", "test2" -> "value2").queryParams must be equalTo Map(
        "test1" -> Seq("value3"),
        "test2" -> Seq("value2")
      )
    }
  }

  "The `withBodyExtractor` method" should {
    "set a new body extractor for the request" in new Context {
      requestPipeline.withBodyExtractor(new CustomBodyExtractor)
        .bodyExtractor.raw(Some(request.body -> MimeType.`application/octet-stream`)) must be equalTo "custom"
    }
  }

  "The default `fingerprint` method" should {
    "return fingerprint including the `User-Agent` header" in new Context {
      val userAgent = "test-user-agent"
      requestPipeline.withHeaders(Header("User-Agent", userAgent)).fingerprint() must
        be equalTo Hash.sha1(userAgent + "::")
    }

    "return fingerprint including the `Accept-Language` header" in new Context {
      val acceptLanguage = "test-accept-language"
      requestPipeline.withHeaders(Header("Accept-Language", acceptLanguage)).fingerprint() must
        be equalTo Hash.sha1(":" + acceptLanguage + ":")
    }

    "return fingerprint including the `Accept-Charset` header" in new Context {
      val acceptCharset = "test-accept-charset"
      requestPipeline.withHeaders(Header("Accept-Charset", acceptCharset)).fingerprint() must
        be equalTo Hash.sha1("::" + acceptCharset)
    }

    "return fingerprint including all values" in new Context {
      val userAgent = "test-user-agent"
      val acceptLanguage = "test-accept-language"
      val acceptCharset = "test-accept-charset"
      requestPipeline.withHeaders(
        Header("User-Agent", userAgent),
        Header("Accept-Language", acceptLanguage),
        Header("Accept-Charset", acceptCharset)
      ).fingerprint() must be equalTo Hash.sha1(
          userAgent + ":" + acceptLanguage + ":" + acceptCharset
        )
    }
  }

  "The custom `fingerprint` method" should {
    "return a fingerprint created by a generator function" in new Context {
      val userAgent = "test-user-agent"
      val acceptLanguage = "test-accept-language"
      val acceptCharset = "test-accept-charset"
      requestPipeline.withHeaders(
        Header(Header.Name.`User-Agent`, userAgent),
        Header(Header.Name.`Accept-Language`, acceptLanguage),
        Header(Header.Name.`Accept-Charset`, acceptCharset),
        Header(Header.Name.`Accept-Encoding`, "gzip", "deflate")
      ).fingerprint(request => Hash.sha1(new StringBuilder()
          .append(request.headerValue(Header.Name.`User-Agent`).getOrElse("")).append(":")
          .append(request.headerValue(Header.Name.`Accept-Language`).getOrElse("")).append(":")
          .append(request.headerValue(Header.Name.`Accept-Charset`).getOrElse("")).append(":")
          .append(request.headerValue(Header.Name.`Accept-Encoding`).getOrElse(""))
          .toString()
        )) must be equalTo Hash.sha1(
          userAgent + ":" + acceptLanguage + ":" + acceptCharset + ":gzip,deflate"
        )
    }
  }

  "The `unbox` method" should {
    "return the handled request" in new Context {
      requestPipeline.unbox must be equalTo request
    }
  }

  "The `extractString` method" should {
    "extract a value from query string if all parts are allowed" in new Context {
      requestPipeline.extractString("test1") must beSome("value1")
    }

    "extract a value from query string if part is allowed" in new Context {
      requestPipeline.extractString("test1", Some(Seq(RequestPart.QueryString))) must beSome("value1")
    }

    "do not extract a value from query string if part isn't allowed" in new Context {
      requestPipeline.extractString("test1", Some(Seq())) must beNone
    }

    "extract a value from headers if all parts are allowed" in new Context {
      requestPipeline.extractString("TEST1") must beSome("value1,value2")
    }

    "extract a value from headers if part is allowed" in new Context {
      requestPipeline.extractString("TEST1", Some(Seq(RequestPart.Headers))) must beSome("value1,value2")
    }

    "do not extract a value from headers if part isn't allowed" in new Context {
      requestPipeline.extractString("TEST1", Some(Seq())) must beNone
    }

    "extract a value from URL encoded body if all parts are allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsFormUrlEncoded(Map("code" -> Seq("value"))))

      requestPipeline.extractString("code") must beSome("value")
    }

    "extract a value from URL encoded body if part is allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsFormUrlEncoded(Map("code" -> Seq("value"))))

      requestPipeline.extractString("code", Some(Seq(RequestPart.FormUrlEncodedBody))) must beSome("value")
    }

    "do not extract a value from URL encoded body if part isn't allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsFormUrlEncoded(Map("code" -> Seq("value"))))

      requestPipeline.extractString("code", Some(Seq())) must beNone
    }

    "return None if the value couldn't be found in form URL encoded body" in new Context {
      override val requestPipeline = withBody(AnyContentAsFormUrlEncoded(Map("code" -> Seq("value"))))

      requestPipeline.extractString("none") must beNone
    }

    "extract a value from Json body if all parts are allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsJson(Json.obj("code" -> "value")))

      requestPipeline.extractString("code") must beSome("value")
    }

    "extract a value from Json body if part is allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsJson(Json.obj("code" -> "value")))

      requestPipeline.extractString("code", Some(Seq(RequestPart.JsonBody))) must beSome("value")
    }

    "do not extract a value from Json body if part isn't allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsJson(Json.obj("code" -> "value")))

      requestPipeline.extractString("code", Some(Seq())) must beNone
    }

    "return None if the value couldn't be found in JSON body" in new Context {
      override val requestPipeline = withBody(AnyContentAsJson(Json.obj("code" -> "value")))

      requestPipeline.extractString("none") must beNone
    }

    "extract a value from XML body if all parts are allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsXml(<code>value</code>))

      requestPipeline.extractString("code") must beSome("value")
    }

    "extract a value from XML body if part is allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsXml(<code>value</code>))

      requestPipeline.extractString("code", Some(Seq(RequestPart.XMLBody))) must beSome("value")
    }

    "do not extract a value from XML body if part isn't allowed" in new Context {
      override val requestPipeline = withBody(AnyContentAsXml(<code>value</code>))

      requestPipeline.extractString("code", Some(Seq())) must beNone
    }

    "return None if the value couldn't be found in XML body" in new Context {
      override val requestPipeline = withBody(AnyContentAsXml(<code>value</code>))

      requestPipeline.extractString("none") must beNone
    }

    "return None if no value could be found in the request" in new Context {
      requestPipeline.extractString("none") must beNone
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * A custom body extractor for testing.
     */
    case class CustomBodyExtractor[B]() extends PlayRequestBodyExtractor[B] {
      override def raw(body: Option[(B, MimeType)]): String = "custom"
    }

    /**
     * A request.
     */
    val request = FakeRequest(
      method = "POST",
      uri = "http://localhost",
      headers = Headers(
        "TEST1" -> "value1",
        "Test1" -> "value2",
        "TEST2" -> "value1"
      ),
      body = AnyContentAsEmpty
    ).withMethod(
        "POST"
      ).withHeaders(
          "TEST1" -> "value1",
          "Test1" -> "value2",
          "TEST2" -> "value1"
        ).withCookies(
            PlayCookie("test1", "value1"),
            PlayCookie("test2", "value2")
          ).withTarget(RequestTarget("http://localhost", "/", Map(
              "test1" -> Seq("value1", "value2"),
              "test2" -> Seq("value1")
            )))

    /**
     * A request pipeline which handles a request.
     */
    val requestPipeline = PlayRequestPipeline[AnyContent](request)

    /**
     * A helper that creates a request pipeline with a body.
     *
     * @param body The body to create the request with.
     * @return A request pipeline with the given body.
     */
    def withBody[B <: AnyContent](body: B): PlayRequestPipeline[B] = {
      PlayRequestPipeline(request.withBody(body))
    }
  }
}
