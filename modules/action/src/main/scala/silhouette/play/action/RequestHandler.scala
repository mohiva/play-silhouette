/**
 * Licensed to the Minutemen Group under one or more contributor license
 * agreements. See the COPYRIGHT file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silhouette.play.action

import com.typesafe.scalalogging.LazyLogging
import play.api.mvc.{ AnyContent, Request, Result }
import silhouette._
import silhouette.play.http.PlayRequestPipeline
import silhouette.provider.RequestProvider

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.higherKinds

/**
 * A result which can transport a result as also additional data through the request handler process.
 *
 * @param result A Play Framework result.
 * @param data Additional data to transport in the result.
 * @tparam T The type of the data.
 */
case class HandlerResult[+T](result: Result, data: Option[T] = None)

/**
 * Base implementation for building request handlers.
 *
 * The base implementations to handle secured endpoints are encapsulated into request handlers which
 * can execute an arbitrary block of code and must return a HandlerResult. This HandlerResult consists
 * of a normal Play result and arbitrary additional data which can be transported out of these handlers.
 *
 * @tparam I The type of the identity.
 * @tparam R The type of the request.
 */
trait RequestHandlerBuilder[I <: Identity, +R[_]]
  extends ExecutionContextProvider with LazyLogging {

  /**
   * The execution context to handle the asynchronous operations.
   */
  implicit lazy val ec: ExecutionContext = environment.ec

  /**
   * The environment instance to handle the request.
   */
  val environment: Environment[I]

  /**
   * Constructs a request handler with default content.
   *
   * @param block The block of code to invoke.
   * @param request The current request.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  final def apply[T](block: R[AnyContent] => Future[HandlerResult[T]])(
    implicit
    request: Request[AnyContent]
  ): Future[HandlerResult[T]] = {
    invokeBlock(block)
  }

  /**
   * Constructs a request handler with the content of the given request.
   *
   * @param request The current request.
   * @param block The block of code to invoke.
   * @tparam B The type of the request body.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  final def apply[B, T](request: Request[B])(block: R[B] => Future[HandlerResult[T]]): Future[HandlerResult[T]] = {
    invokeBlock(block)(request)
  }

  /**
   * Invoke the block.
   *
   * This is the main method that an request handler has to implement.
   *
   * @param block The block of code to invoke.
   * @param request The current request.
   * @tparam B The type of the request body.
   * @tparam T The type of the data included in the handler result.
   * @return A handler result.
   */
  def invokeBlock[B, T](block: R[B] => Future[HandlerResult[T]])(implicit request: Request[B]): Future[HandlerResult[T]]

  /**
   * Handles the authentication process with the request providers.
   *
   * The method authenticates a request against a list of several request providers. If a request provider from the
   * list is able to authenticate the request, then it returns the [[Authenticated]] state. Otherwise it returns None.
   *
   * @param request The current request.
   * @tparam B The type of the request body.
   * @return Some authenticated state on success, otherwise None.
   */
  protected def handleAuthentication[B](
    request: PlayRequestPipeline[B]
  ): Future[Option[(I, Credentials, LoginInfo)]] = {
    type Provider = RequestProvider[Request[_], I]
    def next(remaining: List[Provider]) =
      if (remaining.isEmpty) Future.successful(None) else auth(remaining)

    def auth(providers: List[Provider]): Future[Option[(I, Credentials, LoginInfo)]] = {
      providers match {
        case Nil => Future.successful(None)
        case h :: t => h.authenticate(request).flatMap {
          case Authenticated(identity, credentials, loginInfo) =>
            Future.successful(Some((identity, credentials, loginInfo)))
          case MissingCredentials() =>
            logger.info(s"Couldn't find credentials for provider ${h.id}")
            next(t)
          case InvalidCredentials(credentials, errors) =>
            logger.info(s"Invalid credentials $credentials for provider ${h.id}; got errors: $errors")
            next(t)
          case MissingIdentity(_, loginInfo) =>
            logger.info(s"Couldn't find identity for login info: $loginInfo")
            next(t)
          case AuthFailure(cause) =>
            logger.error("Error during authentication process", cause)
            next(t)
          case _ => next(t)
        }
      }
    }

    auth(environment.requestProviders)
  }
}
