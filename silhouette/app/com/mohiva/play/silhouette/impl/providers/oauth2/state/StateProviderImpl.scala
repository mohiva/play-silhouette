package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import play.api.libs.json.Json
import scala.concurrent.{ ExecutionContext, Future }

/**
 * Created by sahebmotiani on 20/12/2016.
 */
class StateProviderImpl @Inject() (
  override val handlers: Set[SocialStateHandler] = Set.empty) extends StateProvider {

  /**
   * The concrete instance of the state provider.
   */
  type Self = StateProviderImpl

  /**
   * Creates a copy of the state provider with a new set of handlers.
   *
   * There exists two types of state handlers. The first type are global state handlers which can be configured
   * by the user with the help of a configuration mechanism or through dependency injection. And there a local
   * state handlers which are provided by the application itself. This method exists to handle the last type of
   * state handlers, because it allows to extend the list of user defined state handlers from inside the library.
   *
   * @param handlers The new set of handlers.
   * @return A new state provider with a new set of handlers.
   */
  override def withHandler(handlers: Set[SocialStateHandler]): Self = new StateProviderImpl(handlers)

  /**
   * Serializes the state handlers into a single state value which can be passed with the state param.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The serialized state as string.
   */
  override def serialize(stateMap: Map[String, Map[String, String]])(implicit ec: ExecutionContext): Future[String] = {
    handlers.foldLeft(Future(Map.empty[String, Map[String, String]]): Future[Map[String, Map[String, String]]])((a, k) => {
      for {
        aParam <- a
        kParam <- Future.successful(k.state)
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
  override def unserialize[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Map[String, Map[String, String]]] = {
    val states = request.extractString("state") match {
      case Some(state) => Future(Json.parse(Base64.decode(state)).validate[Map[String, Map[String, String]]].get)
      case None        => Future.failed(new OAuth2StateException(SocialStateHandler.ProviderStateDoesNotExists))
    }
    for {
      stateMap <- states
      validated <- validateHelper(stateMap)
    } yield stateMap
  }

  private def validateHelper[B](stateMap: Map[String, Map[String, String]])(implicit
    request: ExtractableRequest[B],
    ec: ExecutionContext): Future[Boolean] = {
    Future.sequence(
      handlers.map(handler => handler.fromState(stateMap.get(handler.toString).getOrElse(Map.empty)).validate(stateMap))
    ).map(bools => bools.forall(k => k))
  }

  override def build[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Map[String, Map[String, String]]] = {
    handlers.filter(_.isInstanceOf[PublishableStateHandler]).map(_.asInstanceOf[PublishableStateHandler]).
      foldLeft(Future(Map.empty[String, Map[String, String]]))((aF, handler) => {
        for {
          a <- aF
          state <- handler.build
        } yield (a + (handler.toString -> state))
      })
  }
}
