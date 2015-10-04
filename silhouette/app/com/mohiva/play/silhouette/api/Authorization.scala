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

import play.api.mvc.Request
import scala.concurrent.{ ExecutionContext, Future }

/**
 * A trait to define Authorization objects that let you hook
 * an authorization implementation in secured endpoints.
 *
 * @tparam I The type of the identity.
 * @tparam A The type of the authenticator.
 */
trait Authorization[I <: Identity, A <: Authenticator] {

  /**
   * Checks whether the user is authorized to execute an endpoint or not.
   *
   * @param identity The current identity instance.
   * @param authenticator The current authenticator instance.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return True if the user is authorized, false otherwise.
   */
  def isAuthorized[B](identity: I, authenticator: A)(implicit request: Request[B]): Future[Boolean]
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
  implicit final class RichAuthorization[I <: Identity, A <: Authenticator](self: Authorization[I, A])(
    implicit ec: ExecutionContext) {

    /**
     * Performs a logical negation on an `Authorization` result.
     *
     * @return An `Authorization` which performs a logical negation on an `Authorization` result.
     */
    def unary_! : Authorization[I, A] = new Authorization[I, A] {
      def isAuthorized[B](identity: I, authenticator: A)(
        implicit request: Request[B]): Future[Boolean] = {

        self.isAuthorized(identity, authenticator).map(x => !x)
      }
    }

    /**
     * Performs a logical AND operation with two `Authorization` instances.
     *
     * @param authorization The right hand operand.
     * @return An authorization which performs a logical AND operation with two `Authorization` instances.
     */
    def &&(authorization: Authorization[I, A]): Authorization[I, A] = new Authorization[I, A] {
      def isAuthorized[B](identity: I, authenticator: A)(
        implicit request: Request[B]): Future[Boolean] = {

        val leftF = self.isAuthorized(identity, authenticator)
        val rightF = authorization.isAuthorized(identity, authenticator)
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
    def ||(authorization: Authorization[I, A]): Authorization[I, A] = new Authorization[I, A] {
      def isAuthorized[B](identity: I, authenticator: A)(
        implicit request: Request[B]): Future[Boolean] = {

        val leftF = self.isAuthorized(identity, authenticator)
        val rightF = authorization.isAuthorized(identity, authenticator)
        for {
          left <- leftF
          right <- rightF
        } yield left || right
      }
    }
  }
}
