/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.api.services

import com.mohiva.play.silhouette.api.{ Authenticator, LoginInfo }
import play.api.mvc.{ Headers, RequestHeader, Result }

import scala.concurrent.Future

/**
 * Handles authenticators for the Silhouette module.
 *
 * @tparam T The type of the authenticator this service is responsible for.
 */
trait AuthenticatorService[T <: Authenticator] {

  /**
   * Used to add additional headers to the existing headers.
   *
   * @param existing The existing headers.
   * @param additional The additional headers to add.
   */
  case class AdditionalHeaders(existing: Headers, additional: Seq[(String, Seq[String])]) extends Headers {
    override protected val data: Seq[(String, Seq[String])] = (existing.toMap ++ additional).toSeq
  }

  /**
   * Creates a new authenticator for the specified login info.
   *
   * @param loginInfo The login info for which the authenticator should be created.
   * @param request The request header.
   * @return An authenticator.
   */
  def create(loginInfo: LoginInfo)(implicit request: RequestHeader): Future[T]

  /**
   * Retrieves the authenticator from request.
   *
   * @param request The request header.
   * @return Some authenticator or None if no authenticator could be found in request.
   */
  def retrieve(implicit request: RequestHeader): Future[Option[T]]

  /**
   * Embeds authenticator specific artifacts into the response.
   *
   * This method should be called on authenticator initialization after an identity has logged in.
   *
   * @param authenticator The authenticator instance.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  def init(authenticator: T, result: Future[Result])(implicit request: RequestHeader): Future[Result]

  /**
   * Embeds authenticator specific artifacts into the request.
   *
   * This method can be used to embed an authenticator in a existing request. This can be useful
   * in Play filters. So before executing a SecuredAction we can embed the authenticator in
   * the request to lead the action to believe that the request is a new request which contains
   * a valid authenticator.
   *
   * If an existing authenticator exists, then it will be overridden.
   *
   * @param authenticator The authenticator instance.
   * @param request The request header.
   * @return The manipulated request header.
   */
  def init(authenticator: T, request: RequestHeader): Future[RequestHeader]

  ///////////////////////////////////////////////////////////////////////////////////////////////
  //
  // Internal API
  //
  // Due the fact that the update method gets called on every subsequent request to update the
  // authenticator related data in the backing store and in the result, it isn't possible to
  // discard or renew the authenticator simultaneously. This is because the "update" method would
  // override the result created by the "renew" or "discard" method, because it will be executed
  // as last in the chain.
  //
  // As example:
  // If we discard the session in a Silhouette action then it will be removed from session. But
  // at the end the update method will embed the session again, because it gets called with the
  // result of the action.
  //
  // So we restrict the access to this methods and force the developer to use the "renew" and
  // "discard" methods on the Authenticator instance. These methods wrap the result into a marker
  // result, for which we can execute the appropriate method on this service.
  //
  ///////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Touches an authenticator.
   *
   * An authenticator can use sliding window expiration. This means that the authenticator times
   * out after a certain time if it wasn't used. So to mark an authenticator as used it will be
   * touched on every request to a Silhouette action. If an authenticator should not be touched
   * because of the fact that sliding window expiration is disabled, then it should be returned
   * on the right, otherwise it should be returned on the left. An untouched authenticator needn't
   * be updated later by the [[update]] method.
   *
   * @param authenticator The authenticator to touch.
   * @return The touched authenticator on the left or the untouched authenticator on the right.
   */
  protected[silhouette] def touch(authenticator: T): Either[T, T]

  /**
   * Updates a touched authenticator.
   *
   * If the authenticator was updated, then the updated artifacts should be embedded into the response.
   * This method gets called on every subsequent request if an identity accesses a Silhouette action,
   * expect the authenticator was not touched.
   *
   * @param authenticator The authenticator to update.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  protected[silhouette] def update(authenticator: T, result: Future[Result])(implicit request: RequestHeader): Future[Result]

  /**
   * Renews the expiration of an authenticator.
   *
   * Based on the implementation, the renew method should revoke the given authenticator first, before
   * creating a new one. If the authenticator was updated, then the updated artifacts should be embedded
   * into the response.
   *
   * @param authenticator The authenticator to renew.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The original or a manipulated result.
   */
  protected[silhouette] def renew(authenticator: T, result: Future[Result])(implicit request: RequestHeader): Future[Result]

  /**
   * Manipulates the response and removes authenticator specific artifacts before sending it to the client.
   *
   * @param authenticator The authenticator instance.
   * @param result The result to manipulate.
   * @param request The request header.
   * @return The manipulated result.
   */
  protected[silhouette] def discard(authenticator: T, result: Future[Result])(implicit request: RequestHeader): Future[Result]
}

/**
 * The companion object.
 */
object AuthenticatorService {

  /**
   * The error messages.
   */
  val CreateError = "[Silhouette][%s] Could not create authenticator for login info: %s"
  val RetrieveError = "[Silhouette][%s] Could not retrieve authenticator"
  val InitError = "[Silhouette][%s] Could not init authenticator: %s"
  val UpdateError = "[Silhouette][%s] Could not update authenticator: %s"
  val RenewError = "[Silhouette][%s] Could not renew authenticator: %s"
  val DiscardError = "[Silhouette][%s] Could not discard authenticator: %s"
}
