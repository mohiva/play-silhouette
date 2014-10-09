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

import javax.xml.bind.DatatypeConverter
import com.sun.xml.internal.messaging.saaj.util.{ Base64 => Decoder }
import play.api.libs.json.JsValue

/**
 * Base64 helper.
 */
object Base64 {

  /**
   * Decodes a Base64 string.
   *
   * @param str The string to decode.
   * @return The decoded string.
   */
  def decode(str: String): String = Decoder.base64Decode(str)

  /**
   * Encodes a string as Base64.
   *
   * @param str The string to encode.
   * @return The encodes string.
   */
  def encode(str: String): String = DatatypeConverter.printBase64Binary(str.getBytes("UTF-8"))

  /**
   * Encodes a Json value as Base64.
   *
   * @param json The json value to encode.
   * @return The encoded value.
   */
  def encode(json: JsValue): String = encode(json.toString())
}
