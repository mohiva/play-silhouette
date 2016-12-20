package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import play.api.libs.json.Json
import play.api.mvc.Result

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Created by sahebmotiani on 20/12/2016.
 */
class CsrfStateHandler(csrfState: Map[String, String] = Map.empty) extends StateHandler with PublishableStateHandler {

  override def validate[B](implicit request: ExtractableRequest[B], ex: ExecutionContext): Future[Boolean] = {
    unserialize.flatMap(providerState => {
      for {
        pState <- providerState.state
        cState <- this.state
      } yield (pState.equals(cState))
    })
  }

  override def state(implicit ex: ExecutionContext): Future[Map[String, String]] = Future(csrfState)

  override def publish[B](result: Result)(implicit request: ExtractableRequest[B]): Future[Result] = ???

  override def unserialize[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[CsrfStateHandler] = {
    request.extractString("state") match {
      case Some(state) => Future {
        new CsrfStateHandler(Json.parse(Base64.decode(state))
          .validate[Map[String, Map[String, String]]]
          .get(this.toString))
      }
      case None => Future.failed(new OAuth2StateException(StateHandler.ProviderStateDoesNotExists))
    }
  }

  override def toString: String = "csrfState"
}
