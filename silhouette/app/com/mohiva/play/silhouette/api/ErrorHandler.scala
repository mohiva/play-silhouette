/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.api

import com.mohiva.play.silhouette.api.exceptions.{ NotAuthorizedException, NotAuthenticatedException }
import play.api.http.{ HeaderNames, ContentTypes, Status }
import play.api.i18n.{ I18nSupport, Messages }
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent._

/**
 * Silhouette error handler.
 */
sealed trait ErrorHandler {

  /**
   * Calls the error handler methods based on a caught exception.
   *
   * @param request The request header.
   * @return A partial function which maps an exception to a Play result.
   */
  def exceptionHandler(implicit request: RequestHeader): PartialFunction[Throwable, Future[Result]]
}

/**
 * Handles errors when a user is not authenticated.
 */
trait NotAuthenticatedErrorHandler extends ErrorHandler {

  /**
   * Exception handler which translates an [com.mohiva.play.silhouette.api.exceptions.NotAuthenticatedException]]
   * into a 401 Unauthorized result.
   *
   * @param request The request header.
   * @return A partial function which maps an exception to a Play result.
   */
  def exceptionHandler(implicit request: RequestHeader): PartialFunction[Throwable, Future[Result]] = {
    case e: NotAuthenticatedException => onNotAuthenticated
  }

  /**
   * Called when a user is not authenticated.
   *
   * As defined by RFC 2616, the status code of the response should be 401 Unauthorized.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  def onNotAuthenticated(implicit request: RequestHeader): Future[Result]
}

/**
 * Handles errors when a user is authenticated but not authorized.
 */
trait NotAuthorizedErrorHandler extends ErrorHandler {

  /**
   * Exception handler which translates an [[com.mohiva.play.silhouette.api.exceptions.NotAuthorizedException]]
   * into a 403 Forbidden result.
   *
   * @param request The request header.
   * @return A partial function which maps an exception to a Play result.
   */
  def exceptionHandler(implicit request: RequestHeader): PartialFunction[Throwable, Future[Result]] = {
    case e: NotAuthorizedException => onNotAuthorized
  }

  /**
   * Called when a user is authenticated but not authorized.
   *
   * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  def onNotAuthorized(implicit request: RequestHeader): Future[Result]
}

/**
 * Handles not authenticated requests in a default way.
 */
trait DefaultNotAuthenticatedErrorHandler
  extends NotAuthenticatedErrorHandler
  with DefaultErrorHandler
  with I18nSupport
  with Logger {

  /**
   * @inheritdoc
   *
   * @param request The request header.
   * @return A partial function which maps an exception to a Play result.
   */
  override def exceptionHandler(implicit request: RequestHeader) = {
    case e: NotAuthenticatedException =>
      logger.info(e.getMessage, e)
      super.exceptionHandler(request)(e)
  }

  /**
   * @inheritdoc
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthenticated(implicit request: RequestHeader) = {
    logger.debug("[Silhouette] Unauthenticated user trying to access '%s'".format(request.uri))
    produceResponse(Unauthorized, Messages("silhouette.not.authenticated"))
  }
}

/**
 * Handles not authorized requests in a default way.
 */
trait DefaultNotAuthorizedErrorHandler
  extends NotAuthorizedErrorHandler
  with DefaultErrorHandler
  with I18nSupport
  with Logger {

  /**
   * @inheritdoc
   *
   * @param request The request header.
   * @return A partial function which maps an exception to a Play result.
   */
  override def exceptionHandler(implicit request: RequestHeader) = {
    case e: NotAuthorizedException =>
      logger.info(e.getMessage, e)
      super.exceptionHandler(request)(e)
  }

  /**
   * @inheritdoc
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthorized(implicit request: RequestHeader) = {
    logger.debug("[Silhouette] Unauthorized user trying to access '%s'".format(request.uri))
    produceResponse(Forbidden, Messages("silhouette.not.authorized"))
  }
}

/**
 * Provides the base implementation for the default error handlers.
 */
trait DefaultErrorHandler
  extends Results
  with Status
  with ContentTypes
  with RequestExtractors
  with Rendering {

  /**
   * Returns an adequate response considering the required status code,
   * the user-friendly message, and the requested media type.
   *
   * @param status The status code of the response.
   * @param msg The user-friendly message.
   * @param request The request header.
   */
  protected def produceResponse[S <: Status](status: S, msg: String)(implicit request: RequestHeader): Future[Result] = {
    import Codec._
    Future.successful(render {
      case Accepts.Html() => status(toHtmlError(msg)).as(HTML).withHeaders(HeaderNames.CONTENT_TYPE -> ContentTypes.HTML)
      // This will also be the default content type if the client doesn't request a specific media type.
      case Accepts.Json() => status(toJsonError(msg)).withHeaders(HeaderNames.CONTENT_TYPE -> ContentTypes.JSON)
      case Accepts.Xml()  => status(toXmlError(msg)).withHeaders(HeaderNames.CONTENT_TYPE -> ContentTypes.XML)
      case _              => status(toPlainTextError(msg)).withHeaders(HeaderNames.CONTENT_TYPE -> ContentTypes.TEXT)
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
