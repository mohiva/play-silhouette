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
package com.mohiva.play.silhouette.contrib.services

import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification
import com.mohiva.play.silhouette.core.utils.HTTPLayer
import play.api.libs.ws.{ WSRequestHolder, WSResponse }
import scala.concurrent.Future
import GravatarService._

/**
 * Test case for the [[com.mohiva.play.silhouette.contrib.services.GravatarService]] class.
 */
class GravatarServiceSpec extends PlaySpecification with Mockito {

  "The retrieveURL method" should {
    "return None if email is empty" in new Context {
      await(service.retrieveURL("")) should beNone
    }

    "return None if HTTP status code isn't 200" in new Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]

      response.status returns 404
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(any) returns requestHolder

      await(service.retrieveURL(email)) should beNone
    }

    "return None if HTTP status code isn't 200" in new Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]

      response.status returns 404
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(any) returns requestHolder

      await(service.retrieveURL(email)) should beNone
    }

    "return None if exception will be thrown during API request" in new Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]

      response.status throws new RuntimeException("Timeout error")
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(any) returns requestHolder

      await(service.retrieveURL(email)) should beNone
    }

    "return Avatar url" in new Context {
      val requestHolder = mock[WSRequestHolder]
      val response = mock[WSResponse]

      response.status returns 200
      requestHolder.get() returns Future.successful(response)
      httpLayer.url(any) returns requestHolder

      await(service.retrieveURL(email)) should beSome(URL.format(hash))
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The HTTP layer implementation.
     */
    val httpLayer = mock[HTTPLayer]

    /**
     * The gravatar service implementation.
     */
    val service = new GravatarService(httpLayer)

    /**
     * The email for which the Avatar should be retrieved.
     */
    val email = "apollonia.vanova@watchmen.com"

    /**
     * The generated hash for the email address.
     *
     * @see http://en.gravatar.com/site/check/
     */
    val hash = "c6b0eb337880e2960f5bdbf0aa24c8ac"
  }
}
