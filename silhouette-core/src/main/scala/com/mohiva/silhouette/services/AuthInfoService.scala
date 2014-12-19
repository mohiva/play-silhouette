/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
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
package com.mohiva.silhouette.services

import com.mohiva.silhouette.LoginInfo
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * A trait that provides the means to find and save auth information
 * for the Silhouette module.
 */
trait AuthInfoService {

  /**
   * Saves auth info.
   *
   * This method gets called when a user logs in (social auth) or registers. This is the chance
   * to persist the auth info for a provider in the backing store. If the application supports
   * the concept of "merged identities", i.e., the same user being able to authenticate through
   * different providers, then make sure that the auth info for every linked login info gets
   * stored separately.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @return The saved auth info.
   */
  def save[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T]

  /**
   * Retrieves the auth info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @param tag The class tag of the auth info.
   * @return The retrieved auth info or None if no auth info could be retrieved for the given login info.
   */
  def retrieve[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Option[T]]
}

/**
 * A marker trait for authentication information.
 */
trait AuthInfo
