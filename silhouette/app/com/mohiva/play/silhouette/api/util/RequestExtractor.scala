/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.api.util

import com.mohiva.play.silhouette.api.Logger
import com.mohiva.play.silhouette.api.util.RequestExtractor._
import play.api.libs.json.JsValue
import play.api.libs.typedmap.TypedKey
import play.api.mvc._

import scala.language.implicitConversions
import scala.xml.NodeSeq

/**
 * Adds the ability to extract values from a request.
 */
trait RequestExtractor[-B] extends Logger {

  /**
   * The request parts from which a value can be extracted.
   */
  type Parts = Seq[RequestPart.Value]

  /**
   * Extracts a string from a request.
   *
   * @param name The name of the value to extract.
   * @param parts Some request parts from which a value can be extracted or None to extract values from any part of the request.
   * @param request The request from which the value should be extract.
   * @return The extracted value as string.
   */
  def extractString(name: String, parts: Option[Parts] = None)(implicit request: Request[B]): Option[String]

  /**
   * Extracts a value from query string.
   *
   * @param name The name of the value to extract.
   * @param parts The request parts from which a value can be extracted.
   * @param request The request from which the value should be extract.
   * @return The extracted value as string.
   */
  protected def fromQueryString(name: String, parts: Option[Parts])(implicit request: Request[B]): Option[String] = {
    isAllowed(RequestPart.QueryString, parts) {
      logger.debug("[Silhouette] Try to extract value with name `%s` from query string: %s".format(name, request.rawQueryString))
      request.queryString.get(name).flatMap(_.headOption)
    }
  }

  /**
   * Extracts a value from headers.
   *
   * @param name The name of the value to extract.
   * @param parts The request parts from which a value can be extracted.
   * @param request The request from which the value should be extract.
   * @return The extracted value as string.
   */
  protected def fromHeaders(name: String, parts: Option[Parts])(implicit request: Request[B]): Option[String] = {
    isAllowed(RequestPart.Headers, parts) {
      logger.debug("[Silhouette] Try to extract value with name `%s` from headers: %s".format(name, request.headers))
      request.headers.get(name)
    }
  }

  /**
   * Extracts a value from attributes.
   *
   * @param name The name of the value to extract.
   * @param parts The request parts from which a value can be extracted.
   * @param request The request from which the value should be extract.
   * @return The extracted value as string.
   */
  protected def fromAttributes(name: String, parts: Option[Parts])(implicit request: Request[B]): Option[String] = {
    isAllowed(RequestPart.Attributes, parts) {
      val key: TypedKey[String] = TypedKey(name)
      logger.debug("[Silhouette] Try to extract value with name `%s` from attributes: %s".format(name, request.attrs))
      request.attrs.get(key)
    }
  }

  /**
   * Extracts a value from form url encoded body.
   *
   * @param name The name of the value to extract.
   * @param body The body from which the value should be extract.
   * @param parts The request parts from which a value can be extracted.
   * @return The extracted value as string.
   */
  protected def fromFormUrlEncoded(name: String, body: Map[String, Seq[String]], parts: Option[Parts]): Option[String] = {
    isAllowed(RequestPart.FormUrlEncodedBody, parts) {
      logger.debug("[Silhouette] Try to extract value with name `%s` from form url encoded body: %s".format(name, body))
      body.get(name).flatMap(_.headOption)
    }
  }

  /**
   * Extracts a value from Json body.
   *
   * @param name The name of the value to extract.
   * @param body The body from which the value should be extract.
   * @param parts The request parts from which a value can be extracted.
   * @return The extracted value.
   */
  protected def fromJson(name: String, body: JsValue, parts: Option[Parts]): Option[String] = {
    isAllowed(RequestPart.JsonBody, parts) {
      logger.debug("[Silhouette] Try to extract value with name `%s` from Json body: %s".format(name, body))
      body.\(name).asOpt[String]
    }
  }

  /**
   * Extracts a value from Xml body.
   *
   * @param name The name of the value to extract.
   * @param body The body from which the value should be extract.
   * @param parts The request parts from which a value can be extracted.
   * @return The extracted value.
   */
  protected def fromXml(name: String, body: NodeSeq, parts: Option[Parts]): Option[String] = {
    isAllowed(RequestPart.XMLBody, parts) {
      logger.debug("[Silhouette] Try to extract value with name `%s` from Xml body: %s".format(name, body))
      body.\\(name).headOption.map(_.text)
    }
  }

  /**
   * Extracts a value from the default parts of a request.
   *
   * @param name The name of the value to extract.
   * @param parts The request parts from which a value can be extracted.
   * @param request The request from which the value should be extract.
   * @return The extracted value.
   */
  protected def fromDefaultParts(name: String, parts: Option[Parts])(implicit request: Request[B]): Option[String] = {
    fromQueryString(name, parts)
      .orElse(fromHeaders(name, parts))
      .orElse(fromAttributes(name, parts))
  }

  /**
   * Executes the given block if the given part is contained in the list of parts or if part validation is disabled.
   *
   * @param part The part to check for.
   * @param parts The request parts from which a value can be extracted.
   * @param b The block to execute.
   * @return The found value if any.
   */
  private def isAllowed(part: RequestPart.Value, parts: Option[Parts])(b: => Option[String]): Option[String] = {
    parts match {
      case Some(p) if !p.contains(part) => None
      case _                            => b
    }
  }
}

