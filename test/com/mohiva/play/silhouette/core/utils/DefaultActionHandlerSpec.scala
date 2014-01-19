package com.mohiva.play.silhouette.core.utils

import play.api.mvc._
import play.api.test._
import play.api.http.ContentTypes._
import play.api.i18n.Messages
import scala.concurrent._

/**
 * Test case for the [[com.mohiva.play.silhouette.core.utils.DefaultActionHandler]] class.
 */
class DefaultActionHandlerSpec extends PlaySpecification {

  "The handleForbidden method" should {
    "return an HTML response for an HTML request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(HTML),
        expectedStatus = FORBIDDEN,
        expectedContentType = HTML,
        expectedResponseFragment = "<html>",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultActionHandler.handleForbidden(r) })
    }

    "return a JSON response for a JSON request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(JSON),
        expectedStatus = FORBIDDEN,
        expectedContentType = JSON,
        expectedResponseFragment = "\"success\":false",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultActionHandler.handleForbidden(r) })
    }

    "return a XML response for a XML request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(XML),
        expectedStatus = FORBIDDEN,
        expectedContentType = XML,
        expectedResponseFragment = "<success>false</success>",
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultActionHandler.handleForbidden(r) })
    }

    "return a plain text response for a plain text request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(TEXT),
        expectedStatus = FORBIDDEN,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultActionHandler.handleForbidden(r) })
    }

    "return a plain text response for other requests" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(BINARY),
        expectedStatus = FORBIDDEN,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultActionHandler.handleForbidden(r) })
    }

    "return an HTML response for a request without an Accept header" in new WithApplication {
      testResponse(
        acceptedMediaType = None,
        expectedStatus = FORBIDDEN,
        expectedContentType = HTML,
        expectedResponseFragment = Messages("silhouette.not.authorized"),
        expectedMessage = "silhouette.not.authorized",
        f = { r: RequestHeader => DefaultActionHandler.handleForbidden(r) })
    }
  }

  "The handleUnauthorized method" should {
    "return an HTML response for an HTML request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(HTML),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = HTML,
        expectedResponseFragment = "<html>",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultActionHandler.handleUnauthorized(r) })
    }

    "return a JSON response for a JSON request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(JSON),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = JSON,
        expectedResponseFragment = "\"success\":false",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultActionHandler.handleUnauthorized(r) })
    }

    "return a XML response for a XML request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(XML),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = XML,
        expectedResponseFragment = "<success>false</success>",
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultActionHandler.handleUnauthorized(r) })
    }

    "return a plain text response for a plain text request" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(TEXT),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultActionHandler.handleUnauthorized(r) })
    }

    "return a plain text response for other requests" in new WithApplication {
      testResponse(
        acceptedMediaType = Some(BINARY),
        expectedStatus = UNAUTHORIZED,
        expectedContentType = TEXT,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultActionHandler.handleUnauthorized(r) })
    }

    "return an HTML response for a request without an Accept header" in new WithApplication {
      testResponse(
        acceptedMediaType = None,
        expectedStatus = UNAUTHORIZED,
        expectedContentType = HTML,
        expectedResponseFragment = Messages("silhouette.not.authenticated"),
        expectedMessage = "silhouette.not.authenticated",
        f = { r: RequestHeader => DefaultActionHandler.handleUnauthorized(r) })
    }
  }

  private def testResponse(
      acceptedMediaType: Option[String],
      expectedStatus: Int,
      expectedContentType: String,
      expectedResponseFragment: String,
      expectedMessage: String,
      f: RequestHeader => Future[SimpleResult]) = {
    implicit val request = acceptedMediaType match {
      case Some(mediaType) => FakeRequest().withHeaders(ACCEPT -> mediaType)
      case None => FakeRequest()
    }

    val result = f(request)

    status(result) must equalTo(expectedStatus)
    header(CONTENT_TYPE, result) must beSome
    header(CONTENT_TYPE, result).get must equalTo(expectedContentType)
    contentAsString(result) must contain(expectedResponseFragment)
    contentAsString(result) must contain(Messages(expectedMessage))
  }
}
