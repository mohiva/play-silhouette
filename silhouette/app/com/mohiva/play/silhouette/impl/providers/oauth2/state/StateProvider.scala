package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.util.ExtractableRequest
import play.api.mvc.Result

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Provides a way to handle different types of state for provides that allow a state param.
 *
 * Some authentication protocols defines a state param which can be used to transport some
 * state to an authentication provider. The authentication provider sends this state back
 * to the application, after the authentication to the provider was granted.
 *
 * The state parameter can be used for different things. Silhouette provides two state handlers
 * out of the box. One state handler can transport additional user state to the provider. This
 * could be an URL were the user should be redirected after authentication to the provider, or
 * any other per-authentication based state. An other important state handler protects the
 * application for CSRF attacks.
 */
trait StateProvider {

  /**
   * The concrete instance of the state provider.
   */
  type Self <: StateProvider

  /**
   * The handler configured for this provider
   */
  val handlers: Set[StateHandler] = Set.empty

  /**
   * Creates a copy of the state provider with a new handler added.
   *
   * There exists two types of state handlers. The first type are global state handlers which can be configured
   * by the user with the help of a configuration mechanism or through dependency injection. And there a local
   * state handlers which are provided by the application itself. This method exists to handle the last type of
   * state handlers, because it allows to extend the list of user defined state handlers from inside the library.
   *
   * @param handler The handler to add.
   * @return A new state provider with a new handler added.
   */
  def withHandler(handler: StateHandler): Self

  /**
   * Serializes the state handlers into a single state value which can be passed with the state param.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The serialized state as string.
   */
  def serialize(implicit ec: ExecutionContext): Future[String]

  /**
   * Unserializes the state handlers from the state param.
   *
   * @param request The request to read the value of the state param from.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The list of state handlers on success, an error on failure.
   */
  def unserialize[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Set[StateHandler]]

  /**
   * Validates the provider state and the client state,
   * in turn calls validate for every state handler configured for the state provider
   * @param request The request to read the value of the state param from.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return true if validated else false
   */
  def validate[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Boolean]

  /**
   * Publishes the state to the client.
   *
   * @param result  The result to send to the client.
   * @param state   The state to publish.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  def publish[B](result: Result, state: String)(implicit request: ExtractableRequest[B]): Result
}
