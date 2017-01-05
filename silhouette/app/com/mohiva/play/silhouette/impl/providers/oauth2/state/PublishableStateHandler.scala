package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.util.ExtractableRequest
import play.api.mvc.Result

import scala.concurrent.{ ExecutionContext, Future }

/**
 * A state handler which can publish its internal state to the client.
 *
 * Some state handlers, like the CSRF state handler, needs the ability to publish state to a cookie. So if you have
 * such a state handler, then mixin this trait, to publish the state to the client.
 */
trait PublishableStateHandler {
  self: SocialStateHandler =>

  def build[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Map[String, String]]
  /**
   * Publishes the state to the client.
   *
   * @param result  The result to send to the client.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def publish[B](result: Result, state: Option[Map[String, String]])(implicit request: ExtractableRequest[B]): Result
}
