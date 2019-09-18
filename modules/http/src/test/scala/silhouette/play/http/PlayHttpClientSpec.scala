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

import java.net.URI

import akka.util.ByteString
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import silhouette.http.client.{ Request, Response }
import silhouette.http.{ Body, Header, MimeType, Status }
import silhouette.specs2.Wait

import scala.concurrent.Future

/**
 * Test case for the [[PlayHttpClient]] class.
 *
 * @param ev The execution environment.
 */
class PlayHttpClientSpec(implicit ev: ExecutionEnv) extends Specification with Mockito with Wait {

  "The `execute` method" should {
    "execute a request with the given URI" in new Context {
      await(httpClient.execute(request))

      there was one(wsClient).url(request.uri.toString)
    }

    "execute a request with the given method" in new Context {
      await(httpClient.execute(request.withMethod("POST")))

      there was one(wsRequest).withMethod("POST")
    }

    "execute a request with the given query string params" in new Context {
      val params = List("param1" -> "value1", "param2" -> "value2")
      await(httpClient.execute(request.withQueryParams(params: _*)))

      there was one(wsRequest).withQueryStringParameters(params: _*)
    }

    "execute a request with the given headers" in new Context {
      await(httpClient.execute(request.withHeaders(
        Header("Content-Encoding", "UTF-8"),
        Header("Content-Type", "application/json", "charset=UTF-8")
      )))

      there was one(wsRequest).addHttpHeaders(
        "Content-Encoding" -> "UTF-8"
      ) andThen one(wsRequest).addHttpHeaders(
          "Content-Type" -> "application/json", "Content-Type" -> "charset=UTF-8"
        )
    }

    "execute a request with a body" in new Context {
      await(httpClient.execute(request.withBody(body)))

      there was one(wsRequest).withBody(body.data.toArray)
    }

    "return a response with the correct status" in new Context {
      httpClient.execute(request) must beLike[Response] {
        case response =>
          response.status must be equalTo Status.OK
      }.await
    }

    "return a response with the correct headers" in new Context {
      httpClient.execute(request) must beLike[Response] {
        case response =>
          response.headers must be equalTo List(
            Header("Content-Encoding", "UTF-8"),
            Header("Content-Type", "application/json", "charset=UTF-8")
          )
      }.await
    }

    "return a response with a body" in new Context {
      httpClient.execute(request) must beLike[Response] {
        case response =>
          response.body must beSome(body)
      }.await
    }

    "return a response without a body" in new Context {
      wsResponse.bodyAsBytes returns ByteString("")
      httpClient.execute(request) must beLike[Response] {
        case response =>
          response.body must beNone
      }.await
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {
    import scala.collection.compat.immutable.ArraySeq

    /**
     * A test body.
     */
    val body = Body(
      contentType = MimeType.`application/json`,
      data = ArraySeq.unsafeWrapArray("""{ test: "test" }""".getBytes(Body.DefaultCodec.charSet))
    )

    /**
     * A test request.
     */
    val request = Request(new URI("http://localhost"))

    /**
     * A mock of the Play [[WSResponse]] class.
     */
    val wsResponse = {
      val m = mock[WSResponse].smart
      m.bodyAsBytes returns ByteString(body.data.toArray)
      m.status returns 200
      m.contentType returns "application/json"
      m.headers returns Map(
        "Content-Encoding" -> Seq("UTF-8"),
        "Content-Type" -> Seq("application/json", "charset=UTF-8")
      )
      m
    }

    /**
     * A mock of the Play [[WSRequest]] class.
     */
    val wsRequest = {
      val m = mock[WSRequest].smart
      m.withMethod(anyString) returns m
      m.withQueryStringParameters(any()) returns m
      m.addHttpHeaders(any()) returns m
      m.withBody(any())(any()) returns m
      m.execute() returns Future.successful(wsResponse)
      m
    }

    /**
     * A mock of the Play [[WSClient]] class.
     */
    val wsClient = {
      val m = mock[WSClient].smart
      m.url(anyString) returns wsRequest
      m
    }

    /**
     * The HTTP client to test.
     */
    val httpClient = PlayHttpClient(wsClient)
  }
}
