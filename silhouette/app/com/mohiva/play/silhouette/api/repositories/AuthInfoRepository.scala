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
package com.mohiva.play.silhouette.api.repositories

import com.mohiva.play.silhouette.api.{ AuthInfo, LoginInfo }

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * A trait that provides the means to persist authentication information for the Silhouette module.
 *
 * If the application supports the concept of "merged identities", i.e., the same user being
 * able to authenticate through different providers, then make sure that the auth info for
 * every linked login info gets stored separately.
 */
trait AuthInfoRepository {

  /**
   * Finds the auth info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @param tag The class tag of the auth info.
   * @tparam T The type of the auth info to handle.
   * @return The found auth info or None if no auth info could be found for the given login info.
   */
  def find[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Option[T]]

  /**
   * Adds new auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @tparam T The type of the auth info to handle.
   * @return The saved auth info.
   */
  def add[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T]

  /**
   * Updates the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be updated.
   * @param authInfo The auth info to update.
   * @tparam T The type of the auth info to handle.
   * @return The updated auth info.
   */
  def update[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T]

  /**
   * Removes the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be removed.
   * @param tag The class tag of the auth info.
   * @tparam T The type of the auth info to handle.
   * @return A future to wait for the process to be completed.
   */
  def remove[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Unit]
}
