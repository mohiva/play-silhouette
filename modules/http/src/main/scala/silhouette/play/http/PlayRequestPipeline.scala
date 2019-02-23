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

import play.api.mvc.request.{ Cell, RequestAttrKey }
import play.api.mvc.{ Cookies, Headers, Request }
import silhouette.RichSeq._
import silhouette.http._

import scala.language.implicitConversions

/**
 * The request pipeline implementation based on the `SilhouetteRequest`.
 *
 * @param request       The request this pipeline handles.
 * @param bodyExtractor The request body extractor used to extract values from request body.
 * @tparam B The type of the request body.
 */
final case class PlayRequestPipeline[B](
  request: Request[B],
  bodyExtractor: RequestBodyExtractor[Request[B]] = new PlayRequestBodyExtractor[B]
) extends RequestPipeline[Request[B]] {

  /**
   * Gets the absolute URI of the request target.
   *
   * This must contain the absolute URI of thr request target, because we need this to resolve relative URIs
   * against this.
   *
   * @return The absolute URI of the request target.
   */
  override def uri: URI = request.target.uri

  /**
   * Creates a new request pipeline with the given URI.
   *
   * This must contain the absolute URI of thr request target, because we need this to resolve relative URIs
   * against this URI.
   *
   * @param uri The absolute URI of the request target.
   * @return A new request pipeline instance with the set URI.
   */
  override def withUri(uri: URI): PlayRequestPipeline[B] = copy(request.withTarget(request.target.withUri(uri)))

  /**
   * Gets the HTTP request method.
   *
   * @return The HTTP request method.
   */
  override def method: Method = request.method

  /**
   * Creates a new request pipeline with the given HTTP request method.
   *
   * @param method The HTTP request method to set.
   * @return A new request pipeline instance with the set HTTP request method.
   */
  override def withMethod(method: Method): PlayRequestPipeline[B] = copy(request.withMethod(method))

  /**
   * Gets all headers.
   *
   * @return All headers.
   */
  override def headers: Seq[Header] = request.headers.toMap.map {
    case (name, values) =>
      Header(name, values: _*)
  }.toSeq

  /**
   * Creates a new request pipeline with the given headers.
   *
   * @inheritdoc
   *
   * @param headers The headers to set.
   * @return A new request pipeline instance with the set headers.
   */
  override def withHeaders(headers: Header*): PlayRequestPipeline[B] = {
    val groupedHeaders = headers.groupByPreserveOrder(_.name).map {
      case (key, h) => Header(key, h.flatMap(_.values): _*)
    }
    val newHeaders = groupedHeaders.foldLeft(request.headers.toMap) {
      case (acc, header) =>
        acc.get(header.name.value) match {
          case None    => acc ++ Map(header.name.value -> header.values)
          case Some(_) => acc.updated(header.name.value, header.values)
        }
    }.foldLeft(Seq.empty[(String, String)]) {
      case (acc, (key, values)) =>
        acc ++ values.map(value => key -> value)
    }

    copy(request.withHeaders(Headers(newHeaders: _*)))
  }

  /**
   * Gets the list of cookies.
   *
   * @return The list of cookies.
   */
  override def cookies: Seq[Cookie] = request.cookies.map(playCookieToSilhouetteCookie).toList

  /**
   * Creates a new request pipeline with the given cookies.
   *
   * @inheritdoc
   *
   * @param cookies The cookies to set.
   * @return A new request pipeline instance with the set cookies.
   */
  override def withCookies(cookies: Cookie*): PlayRequestPipeline[B] = {
    val filteredCookies = cookies.groupByPreserveOrder(_.name).map(_._2.last)
    val newCookies = Cookies(filteredCookies.foldLeft(request.cookies.toList) {
      case (acc, cookie) =>
        acc.indexWhere(_.name == cookie.name) match {
          case -1 => acc :+ silhouetteCookieToPlayCookie(cookie)
          case i  => acc.patch(i, Seq(silhouetteCookieToPlayCookie(cookie)), 1)
        }
    })

    copy(request.withAttrs(request.attrs + RequestAttrKey.Cookies.bindValue(Cell(newCookies))))
  }

  /**
   * Gets all query params.
   *
   * @return All query params.
   */
  def queryParams: Map[String, Seq[String]] = request.queryString

  /**
   * Creates a new request pipeline with the given query params.
   *
   * @inheritdoc
   *
   * @param params The query params to set.
   * @return A new request pipeline instance with the set query params.
   */
  override def withQueryParams(params: (String, String)*): PlayRequestPipeline[B] = {
    val newParams = params.groupByPreserveOrder(_._1).map {
      case (key, value) => key -> value.map(_._2)
    }.foldLeft(request.queryString) {
      case (acc, (key, value)) => acc + (key -> value)
    }

    copy(request.withTarget(request.target.withQueryString(newParams)))
  }

  /**
   * Creates a new request pipeline with the given body extractor.
   *
   * @param bodyExtractor The body extractor to set.
   * @return A new request pipeline instance with the set body extractor.
   */
  override def withBodyExtractor(bodyExtractor: RequestBodyExtractor[Request[B]]): PlayRequestPipeline[B] = {
    copy(bodyExtractor = bodyExtractor)
  }

  /**
   * Unboxes the request this pipeline handles.
   *
   * @return The request this pipeline handles.
   */
  override def unbox: Request[B] = request
}

/**
 * The companion object.
 */
object PlayRequestPipeline {

  /**
   * Converts the [[silhouette.play.http.PlayRequestPipeline]] type to the [[play.api.mvc.Request]] type.
   *
   * @param requestPipeline The instance to convert.
   * @tparam B The type of the request body.
   * @return The converted instance.
   */
  implicit def toPlayRequest[B](requestPipeline: PlayRequestPipeline[B]): Request[B] = requestPipeline.unbox

  /**
   * Converts the [[play.api.mvc.Request]] type to a [[silhouette.play.http.PlayRequestPipeline]] type.
   *
   * @param request The instance to convert.
   * @tparam B The type of the request body.
   * @return The converted instance.
   */
  implicit def fromPlayRequest[B](request: Request[B]): PlayRequestPipeline[B] = PlayRequestPipeline(request)
}
