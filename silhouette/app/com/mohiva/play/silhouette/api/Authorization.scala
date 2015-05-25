/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2015 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.api

import play.api.i18n.Messages
import play.api.mvc.RequestHeader
import scala.concurrent.{ ExecutionContext, Future }

/**
 * A trait to define Authorization objects that let you hook
 * an authorization implementation in secured endpoints.
 *
 * @tparam I The type of the identity.
 */
trait Authorization[I <: Identity] {

  /**
   * Checks whether the user is authorized to execute an endpoint or not.
   *
   * @param identity The identity to check for.
   * @param request The current request header.
   * @param messages The messages for the current language.
   * @return True if the user is authorized, false otherwise.
   */
  def isAuthorized(identity: I)(implicit request: RequestHeader, messages: Messages): Future[Boolean]
}

/**
 * The companion object.
 */
object Authorization {

  /**
   * Defines additional methods on an `Authorization` instance.
   *
   * @param self The `Authorization` instance on which the additional methods should be defined.
   * @param ec The execution context to handle the asynchronous operations.
   */
  implicit final class RichAuthorization[I <: Identity](self: Authorization[I])(implicit ec: ExecutionContext) {

    /**
     * Performs a logical negation on an `Authorization` result.
     *
     * @return An `Authorization` which performs a logical negation on an `Authorization` result.
     */
    def unary_! : Authorization[I] = new Authorization[I] {
      def isAuthorized(identity: I)(
        implicit request: RequestHeader, messages: Messages): Future[Boolean] = {
        self.isAuthorized(identity).map(x => !x)
      }
    }

    /**
     * Performs a logical AND operation with two `Authorization` instances.
     *
     * @param authorization The right hand operand.
     * @return An authorization which performs a logical AND operation with two `Authorization` instances.
     */
    def &&(authorization: Authorization[I]): Authorization[I] = new Authorization[I] {
      def isAuthorized(identity: I)(implicit request: RequestHeader, messages: Messages): Future[Boolean] = {
        val leftF = self.isAuthorized(identity)
        val rightF = authorization.isAuthorized(identity)
        for {
          left <- leftF
          right <- rightF
        } yield left && right
      }
    }

    /**
     * Performs a logical OR operation with two `Authorization` instances.
     *
     * @param authorization The right hand operand.
     * @return An authorization which performs a logical OR operation with two `Authorization` instances.
     */
    def ||(authorization: Authorization[I]): Authorization[I] = new Authorization[I] {
      def isAuthorized(identity: I)(implicit request: RequestHeader, messages: Messages): Future[Boolean] = {
        val leftF = self.isAuthorized(identity)
        val rightF = authorization.isAuthorized(identity)
        for {
          left <- leftF
          right <- rightF
        } yield left || right
      }
    }
  }
}
