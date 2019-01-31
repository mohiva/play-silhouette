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

import javax.inject.Inject
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import silhouette.http._
import silhouette.http.client.{ Request, Response }

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions

/**
 * Implementation of the Silhouette [[HttpClient]] based on the Play WS client.
 *
 * @param underlying The underlying Play WS client.
 * @param ec         The execution context to handle the asynchronous operations.
 */
case class PlayHttpClient @Inject() (underlying: WSClient)(implicit ec: ExecutionContext) extends HttpClient {

  /**
   * Monkey patches the [[WSRequest]] instance.
   *
   * @param wsRequest The [[WSRequest]] instance to patch.
   */
  implicit class RichWsRequest(wsRequest: WSRequest) {

    /**
     * Adds a list of Silhouette [[Header]] instances to a [[WSRequest]].
     *
     * @param headers The headers to add.
     * @return The [[WSRequest]] instance to provide a fluent interface.
     */
    def withSilhouetteHeaders(headers: Seq[Header]): WSRequest = {
      headers.foldLeft(wsRequest) {
        case (acc, header) =>
          val headers = header.values.map(header.name.value -> _)
          acc.addHttpHeaders(headers: _*)
      }
    }

    /**
     * Adds a Silhouette [[Body]] to a [[WSRequest]].
     *
     * @param body The body to add.
     * @return The [[WSRequest]] instance to provide a fluent interface.
     */
    def withSilhouetteBody(body: Option[Body]): WSRequest = {
      body.map(body => wsRequest.withBody(body.data.array)).getOrElse(wsRequest)
    }
  }

  /**
   * Execute the request and produce a response.
   *
   * @param request The request to execute.
   * @return The resulting response.
   */
  override def execute(request: Request): Future[Response] = {
    request.execute().map(playResponseToSilhouetteResponse)
  }

  /**
   * Converts a Silhouette [[Request]] to a Play [[WSRequest]].
   *
   * @param request The Silhouette [[Request]] to convert.
   * @return The resulting Play [[WSRequest]].
   */
  private implicit def silhouetteRequestToPlayRequest(request: Request): WSRequest = {
    underlying.url(request.uri.toString)
      .withMethod(request.method)
      .withQueryStringParameters(request.queryParams: _*)
      .withSilhouetteHeaders(request.headers)
      .withSilhouetteBody(request.body)
  }

  /**
   * Converts a Play [[WSResponse]] to a Silhouette [[Response]].
   *
   * @param wsResponse The Play [[WSResponse]] to convert.
   * @return The resulting Silhouette [[Response]].
   */
  private implicit def playResponseToSilhouetteResponse(wsResponse: WSResponse): Response = {
    val bytes = wsResponse.bodyAsBytes.toArray
    new Response(
      status = wsResponse.status,
      body = if (bytes.length == 0) None else Some(Body(wsResponse.contentType, data = bytes)),
      headers = wsResponse.headers.map { case (name, values) => Header(name, values: _*) }.toList
    )
  }
}
