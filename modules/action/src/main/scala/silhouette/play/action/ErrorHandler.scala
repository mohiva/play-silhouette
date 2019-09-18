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
package silhouette.play.action

import com.typesafe.scalalogging.LazyLogging
import play.api.http.{ ContentTypes, Status }
import play.api.i18n.{ I18nSupport, Messages }
import play.api.libs.json.Json
import play.api.mvc._
import silhouette.Identity

import scala.concurrent._

/**
 * Silhouette error handler.
 */
sealed trait ErrorHandler

/**
 * Handles errors when a user is not authenticated.
 */
trait NotAuthenticatedErrorHandler extends ErrorHandler {

  /**
   * Called when a user is not authenticated.
   *
   * As defined by RFC 2616, the status code of the response should be 401 Unauthorized.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def onNotAuthenticated[B](implicit request: Request[B]): Future[Result]
}

/**
 * Handles errors when a user is authenticated but not authorized.
 *
 * @tparam I The type of the identity.
 */
trait NotAuthorizedErrorHandler[I <: Identity] extends ErrorHandler {

  /**
   * Called when a user is authenticated but not authorized.
   *
   * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def onNotAuthorized[B](identity: I)(implicit request: Request[B]): Future[Result]
}

/**
 * Handles not authenticated requests in a default way.
 */
trait DefaultNotAuthenticatedErrorHandler
  extends NotAuthenticatedErrorHandler
  with DefaultErrorHandler
  with I18nSupport
  with LazyLogging {

  /**
   * @inheritdoc
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def onNotAuthenticated[B](implicit request: Request[B]): Future[Result] = {
    logger.debug("[Silhouette] Unauthenticated user trying to access '%s'".format(request.uri))
    produceResponse(Unauthorized, Messages("silhouette.not.authenticated"))
  }
}

/**
 * Handles not authorized requests in a default way.
 *
 * @tparam I The type of the identity.
 */
trait DefaultNotAuthorizedErrorHandler[I <: Identity]
  extends NotAuthorizedErrorHandler[I]
  with DefaultErrorHandler
  with I18nSupport
  with LazyLogging {

  /**
   * @inheritdoc
   *
   * @param identity The not authorized identity.
   * @param request  The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def onNotAuthorized[B](identity: I)(implicit request: Request[B]): Future[Result] = {
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
   * Returns an adequate response considering the required status code, the user-friendly message, and the
   * requested media type.
   *
   * @param status The status code of the response.
   * @param msg The user-friendly message.
   * @param request The request header.
   */
  protected def produceResponse[B, S <: Status](status: S, msg: String)(
    implicit
    request: Request[B]
  ): Future[Result] = {
    import Codec._
    Future.successful(render {
      case Accepts.Html() => status(toHtmlError(msg)).as(HTML)
      // This will also be the default content type if the client doesn't request a specific media type.
      case Accepts.Json() => status(toJsonError(msg))
      case Accepts.Xml()  => status(toXmlError(msg))
      case _              => status(toPlainTextError(msg))
      // The correct HTTP status code must be returned in any situation.
      // The response format will default to plain text in case the request does not specify one of known
      // media types. The user agent is responsible for inspecting the response headers as specified in
      // [[https://tools.ietf.org/html/rfc2616#section-10.4.7 RFC 2616]].
    })
  }

  protected def toHtmlError(message: String) =
    s"<html><head><title>$message</title></head><body>$message</body></html>"

  protected def toJsonError(message: String) =
    Json.obj("success" -> false, "message" -> message)

  protected def toXmlError(message: String) =
    <response><success>false</success><message>{ message }</message></response>

  protected def toPlainTextError(message: String) = message
}