/**
 * The companion object.
 */
object RequestExtractor extends DefaultRequestExtractors

/**
 * The request parts from which a value can be extracted.
 */
object RequestPart extends Enumeration {

  /**
   * Allows to extract a request value from query string.
   */
  val QueryString = Value("query-string")

  /**
   * Allows to extract a request value from the headers.
   */
  val Headers = Value("headers")

  /**
   * Allows to extract a request value from the attributes.
   */
  val Attributes = Value("attributes")

  /**
   * Allows to extract a request value from a Json body.
   */
  val JsonBody = Value("json-body")

  /**
   * Allows to extract a request value from a XML body.
   */
  val XMLBody = Value("xml-body")

  /**
   * Allows to extract a request value from a form-urlencoded body.
   */
  val FormUrlEncodedBody = Value("form-urlencoded-body")
}

/**
 * Default request extractors with lower priority.
 */
trait LowPriorityRequestExtractors {

  /**
   * Tries to extract the value from query string.
   *
   * This acts as a catch all request extractor to avoid errors for not implemented body types.
   */
  implicit def anyExtractor[B]: RequestExtractor[B] = new RequestExtractor[B] {
    def extractString(name: String, parts: Option[Parts] = None)(implicit request: Request[B]) = {
      fromDefaultParts(name, parts)
    }
  }
}

/**
 * Contains the default request extractors.
 */
trait DefaultRequestExtractors extends LowPriorityRequestExtractors {

  /**
   * Tries to extract the value from query string and then it tries to extract the value from any
   * content.
   */
  implicit val anyContentExtractor = new RequestExtractor[AnyContent] {
    def extractString(name: String, parts: Option[Parts] = None)(implicit request: Request[AnyContent]) = {
      fromDefaultParts(name, parts)
        .orElse(request.body.asFormUrlEncoded.flatMap { body => fromFormUrlEncoded(name, body, parts) })
        .orElse(request.body.asJson.flatMap { body => fromJson(name, body, parts) })
        .orElse(request.body.asXml.flatMap { body => fromXml(name, body, parts) })
    }
  }

  /**
   * Tries to extract the value from query string and then from form url encoded body.
   */
  implicit val formUrlEncodedExtractor = new RequestExtractor[Map[String, Seq[String]]] {
    def extractString(name: String, parts: Option[Parts] = None)(implicit request: Request[Map[String, Seq[String]]]) = {
      fromDefaultParts(name, parts).orElse(fromFormUrlEncoded(name, request.body, parts))
    }
  }

  /**
   * Tries to extract the value from query string and then from Json body.
   */
  implicit val jsonExtractor = new RequestExtractor[JsValue] {
    def extractString(name: String, parts: Option[Parts] = None)(implicit request: Request[JsValue]) = {
      fromDefaultParts(name, parts).orElse(fromJson(name, request.body, parts))
    }
  }

  /**
   * Tries to extract the value from query string and then from Xml body.
   */
  implicit val xmlExtractor = new RequestExtractor[NodeSeq] {
    def extractString(name: String, parts: Option[Parts] = None)(implicit request: Request[NodeSeq]) = {
      fromDefaultParts(name, parts).orElse(fromXml(name, request.body, parts))
    }
  }
}

/**
 * A request which can extract values based on the request body.
 *
 * @param request The request.
 * @param extractor The extractor to extract the value.
 * @tparam B The type of the request body.
 */
class ExtractableRequest[B](request: Request[B])(implicit extractor: RequestExtractor[B])
  extends WrappedRequest[B](request) {

  /**
   * Extracts a string from a request.
   *
   * @param name The name of the value to extract.
   * @return The extracted value as string.
   */
  def extractString(name: String, parts: Option[Seq[RequestPart.Value]] = None): Option[String] =
    extractor.extractString(name, parts)(request)
}

/**
 * The companion object.
 */
object ExtractableRequest {

  /**
   * Creates an extractable request from an implicit request.
   *
   * @param request The implicit request.
   * @param extractor The extractor to extract the value.
   * @tparam B The type of the request body.
   * @return An extractable request.
   */
  def apply[B](implicit request: Request[B], extractor: RequestExtractor[B]) = new ExtractableRequest(request)

  /**
   * Converts a `Request` to an `ExtractableRequest` instance.
   *
   * @param request The request to convert.
   * @param extractor The extractor to extract the value.
   * @tparam B The type of the request body.
   * @return An extractable request.
   */
  implicit def convertExplicit[B](request: Request[B])(implicit extractor: RequestExtractor[B]): ExtractableRequest[B] =
    new ExtractableRequest(request)

  /**
   * Converts an implicit `Request` to an `ExtractableRequest` instance.
   *
   * @param request The request to convert.
   * @param extractor The extractor to extract the value.
   * @tparam B The type of the request body.
   * @return An extractable request.
   */
  implicit def convertImplicit[B](implicit request: Request[B], extractor: RequestExtractor[B]): ExtractableRequest[B] =
    this(request, extractor)
}
