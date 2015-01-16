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
package com.mohiva.play.silhouette.impl.services

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.{ AuthInfo, AuthInfoService }
import com.mohiva.play.silhouette.impl.daos.{ AuthInfoDAO, DelegableAuthInfoDAO }
import com.mohiva.play.silhouette.impl.services.DelegableAuthInfoService._
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * An implementation of the auth info service which delegates the storage of an auth info instance to its
 * appropriate DAO.
 *
 * Due the nature of the different auth information it is hard to persist the data in a single data structure,
 * expect the data gets stored in a serialized format. With this implementation it is possible to store the
 * different auth info in different backing stores. If we speak of a relational database, then the auth info
 * can be stored in different tables. And the tables represents the internal data structure of each auth info
 * object.
 *
 * @param daos The auth info DAO implementations.
 */
class DelegableAuthInfoService(daos: DelegableAuthInfoDAO[_]*) extends AuthInfoService {

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
  def save[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T] = {
    daos.find(_.classTag.runtimeClass == authInfo.getClass) match {
      case Some(dao) => dao.asInstanceOf[AuthInfoDAO[T]].save(loginInfo, authInfo)
      case _ => throw new Exception(SaveError.format(authInfo.getClass))
    }
  }

  /**
   * Retrieves the auth info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @param tag The class tag of the auth info.
   * @return The retrieved auth info or None if no auth info could be retrieved for the given login info.
   */
  def retrieve[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Option[T]] = {
    daos.find(_.classTag == tag) match {
      case Some(dao) => dao.find(loginInfo).map(_.map(_.asInstanceOf[T]))
      case _ => throw new Exception(RetrieveError.format(tag.runtimeClass))
    }
  }
}

/**
 * The companion object.
 */
object DelegableAuthInfoService {

  /**
   * The error messages.
   */
  val SaveError = "Cannot save auth info of type: %s"
  val RetrieveError = "Cannot search for auth info of type: %s"
}
