package com.mohiva.play.silhouette.api.util

import com.mohiva.play.silhouette.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc._

import scala.language.implicitConversions
import scala.xml.NodeSeq

/**
 * Adds the ability to extract values from a request.
 */
trait RequestExtractor[-B] extends Logger {

  /**
   * Extracts a string from a request.
   *
   * @param name The name of the value to extract.
   * @param request The request from which the value should be extract.
   * @return The extracted value as string.
   */
  def extractString(name: String)(implicit request: Request[B]): Option[String]

  /**
   * Extracts a value from query string.
   *
   * @param name The name of the value to extract.
   * @param request The request from which the value should be extract.
   * @return The extracted value as string.
   */
  protected def fromQueryString(name: String)(implicit request: Request[B]): Option[String] = {
    logger.debug("[Silhouette] Try to extract value with name `%s` from query string: %s".format(name, request.rawQueryString))
    request.queryString.get(name).flatMap(_.headOption)
  }

  /**
   * Extracts a value from form url encoded body.
   *
   * @param name The name of the value to extract.
   * @param body The body from which the value should be extract.
   * @return The extracted value as string.
   */
  protected def fromFormUrlEncoded(name: String, body: Map[String, Seq[String]]): Option[String] = {
    logger.debug("[Silhouette] Try to extract value with name `%s` from form url encoded body: %s".format(name, body))
    body.get(name).flatMap(_.headOption)
  }

  /**
   * Extracts a value from Json body.
   *
   * @param name The name of the value to extract.
   * @param body The body from which the value should be extract.
   * @return The extracted value.
   */
  protected def fromJson(name: String, body: JsValue): Option[String] = {
    logger.debug("[Silhouette] Try to extract value with name `%s` from Json body: %s".format(name, body))
    body.\(name).asOpt[String]
  }

  /**
   * Extracts a value from Xml body.
   *
   * @param name The name of the value to extract.
   * @param body The body from which the value should be extract.
   * @return The extracted value.
   */
  protected def fromXml(name: String, body: NodeSeq): Option[String] = {
    logger.debug("[Silhouette] Try to extract value with name `%s` from Xml body: %s".format(name, body))
    body.\\(name).headOption.map(_.text)
  }
}

/**
 * The companion object.
 */
object RequestExtractor extends DefaultRequestExtractors

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
    def extractString(name: String)(implicit request: Request[B]) = {
      fromQueryString(name)
    }
  }
}

/**
 * Contains the default request extractors.
 */
trait DefaultRequestExtractors extends LowPriorityRequestExtractors {

  /**
   * Tries to extract the value from query string and then from form url encoded body.
   */
  implicit val anyContentAsFormUrlEncodedExtractor = new RequestExtractor[AnyContentAsFormUrlEncoded] {
    def extractString(name: String)(implicit request: Request[AnyContentAsFormUrlEncoded]) = {
      fromQueryString(name).orElse(fromFormUrlEncoded(name, request.body.data))
    }
  }

  /**
   * Tries to extract the value from query string and then from Json body.
   */
  implicit val anyContentAsJsonExtractor = new RequestExtractor[AnyContentAsJson] {
    def extractString(name: String)(implicit request: Request[AnyContentAsJson]) = {
      fromQueryString(name).orElse(fromJson(name, request.body.json))
    }
  }

  /**
   * Tries to extract the value from query string and then from Xml body.
   */
  implicit val anyContentAsXmlExtractor = new RequestExtractor[AnyContentAsXml] {
    def extractString(name: String)(implicit request: Request[AnyContentAsXml]) = {
      fromQueryString(name).orElse(fromXml(name, request.body.xml))
    }
  }

  /**
   * Tries to extract the value from query string and then from form url encoded body.
   */
  implicit val formUrlEncodedExtractor = new RequestExtractor[Map[String, Seq[String]]] {
    def extractString(name: String)(implicit request: Request[Map[String, Seq[String]]]) = {
      fromQueryString(name).orElse(fromFormUrlEncoded(name, request.body))
    }
  }

  /**
   * Tries to extract the value from query string and then from Json body.
   */
  implicit val jsonExtractor = new RequestExtractor[JsValue] {
    def extractString(name: String)(implicit request: Request[JsValue]) = {
      fromQueryString(name).orElse(fromJson(name, request.body))
    }
  }

  /**
   * Tries to extract the value from query string and then from Xml body.
   */
  implicit val xmlExtractor = new RequestExtractor[NodeSeq] {
    def extractString(name: String)(implicit request: Request[NodeSeq]) = {
      fromQueryString(name).orElse(fromXml(name, request.body))
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
  def extractString(name: String): Option[String] = extractor.extractString(name)(request)
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
  def apply[B](implicit request: Request[B], extractor: RequestExtractor[B]) = {
    new ExtractableRequest(request)
  }

  /**
   * Converts a `Request` to a `ExtractableRequest` instance.
   *
   * @param request The request to convert.
   * @param extractor The extractor to extract the value.
   * @tparam B The type of the request body.
   * @return An extractable request.
   */
  implicit def convertExplicit[B](request: Request[B])(implicit extractor: RequestExtractor[B]): ExtractableRequest[B] = {
    new ExtractableRequest(request)
  }

  /**
   * Converts an implicit `Request` to a `ExtractableRequest` instance.
   *
   * @param request The request to convert.
   * @param extractor The extractor to extract the value.
   * @tparam B The type of the request body.
   * @return An extractable request.
   */
  implicit def convertImplicit[B](implicit request: Request[B], extractor: RequestExtractor[B]): ExtractableRequest[B] = {
    this(request, extractor)
  }
}
