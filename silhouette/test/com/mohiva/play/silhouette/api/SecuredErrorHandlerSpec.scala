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
package com.mohiva.play.silhouette.api

import javax.inject.Inject

import com.mohiva.play.silhouette.api.SecuredErrorHandlerSpec._
import org.specs2.specification.Scope
import play.api.http.{ DefaultHttpErrorHandler, HttpErrorHandler }
import play.api.i18n.{ Lang, Messages, MessagesApi }
import play.api.inject.Module
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.routing.Router
import play.api.test.{ FakeRequest, PlaySpecification, WithApplication }
import play.api.{ Configuration, OptionalSourceMapper }

/**
 * Test case for the [[com.mohiva.play.silhouette.api.SecuredErrorHandler]] trait.
 */
class SecuredErrorHandlerSpec extends PlaySpecification {

  "The `SilhouetteErrorHandler` implementation" should {
    "return None as default value for onNotAuthenticated method " in new WithApplication with Context {
      handler.onNotAuthenticated(FakeRequest(GET, "/"), messages) should beNone
    }

    "return None as default value for onNotAuthenticated method " in new WithApplication with Context {
      handler.onNotAuthorized(FakeRequest(GET, "/"), messages) should beNone
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {
    self: WithApplication =>

    /**
     * The injector instance.
     */
    lazy val injector = new GuiceApplicationBuilder().overrides(new Module {
      def bindings(env: play.api.Environment, conf: Configuration) = Seq(
        bind[HttpErrorHandler].to[ErrorHandler]
      )
    }).injector()

    /**
     * The messages for the current language.
     */
    val messages = Messages(Lang("en-US"), injector.instanceOf[MessagesApi])

    /**
     * The error handler to test.
     */
    val handler = injector.instanceOf[HttpErrorHandler].asInstanceOf[SecuredErrorHandler]
  }
}

/**
 * The companion object.
 */
object SecuredErrorHandlerSpec {

  /**
   * A custom error handler.
   */
  class ErrorHandler @Inject() (
    env: play.api.Environment,
    config: Configuration,
    sourceMapper: OptionalSourceMapper,
    router: javax.inject.Provider[Router])
    extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
    with SecuredErrorHandler
}
