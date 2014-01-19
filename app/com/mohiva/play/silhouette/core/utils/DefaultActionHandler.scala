package com.mohiva.play.silhouette.core.utils

import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc._
import scala.concurrent._

/**
 * Provides default actions used by the core as a last fallback.
 */
object DefaultActionHandler extends Controller {

  /**
   * Handles forbidden requests in a default way.
   *
   * The HTTP status code of the response will be 403 Forbidden, complying with
   * [[https://tools.ietf.org/html/rfc2616#section-10.4.4 RFC 2616]].
   *
   * @param request The request header.
   * @return A response indicating that access is forbidden.
   */
  def handleForbidden(implicit request: RequestHeader): Future[SimpleResult] = {
    produceResponse(Forbidden, "silhouette.not.authorized")
  }

  /**
   * Handles unauthorized requests in a default way.
   *
   * The HTTP status code of the response will be 401 Unauthorized, complying with
   * [[https://tools.ietf.org/html/rfc2616#section-10.4.2 RFC 2616]].
   *
   * @param request The request header.
   * @return A response indicating that user authentication is required.
   */
  def handleUnauthorized(implicit request: RequestHeader): Future[SimpleResult] = {
    produceResponse(Unauthorized, "silhouette.not.authenticated")
  }

  /**
   * Returns an adequate response considering the required status code,
   * the user-friendly message, and the requested media type.
   *
   * @param status The status code of the response.
   * @param msg The user-friendly message.
   * @param request The request header.
   */
  private def produceResponse[S <: Status](status: S, msg: String)(implicit request: RequestHeader): Future[SimpleResult] = {
    val localized = Messages(msg)
    Future.successful(render {
      case Accepts.Html() => status(toHtmlError(localized)).as(HTML)
      // This will also be the default content type if the client doesn't request a specific media type.
      case Accepts.Json() => status(toJsonError(localized))
      case Accepts.Xml() => status(toXmlError(localized))
      case _ => status(toPlainTextError(localized))
      // The correct HTTP status code must be returned in any situation.
      // The response format will default to plain text in case the request does not specify one of known
      // media types. The user agent is responsible for inspecting the response headers as specified in
      // [[https://tools.ietf.org/html/rfc2616#section-10.4.7 RFC 2616]].
    })
  }

  private def toHtmlError(message: String) =
    s"<html><head><title>$message</title></head><body>$message</body></html>"

  private def toJsonError(message: String) =
    Json.obj("success" -> false, "message" -> message)

  private def toXmlError(message: String) =
    <response><success>false</success><message>{ message }</message></response>

  private def toPlainTextError(message: String) = message
}
