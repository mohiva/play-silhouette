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

import org.specs2.matcher.Scope
import play.api.http.ContentTypes._
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.mvc.{ Result, RequestHeader }
import play.api.test.{ FakeRequest, WithApplication, PlaySpecification }

import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.api.ErrorHandler]] implementations.
 */
class ErrorHandlerSpec extends PlaySpecification {

  "The `DefaultNotAuthenticatedErrorHandler.notAuthenticated` method" should {
    "return an HTML response for an HTML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(HTML),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = HTML,
        expectedResponseFragment = "<html>",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => notAuthenticated.onNotAuthenticated(r) })
    }

    "return a JSON response for a JSON request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(JSON),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = JSON,
        expectedResponseFragment = "\"success\":false",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => notAuthenticated.onNotAuthenticated(r) })
    }

    "return a XML response for a XML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(XML),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = XML,
        expectedResponseFragment = "<success>false</success>",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => notAuthenticated.onNotAuthenticated(r) })
    }

    "return a plain text response for a plain text request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(TEXT),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => notAuthenticated.onNotAuthenticated(r) })
    }

    "return a plain text response for other requests" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(BINARY),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => notAuthenticated.onNotAuthenticated(r) })
    }

    "return an HTML response for a request without an Accept header" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = None,
        expectedStatus = UNAUTHORIZED,
        expectedContentType = HTML,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => notAuthenticated.onNotAuthenticated(r) })
    }
  }

  "The `DefaultNotAuthorizedErrorHandler.onNotAuthorized` method" should {
    "return an HTML response for an HTML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(HTML),
        expectedStatus = FORBIDDEN,
        expectedContentType = HTML,
        expectedResponseFragment = "<html>",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => notAuthorized.onNotAuthorized(r) })
    }

    "return a JSON response for a JSON request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(JSON),
        expectedStatus = FORBIDDEN,
        expectedContentType = JSON,
        expectedResponseFragment = "\"success\":false",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => notAuthorized.onNotAuthorized(r) })
    }

    "return a XML response for a XML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(XML),
        expectedStatus = FORBIDDEN,
        expectedContentType = XML,
        expectedResponseFragment = "<success>false</success>",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => notAuthorized.onNotAuthorized(r) })
    }

    "return a plain text response for a plain text request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(TEXT),
        expectedStatus = FORBIDDEN,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => notAuthorized.onNotAuthorized(r) })
    }

    "return a plain text response for other requests" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(BINARY),
        expectedStatus = FORBIDDEN,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => notAuthorized.onNotAuthorized(r) })
    }

    "return an HTML response for a request without an Accept header" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = None,
        expectedStatus = FORBIDDEN,
        expectedContentType = HTML,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => notAuthorized.onNotAuthorized(r) })
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope with I18nSupport {
    self: WithApplication =>

    /**
     * The Play messages API.
     */
    lazy val messagesApi = app.injector.instanceOf[MessagesApi]

    /**
     * The default not-authenticated error handler.
     */
    lazy val notAuthenticated = new DefaultNotAuthenticatedErrorHandler {
      val messagesApi = self.messagesApi
    }

    /**
     * The default not-authorized error handler.
     */
    lazy val notAuthorized = new DefaultNotAuthorizedErrorHandler {
      val messagesApi = self.messagesApi
    }

    /**
     * A helper method to test the response.
     */
    def testResponse(
      acceptedMediaType: Option[String],
      expectedStatus: Int,
      expectedContentType: String,
      expectedResponseFragment: String,
      expectedMessage: String,
      f: RequestHeader => Future[Result]) = {
      implicit val request = acceptedMediaType match {
        case Some(mediaType) => FakeRequest().withHeaders(ACCEPT -> mediaType)
        case None            => FakeRequest()
      }

      val result = f(request)

      status(result) must equalTo(expectedStatus)
      header(CONTENT_TYPE, result) must beSome(expectedContentType)
      contentAsString(result) must contain(expectedResponseFragment)
      contentAsString(result) must contain(Messages(expectedMessage))
    }
  }
}
