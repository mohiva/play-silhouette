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

import play.api.mvc._
import silhouette.http.{ Request => _, _ }

/**
 * The request body extractor based on the [[play.api.mvc.Request]].
 *
 * @tparam B The type of the body.
 */
class PlayRequestBodyExtractor[B] extends RequestBodyExtractor[Request[B]] {

  /**
   * Gets the raw string representation of the body for debugging purpose.
   *
   * @param request The request from which the body should be extracted.
   * @return The raw string representation of the body for debugging purpose.
   */
  override def raw(request: Request[B]): String = request.body match {
    case AnyContentAsFormUrlEncoded(map) => map.toString()
    case AnyContentAsJson(json)          => json.toString()
    case AnyContentAsXml(xml)            => xml.toString()
    case _                               => ""
  }

  /**
   * Extracts a value from JSON body.
   *
   * @param request The request from which the body should be extracted.
   * @param name    The name of the value to extract.
   * @return The extracted value on success, otherwise an error on failure.
   */
  override def fromJson(request: Request[B], name: String): ExtractionResult = {
    if (!request.hasBody) {
      EmptyBody
    } else {
      request.body match {
        case AnyContentAsJson(json) => json.\(name).asOpt[String] match {
          case Some(value) => ExtractedValue(value)
          case None        => NotFound
        }
        case _ => WrongContentType(contentType(request), JsonBody.allowedTypes)
      }
    }
  }

  /**
   * Extracts a value from XML body.
   *
   * @param request The request from which the body should be extracted.
   * @param name    The name of the value to extract.
   * @return The extracted value on success, otherwise an error on failure.
   */
  override def fromXml(request: Request[B], name: String): ExtractionResult = {
    if (!request.hasBody) {
      EmptyBody
    } else {
      request.body match {
        case AnyContentAsXml(xml) => xml.\\(name).headOption.map(_.text) match {
          case Some(value) => ExtractedValue(value)
          case None        => NotFound
        }
        case _ => WrongContentType(contentType(request), XmlBody.allowedTypes)
      }
    }
  }

  /**
   * Extracts a value from form-url-encoded body.
   *
   * @param request The request from which the body should be extracted.
   * @param name    The name of the value to extract.
   * @return The extracted value on success, otherwise an error on failure.
   */
  override def fromFormUrlEncoded(request: Request[B], name: String): ExtractionResult = {
    if (!request.hasBody) {
      EmptyBody
    } else {
      request.body match {
        case AnyContentAsFormUrlEncoded(map) => map.get(name).flatMap(_.headOption) match {
          case Some(value) => ExtractedValue(value)
          case None        => NotFound
        }
        case _ => WrongContentType(contentType(request), Seq(FormUrlEncodedBody.contentType))
      }
    }
  }

  /**
   * Tries to get the content type from request.
   *
   * @param request The request from which the content type should be returned.
   * @return The content type if found, application/octet-stream otherwise.
   */
  private def contentType(request: Request[B]): MimeType =
    request.contentType.map(MimeType.fromString).getOrElse(MimeType.`application/octet-stream`)
}
