package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import play.api.libs.json.Json

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Created by sahebmotiani on 20/12/2016.
 */
class UserSocialStateHandler(val userState: Map[String, String]) extends SocialStateHandler {

  override def validate[B](stateMap: Map[String, Map[String, String]])(implicit request: ExtractableRequest[B], ex: ExecutionContext): Future[Boolean] = Future.successful(true)

  override def state(implicit ec: ExecutionContext): Map[String, String] = userState

  override def fromState(state: Map[String, String]): SocialStateHandler = new UserSocialStateHandler(state)

  override def toString: String = "userState"
}
