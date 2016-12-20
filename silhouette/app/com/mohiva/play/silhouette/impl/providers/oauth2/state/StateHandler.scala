package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.util.ExtractableRequest
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Handles state for different purposes.
 */
trait StateHandler {

  /**
   * Validates the state.
   *
   * @param request The request instance to get additional data to validate against.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return True if the state is valid, false otherwise.
   */
  def validate[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Boolean]

  /**
   * Gets the state params the handler can handle.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The state params the handler can handle.
   */
  def state(implicit ec: ExecutionContext): Future[Map[String, String]]

  /**
   * Unserializes the state handler from the state param.
   *
   * @param request The request instance to get additional data to validate against.
   * @param ec The execution context to handle the asynchronous operations.
   * @return The state params the handler can handle.
   */
  def unserialize[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[StateHandler]
}

object StateHandler {
  /**
   * The error messages.
   */
  val ClientStateDoesNotExists = "[Silhouette][CookieState] State cookie doesn't exists for name: %s"
  val ProviderStateDoesNotExists = "[Silhouette][CookieState] Couldn't find state in request for param: %s"
  val StateIsNotEqual = "[Silhouette][CookieState] State isn't equal"
  val StateIsExpired = "[Silhouette][CookieState] State is expired"
  val InvalidJson = "[Silhouette][CookieState] Cannot parse invalid Json: %s"
  val InvalidStateFormat = "[Silhouette][CookieState] Cannot build OAuth2State because of invalid Json format: %s"
  val InvalidCookieSignature = "[Silhouette][CookieState] Invalid cookie signature"
}

