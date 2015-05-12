package com.mohiva.play.silhouette.api.util

import org.specs2.specification.Scope
import play.api.http.ContentTypes._
import play.api.i18n.{ Lang, Messages, MessagesApi }
import play.api.mvc._
import play.api.test._

import scala.concurrent._

/**
 * Test case for the [[com.mohiva.play.silhouette.api.util.DefaultEndpointHandler]] class.
 */
class DefaultEndpointHandlerSpec extends PlaySpecification {

  "The `handleNotAuthenticated` method" should {
    "return an HTML response for an HTML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(HTML),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = HTML,
        expectedResponseFragment = "<html>",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthenticated(r, messages) })
    }

    "return a JSON response for a JSON request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(JSON),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = JSON,
        expectedResponseFragment = "\"success\":false",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthenticated(r, messages) })
    }

    "return a XML response for a XML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(XML),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = XML,
        expectedResponseFragment = "<success>false</success>",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthenticated(r, messages) })
    }

    "return a plain text response for a plain text request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(TEXT),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthenticated(r, messages) })
    }

    "return a plain text response for other requests" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(BINARY),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthenticated(r, messages) })
    }

    "return an HTML response for a request without an Accept header" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = None,
        expectedStatus = UNAUTHORIZED,
        expectedContentType = HTML,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthenticated(r, messages) })
    }
  }

  "The `handleNotAuthorized` method" should {
    "return an HTML response for an HTML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(HTML),
        expectedStatus = FORBIDDEN,
        expectedContentType = HTML,
        expectedResponseFragment = "<html>",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthorized(r, messages) })
    }

    "return a JSON response for a JSON request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(JSON),
        expectedStatus = FORBIDDEN,
        expectedContentType = JSON,
        expectedResponseFragment = "\"success\":false",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthorized(r, messages) })
    }

    "return a XML response for a XML request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(XML),
        expectedStatus = FORBIDDEN,
        expectedContentType = XML,
        expectedResponseFragment = "<success>false</success>",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthorized(r, messages) })
    }

    "return a plain text response for a plain text request" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(TEXT),
        expectedStatus = FORBIDDEN,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthorized(r, messages) })
    }

    "return a plain text response for other requests" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = Some(BINARY),
        expectedStatus = FORBIDDEN,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthorized(r, messages) })
    }

    "return an HTML response for a request without an Accept header" in new WithApplication with Context {
      testResponse(
        acceptedMediaType = None,
        expectedStatus = FORBIDDEN,
        expectedContentType = HTML,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultEndpointHandler.handleNotAuthorized(r, messages) })
    }
  }

  /**
   * A helper method to test the response.
   */
  private def testResponse(
    acceptedMediaType: Option[String],
    expectedStatus: Int,
    expectedContentType: String,
    expectedResponseFragment: String,
    expectedMessage: String,
    f: RequestHeader => Future[Result])(implicit messages: Messages) = {
    implicit val request = acceptedMediaType match {
      case Some(mediaType) => FakeRequest().withHeaders(ACCEPT -> mediaType)
      case None => FakeRequest()
    }

    val result = f(request)

    status(result) must equalTo(expectedStatus)
    header(CONTENT_TYPE, result) must beSome(expectedContentType)
    contentAsString(result) must contain(expectedResponseFragment)
    contentAsString(result) must contain(Messages(expectedMessage))
  }

  /**
   * The context.
   */
  trait Context extends Scope {
    self: WithApplication =>

    /**
     * The messages API.
     */
    implicit val messagesAPI = app.injector.instanceOf[MessagesApi]

    /**
     * The messages for the current language.
     */
    implicit val messages = Messages(Lang("en-US"), messagesAPI)
  }
}
