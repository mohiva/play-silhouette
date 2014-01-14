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
package com.mohiva.play.silhouette.core.utils

import play.api.libs.ws.WS

/**
 * A trait which provides a mockable implementation for the HTTP layer.
 */
trait HTTPLayer {

  /**
   * Prepare a new request. You can then construct it by chaining calls.
   *
   * @param url the URL to request
   */
  def url(url: String): WS.WSRequestHolder
}

/**
 * Implementation of the HTTP layer which uses the Play web service implementation.
 *
 * It makes no sense to move the HTTPLayer implementation to the contrib package, because the complete
 * Silhouette module is bound to Play's HTTP implementation. So this layer exists only for mocking purpose.
 */
class PlayHTTPLayer extends HTTPLayer {

  /**
   * Prepare a new request. You can then construct it by chaining calls.
   *
   * @param url the URL to request
   */
  def url(url: String): WS.WSRequestHolder = WS.url(url)
}
