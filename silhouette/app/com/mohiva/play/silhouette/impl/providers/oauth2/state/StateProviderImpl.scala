package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import play.api.libs.json.Json
import play.api.mvc.{ Cookie, Result }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Created by sahebmotiani on 20/12/2016.
 */
class StateProviderImpl @Inject() (
  override val handlers: Set[StateHandler] = Set.empty,
  settings: StateProviderImplSettings) extends StateProvider {

  /**
   * The concrete instance of the state provider.
   */
  type Self = StateProviderImpl

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
  override def withHandler(handler: StateHandler): Self = new StateProviderImpl(handlers = handlers ++ Set(handler), settings)

  /**
   * Serializes the state handlers into a single state value which can be passed with the state param.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The serialized state as string.
   */
  override def serialize(implicit ec: ExecutionContext): Future[String] = {
    handlers.foldLeft(Future(Map.empty[String, Map[String, String]]): Future[Map[String, Map[String, String]]])((a, k) => {
      for {
        aParam <- a
        kParam <- k.state
      } yield (aParam + (k.toString -> kParam))
    }).map(state => Base64.encode(Json.toJson(state)))
  }

  /**
   * Unserializes the state handlers from the state param.
   *
   * @param request The request to read the value of the state param from.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The list of state handlers on success, an error on failure.
   */
  override def unserialize[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Set[StateHandler]] = {
    Future.sequence(handlers.map(handler => handler.unserialize))
  }

  /**
   * Validates the provider state and the client state,
   * in turn calls validate for every state handler configured for the state provider
   * @param request The request to read the value of the state param from.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return true if validated else false
   */
  override def validate[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Boolean] = {
    Future.sequence(handlers.map(_.validate)).map(bools => bools.forall(k => k))
  }

  /**
   * Publishes the state to the client.
   *
   * @param result  The result to send to the client.
   * @param state   The state to publish.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def publish[B](result: Result, state: String)(implicit request: ExtractableRequest[B]): Result = {
    result.withCookies(Cookie(
      name = settings.cookieName,
      value = state,
      maxAge = Some(settings.expirationTime.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie))
  }
}

/**
 * The settings for the cookie state.
 *
 * @param cookieName The cookie name.
 * @param cookiePath The cookie path.
 * @param cookieDomain The cookie domain.
 * @param secureCookie Whether this cookie is secured, sent only for HTTPS requests.
 * @param httpOnlyCookie Whether this cookie is HTTP only, i.e. not accessible from client-side JavaScript code.
 * @param expirationTime State expiration. Defaults to 5 minutes which provides sufficient time to log in, but
 *                       not too much. This is a balance between convenience and security.
 */
case class StateProviderImplSettings(
  cookieName: String = "OAuth2State",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = true,
  httpOnlyCookie: Boolean = true,
  expirationTime: FiniteDuration = 5 minutes)
