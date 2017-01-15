/**
 * Copyright 2016 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.play.silhouette.impl.providers.state

import com.mohiva.play.silhouette.api.util.ExtractableRequest
import com.mohiva.play.silhouette.impl.providers.SocialStateItem.ItemStructure
import com.mohiva.play.silhouette.impl.providers.state.UserStateItemHandler._
import com.mohiva.play.silhouette.impl.providers.{ SocialStateItem, SocialStateItemHandler }
import play.api.libs.json.{ Format, Json }

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag
import scala.util.Try

/**
 * Handles user defined state.
 *
 * @tparam S The type of the user state.
 * @param userState The user state.
 * @param format The JSON format to the transform the user state into JSON.
 * @param classTag The class tag for the user state item.
 */
class UserStateItemHandler[S <: SocialStateItem](userState: S)(
  implicit
  format: Format[S],
  classTag: ClassTag[S])
  extends SocialStateItemHandler {

  /**
   * The item the handler can handle.
   */
  override type Item = S

  /**
   * Gets the state item the handler can handle.
   *
   * @param ec The execution context to handle the asynchronous operations.
   * @return The state params the handler can handle.
   */
  override def item(implicit ec: ExecutionContext): Future[Item] = Future.successful(userState)

  /**
   * Indicates if a handler can handle the given [[SocialStateItem]].
   *
   * This method should check if the [[serialize]] method of this handler can serialize the given
   * unserialized state item.
   *
   * @param item The item to check for.
   * @return [[Some]] casted state item if the handler can handle the given state item, [[None]] otherwise.
   */
  override def canHandle(item: SocialStateItem): Option[Item] = item match {
    case i: Item => Some(i)
    case _       => None
  }

  /**
   * Indicates if a handler can handle the given unserialized state item.
   *
   * This method should check if the [[unserialize]] method of this handler can unserialize the given
   * serialized state item.
   *
   * @param item The item to check for.
   * @return True if the handler can handle the given state item, false otherwise.
   */
  override def canHandle(item: ItemStructure): Boolean = item.id == ID && item.version == Version

  /**
   * Returns a serialized value of the state item.
   *
   * @param item The state item to serialize.
   * @return The serialized state item.
   */
  override def serialize(item: Item): ItemStructure = ItemStructure(ID, Version, Json.toJson(item))

  /**
   * Unserializes the state item.
   *
   * @param item    The state item to unserialize.
   * @param request The request instance to get additional data to validate against.
   * @param ec      The execution context to handle the asynchronous operations.
   * @tparam B The type of the request body.
   * @return The unserialized state item.
   */
  override def unserialize[B](item: ItemStructure)(
    implicit
    request: ExtractableRequest[B],
    ec: ExecutionContext
  ): Future[Item] = {
    Future.fromTry(Try(item.data.as[S]))
  }
}

/**
 * The companion object.
 */
object UserStateItemHandler {
  val ID = "user-state"
  val Version = "1"
}
