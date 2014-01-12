/**
 * Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.core.providers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.{Result, RequestHeader}
import com.mohiva.play.silhouette.core.{Provider, Identity}
import play.api.i18n.Lang

/**
 * The base interface for all social providers.
 *
 * @tparam I The type of the identity.
 * @tparam A The type of the auth info.
 */
trait SocialProvider[I <: Identity, A] extends Provider {

  /**
   * Subclasses need to implement this method to populate the Identity object with profile
   * information from the service provider.
   *
   * @param authInfo The auth info received from the provider.
   * @param request The request header.
   * @param lang The current lang.
   * @return The build identity.
   */
  def buildIdentity(authInfo: A)(implicit request: RequestHeader, lang: Lang): Future[I]

  /**
   * Subclasses need to implement the authentication logic.
   *
   * This method needs to return a auth info object that then gets passed to the buildIdentity method.
   *
   * @param request The request header.
   * @return Either a Result or the auth info from the provider.
   */
  def doAuth()(implicit request: RequestHeader): Future[Either[Result, A]]

  /**
   * Authenticates the user and fills the profile information.
   *
   * Returns either an Identity if all went ok or a Result that the controller sends to the
   * browser (eg: in the case of OAuth for example where the user needs to be redirected to
   * the service provider)
   *
   * @param request The request header.
   * @param lang The current lang.
   * @return The identity.
   */
  def authenticate()(implicit request: RequestHeader, lang: Lang) = doAuth().flatMap(_.fold(
    result => Future.successful(Left(result)),
    authInfo => buildIdentity(authInfo).map(i => Right(i))))
}
