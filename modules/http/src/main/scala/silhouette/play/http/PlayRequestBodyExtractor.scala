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
import silhouette.http.RequestBodyExtractor._
import silhouette.http._

/**
 * The request body extractor based on the [[play.api.mvc.Request]].
 *
 * @tparam B The type of the body.
 */
class PlayRequestBodyExtractor[B] extends RequestBodyExtractor[Option[(B, MimeType)]] {

  /**
   * Gets the raw string representation of the body for debugging purpose.
   *
   * @param maybeBody Maybe the body to extract from and the content type.
   * @return The raw string representation of the body for debugging purpose.
   */
  override def raw(maybeBody: Option[(B, MimeType)]): String = maybeBody match {
    case Some((AnyContentAsFormUrlEncoded(map), _)) => map.toString()
    case Some((AnyContentAsJson(json), _)) => json.toString()
    case Some((AnyContentAsXml(xml), _)) => xml.toString()
    case _ => ""
  }

  /**
   * Extracts a value from JSON body.
   *
   * @param maybeBody Maybe the body to extract from and the content type.
   * @param name      The name of the value to extract.
   * @return The extracted value on success, otherwise an error on failure.
   */
  override def fromJson(maybeBody: Option[(B, MimeType)], name: String): ExtractionResult = {
    maybeBody match {
      case None => WithoutBody
      case Some((body, contentType)) =>
        body match {
          case AnyContentAsJson(json) => json.\(name).asOpt[String] match {
            case Some(value) => ExtractedValue(value)
            case None        => NotFound
          }
          case _ => WrongContentType(contentType, JsonBody.allowedTypes)
        }
    }
  }

  /**
   * Extracts a value from XML body.
   *
   * @param maybeBody Maybe the body to extract from and the content type.
   * @param name      The name of the value to extract.
   * @return The extracted value on success, otherwise an error on failure.
   */
  override def fromXml(maybeBody: Option[(B, MimeType)], name: String): ExtractionResult = {
    maybeBody match {
      case None => WithoutBody
      case Some((body, contentType)) =>
        body match {
          case AnyContentAsXml(xml) => xml.\\(name).headOption.map(_.text) match {
            case Some(value) => ExtractedValue(value)
            case None        => NotFound
          }
          case _ => WrongContentType(contentType, XmlBody.allowedTypes)
        }
    }
  }

  /**
   * Extracts a value from form-url-encoded body.
   *
   * @param maybeBody Maybe the body to extract from and the content type.
   * @param name      The name of the value to extract.
   * @return The extracted value on success, otherwise an error on failure.
   */
  override def fromFormUrlEncoded(maybeBody: Option[(B, MimeType)], name: String): ExtractionResult = {
    maybeBody match {
      case None => WithoutBody
      case Some((body, contentType)) =>
        body match {
          case AnyContentAsFormUrlEncoded(map) => map.get(name).flatMap(_.headOption) match {
            case Some(value) => ExtractedValue(value)
            case None        => NotFound
          }
          case _ => WrongContentType(contentType, Seq(FormUrlEncodedBody.contentType))
        }
    }
  }
}
