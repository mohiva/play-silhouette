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
package com.mohiva.play.silhouette.persistence.repositories

import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.{ AuthInfo, LoginInfo }
import com.mohiva.play.silhouette.persistence.daos.{ AuthInfoDAO, DelegableAuthInfoDAO }
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository._

import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.ClassTag

/**
 * An implementation of the auth info repository which delegates the storage of an auth info instance to its
 * appropriate DAO.
 *
 * Due the nature of the different auth information it is hard to persist the data in a single data structure,
 * expect the data gets stored in a serialized format. With this implementation it is possible to store the
 * different auth info in different backing stores. If we speak of a relational database, then the auth info
 * can be stored in different tables. And the tables represents the internal data structure of each auth info
 * object.
 *
 * @param daos The auth info DAO implementations.
 * @param ec The execution context to handle the asynchronous operations.
 */
class DelegableAuthInfoRepository(daos: DelegableAuthInfoDAO[_]*)(implicit ec: ExecutionContext) extends AuthInfoRepository {

  /**
   * Finds the auth info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @param tag The class tag of the auth info.
   * @tparam T The type of the auth info to handle.
   * @return The found auth info or None if no auth info could be found for the given login info.
   */
  override def find[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Option[T]] = {
    daos.find(_.classTag == tag) match {
      case Some(dao) => dao.find(loginInfo).map(_.map(_.asInstanceOf[T]))
      case _ => throw new ConfigurationException(FindError.format(tag.runtimeClass))
    }
  }

  /**
   * Adds new auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @tparam T The type of the auth info to handle.
   * @return The saved auth info.
   */
  override def add[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T] = {
    daos.find(_.classTag.runtimeClass == authInfo.getClass) match {
      case Some(dao) => dao.asInstanceOf[AuthInfoDAO[T]].add(loginInfo, authInfo)
      case _ => throw new ConfigurationException(AddError.format(authInfo.getClass))
    }
  }

  /**
   * Updates the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be updated.
   * @param authInfo The auth info to update.
   * @tparam T The type of the auth info to handle.
   * @return The updated auth info.
   */
  override def update[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T] = {
    daos.find(_.classTag.runtimeClass == authInfo.getClass) match {
      case Some(dao) => dao.asInstanceOf[AuthInfoDAO[T]].update(loginInfo, authInfo)
      case _ => throw new ConfigurationException(UpdateError.format(authInfo.getClass))
    }
  }

  /**
   * Updates the auth info for the given login info.
   *
   * This method either adds the auth info if it doesn't exists or it updates the auth info if it already exists.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @tparam T The type of the auth info to handle.
   * @return The updated auth info.
   */
  override def save[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T] = {
    daos.find(_.classTag.runtimeClass == authInfo.getClass) match {
      case Some(dao) => dao.asInstanceOf[AuthInfoDAO[T]].save(loginInfo, authInfo)
      case _ => throw new ConfigurationException(SaveError.format(authInfo.getClass))
    }
  }

  /**
   * Removes the auth info for the given login info.
   *
   * @param loginInfo The login info for which the auth info should be removed.
   * @param tag The class tag of the auth info.
   * @tparam T The type of the auth info to handle.
   * @return A future to wait for the process to be completed.
   */
  override def remove[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Unit] = {
    daos.find(_.classTag == tag) match {
      case Some(dao) => dao.remove(loginInfo)
      case _ => throw new ConfigurationException(RemoveError.format(tag.runtimeClass))
    }
  }
}

/**
 * The companion object.
 */
object DelegableAuthInfoRepository {

  /**
   * The error messages.
   */
  val AddError = "Cannot add auth info of type: %s; Please configure the DAO for this type"
  val FindError = "Cannot find auth info of type: %s; Please configure the DAO for this type"
  val UpdateError = "Cannot update auth info of type: %s; Please configure the DAO for this type"
  val SaveError = "Cannot save auth info of type: %s; Please configure the DAO for this type"
  val RemoveError = "Cannot remove auth info of type: %s; Please configure the DAO for this type"
}
