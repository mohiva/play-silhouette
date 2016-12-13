/**
 * Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.impl.providers.oauth2.state

import javax.inject.Inject

import com.mohiva.play.silhouette.api.crypto.{ Base64, CookieSigner }
import com.mohiva.play.silhouette.api.util.{ Clock, ExtractableRequest, IDGenerator }
import com.mohiva.play.silhouette.impl.exceptions.OAuth2StateException
import com.mohiva.play.silhouette.impl.providers.OAuth2Provider._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.CookieStateProvider._
import com.mohiva.play.silhouette.impl.providers.{ OAuth2State, OAuth2StateProvider }
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc.{ Cookie, RequestHeader, Result }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

/**
 * The cookie state companion object.
 */
object CookieState {

  /**
   * Converts the [[CookieState]]] to Json and vice versa.
   */
  implicit val jsonFormat = Json.format[CookieState]

  /**
   * Returns a serialized value of the state.
   *
   * @param state The state to serialize.
   * @param cookieSigner The cookie signer implementation.
   * @return A serialized value of the state.
   */
  def serialize(state: CookieState, cookieSigner: CookieSigner) = {
    cookieSigner.sign(Base64.encode(Json.toJson(state)))
  }

  /**
   * Unserializes the state.
   *
   * @param str The string representation of the state.
   * @param cookieSigner The cookie signer implementation.
   * @return Some state on success, otherwise None.
   */
  def unserialize(str: String, cookieSigner: CookieSigner): Try[CookieState] = {
    cookieSigner.extract(str) match {
      case Success(data) => buildState(Base64.decode(data))
      case Failure(e)    => Failure(new OAuth2StateException(InvalidCookieSignature, e))
    }
  }

  /**
   * Builds the state from Json.
   *
   * @param str The string representation of the state.
   * @return A state on success, otherwise a failure.
   */
  private def buildState(str: String): Try[CookieState] = {
    Try(Json.parse(str)) match {
      case Success(json) => json.validate[CookieState].asEither match {
        case Left(error)          => Failure(new OAuth2StateException(InvalidStateFormat.format(error)))
        case Right(authenticator) => Success(authenticator)
      }
      case Failure(error) => Failure(new OAuth2StateException(InvalidJson.format(str), error))
    }
  }
}

/**
 * A state which gets persisted in a cookie.
 *
 * This is to prevent the client for CSRF attacks as described in the OAuth2 RFC.
 *
 * @see https://tools.ietf.org/html/rfc6749#section-10.12
 * @param expirationDate The expiration time.
 * @param value A value that binds the request to the user-agent's authenticated state.
 */
case class CookieState(expirationDate: DateTime, value: String, userState: Map[String, String] = Map.empty) extends OAuth2State {

  /**
   * Checks if the state is expired. This is an absolute timeout since the creation of
   * the state.
   *
   * @return True if the state is expired, false otherwise.
   */
  override def isExpired = expirationDate.isBeforeNow
}

/**
 * Saves the state in a cookie.
 *
 * @param settings The state settings.
 * @param idGenerator The ID generator used to create the state value.
 * @param cookieSigner The cookie signer implementation.
 * @param clock The clock implementation.
 */
class CookieStateProvider @Inject() (
  settings: CookieStateSettings,
  idGenerator: IDGenerator,
  cookieSigner: CookieSigner,
  clock: Clock)
  extends OAuth2StateProvider {

  /**
   * The type of the state implementation.
   */
  override type State = CookieState

  /**
   * Builds the state.
   *
   * @param request The request.
   * @param ec The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The build state.
   */
  override def build[B](userState: Map[String, String] = Map.empty)(implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[CookieState] = {
    idGenerator.generate.map { id =>
      CookieState(clock.now.plusSeconds(settings.expirationTime.toSeconds.toInt), id, userState)
    }
  }

  /**
   * Validates the provider and the client state.
   *
   * @param request The request.
   * @param ec The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The state on success, otherwise an failure.
   */
  override def validate[B](implicit request: ExtractableRequest[B], ec: ExecutionContext): Future[CookieState] = {
    Future.fromTry(clientState.flatMap(clientState => providerState.flatMap(providerState =>
      if (clientState != providerState) Failure(new OAuth2StateException(StateIsNotEqual))
      else if (clientState.isExpired) Failure(new OAuth2StateException(StateIsExpired))
      else Success(clientState)
    )))
  }

  /**
   * Sends a cookie to the client containing the serialized state.
   *
   * @param result The result to send to the client.
   * @param state The state to publish.
   * @param request The request.
   * @tparam B The type of the request body.
   * @return The result to send to the client.
   */
  override def publish[B](result: Result, state: State)(implicit request: ExtractableRequest[B]): Result = {
    result.withCookies(Cookie(
      name = settings.cookieName,
      value = serialize(state),
      maxAge = Some(settings.expirationTime.toSeconds.toInt),
      path = settings.cookiePath,
      domain = settings.cookieDomain,
      secure = settings.secureCookie,
      httpOnly = settings.httpOnlyCookie))
  }

  /**
   * Returns a serialized value of the state.
   *
   * @param state The state to serialize.
   * @return A serialized value of the state.
   */
  override def serialize(state: State): String = CookieState.serialize(state, cookieSigner)

  /**
   * Gets the state from cookie.
   *
   * @param request The request header.
   * @return The OAuth2 state on success, otherwise a failure.
   */
  private def clientState(implicit request: RequestHeader): Try[CookieState] = {
    request.cookies.get(settings.cookieName) match {
      case Some(cookie) => CookieState.unserialize(cookie.value, cookieSigner)
      case None         => Failure(new OAuth2StateException(ClientStateDoesNotExists.format(settings.cookieName)))
    }
  }

  /**
   * Gets the state from request the after the provider has redirected back from the authorization server
   * with the access code.
   *
   * @param request The request.
   * @tparam B The type of the request body.
   * @return The OAuth2 state on success, otherwise a failure.
   */
  private def providerState[B](implicit request: ExtractableRequest[B]): Try[CookieState] = {
    request.extractString(State) match {
      case Some(state) => CookieState.unserialize(state, cookieSigner)
      case _           => Failure(new OAuth2StateException(ProviderStateDoesNotExists.format(State)))
    }
  }
}

/**
 * The CookieStateProvider companion object.
 */
object CookieStateProvider {

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
case class CookieStateSettings(
  cookieName: String = "OAuth2State",
  cookiePath: String = "/",
  cookieDomain: Option[String] = None,
  secureCookie: Boolean = true,
  httpOnlyCookie: Boolean = true,
  expirationTime: FiniteDuration = 5 minutes)
