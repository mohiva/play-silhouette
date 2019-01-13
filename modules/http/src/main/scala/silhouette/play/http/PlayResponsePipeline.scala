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

import play.api.mvc.{ Result, Results }
import silhouette.RichSeq._
import silhouette.http._

import scala.language.implicitConversions

/**
 * The response pipeline implementation based on the Play `Result` class.
 *
 * @param response The response this pipeline handles.
 */
final case class PlayResponsePipeline(response: Result)
  extends ResponsePipeline[Result] {

  /**
   * Gets the HTTP status code.
   *
   * @return The HTTP status code.
   */
  override def status: Status = response.header.status

  /**
   * Gets all headers.
   *
   * Play doesn't allow to set duplicate headers: https://github.com/playframework/playframework/issues/3544
   *
   * @return All headers.
   */
  override def headers: Seq[Header] = response.header.headers.toSeq.map {
    case (name, value) => Header(Header.Name(name), value)
  }

  /**
   * Creates a new response pipeline with the given headers.
   *
   * @inheritdoc
   *
   * Play doesn't allow to set duplicate headers: https://github.com/playframework/playframework/issues/3544
   *
   * @param headers The headers to add.
   * @return A new response pipeline instance with the added headers.
   */
  override def withHeaders(headers: Header*): PlayResponsePipeline = {
    val groupedHeaders = headers.groupByPreserveOrder(_.name).map {
      case (key, h) => Header(key, h.flatMap(_.values): _*)
    }
    val newHeaders = groupedHeaders.foldLeft(this.headers) {
      case (acc, header) =>
        acc.indexWhere(_.name == header.name) match {
          case -1 => acc :+ header
          case i  => acc.patch(i, Seq(header), 1)
        }
    }.map { header =>
      header.name.value -> header.value
    }.toMap

    copy(response.copy(header = response.header.copy(headers = newHeaders)))
  }

  /**
   * Gets the list of cookies.
   *
   * @return The list of cookies.
   */
  override def cookies: Seq[Cookie] = response.newCookies.map(playCookieToSilhouetteCookie)

  /**
   * Creates a new response pipeline with the given cookies.
   *
   * @inheritdoc
   *
   * @param cookies The cookies to add.
   * @return A new response pipeline instance with the added cookies.
   */
  override def withCookies(cookies: Cookie*): PlayResponsePipeline = {
    val filteredCookies = cookies.groupByPreserveOrder(_.name).map(_._2.last)
    val newCookies = filteredCookies.foldLeft(this.cookies) {
      case (acc, cookie) =>
        acc.indexWhere(_.name == cookie.name) match {
          case -1 => acc :+ cookie
          case i  => acc.patch(i, Seq(cookie), 1)
        }
    }

    copy(response.withCookies(newCookies.map(silhouetteCookieToPlayCookie): _*))
  }
  /**
   * Unboxes the framework specific response implementation.
   *
   * @return The framework specific response implementation.
   */
  override def unbox: Result = response
}

/**
 * The companion object.
 */
object PlayResponsePipeline {

  /**
   * Converts the [[silhouette.http.SilhouetteResponsePipeline]] type to the
   * [[silhouette.play.http.PlayResponsePipeline]] type.
   *
   * @param responsePipeline The instance to convert.
   * @return The converted instance.
   */
  implicit def fromSilhouetteResponsePipeline(
    responsePipeline: SilhouetteResponsePipeline
  ): PlayResponsePipeline = {
    PlayResponsePipeline(new Results.Status(responsePipeline.status.code))
      .withCookies(responsePipeline.cookies: _*)
      .withHeaders(responsePipeline.headers: _*)
  }

  /**
   * Converts the [[silhouette.play.http.PlayResponsePipeline]] type to the [[play.api.mvc.Result]] type.
   *
   * @param responsePipeline The instance to convert.
   * @return The converted instance.
   */
  implicit def toPlayResult(responsePipeline: PlayResponsePipeline): Result = responsePipeline.unbox
}
