package com.mohiva.play.silhouette.impl.providers.oauth2.state

import com.mohiva.play.silhouette.api.crypto.{ Base64, CookieSigner }
import com.mohiva.play.silhouette.api.util.{ Clock, ExtractableRequest, IDGenerator }
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import play.api.mvc.{ Cookie, Result }

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import SocialStateHandler._
import com.google.inject.Inject

class CsrfSocialStateHandler @Inject() (
  settings: CsrfStateSettings,
  idGenerator: IDGenerator,
  cookieSigner: CookieSigner,
  clock: Clock,
  s: Map[String, String]) extends SocialStateHandler with PublishableStateHandler {

  override def validate[B](stateMap: Map[String, Map[String, String]])(implicit
    request: ExtractableRequest[B],
    ec: ExecutionContext): Future[Boolean] = {
    Future.fromTry(clientState.flatMap(cS => {
      println(cS)
      println(this.state)
      if (cS != this.state) Failure(new OAuth2StateException(StateIsNotEqual))
      else Success(true)
    }
    ))
  }

  override def state(implicit ec: ExecutionContext): Map[String, String] = {
    this.s
  }

  override def publish[B](result: Result, state: Option[Map[String, String]])(implicit request: ExtractableRequest[B]): Result = {
    result.withCookies(Cookie(
      name = settings.cookieName,
      value = state.get.get("token").getOrElse("Error"),
      maxAge = Some(settings.expirationTime.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie))
  }

  override def build[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[Map[String, String]] = {
    idGenerator.generate.map { id =>
      Map("token" -> cookieSigner.sign(id))
    }
  }

  override def fromState(state: Map[String, String]): SocialStateHandler = new CsrfSocialStateHandler(settings, idGenerator, cookieSigner, clock, state)

  private def clientState[B](implicit request: ExtractableRequest[B]): Try[Map[String, String]] = {
    request.cookies.get(settings.cookieName) match {
      case Some(cookie) => cookieSigner.extract(cookie.value).map(state => Map("token" -> state))
      case None         => Failure(new OAuth2StateException(ClientStateDoesNotExists.format(settings.cookieName)))
    }
  }

  override def toString: String = "csrfState"
}

case class CsrfStateSettings(
  cookieName: String = "OAuth2State",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = true,
  httpOnlyCookie: Boolean = true,
  expirationTime: FiniteDuration = 5 minutes)
