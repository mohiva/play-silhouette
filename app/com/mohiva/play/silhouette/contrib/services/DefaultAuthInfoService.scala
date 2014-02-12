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

import javax.inject.Inject
import scala.reflect.ClassTag
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.services.{ AuthInfo, AuthInfoService }
import com.mohiva.play.silhouette.core.providers.{ OAuth2Info, OAuth1Info, PasswordInfo }
import com.mohiva.play.silhouette.contrib.daos.{ OAuth2InfoDAO, OAuth1InfoDAO, PasswordInfoDAO }
import DefaultAuthInfoService._

/**
 * An implementation of the auth info service which stores the different auth info instances with the help of
 * different DAOs.
 *
 * Due the nature of the different auth information it is hard to persist the data in a single data structure,
 * expect the data gets stored in a serialized format. With this implementation it is possible to store the
 * different auth info in different backing stores. If we speak of a relational database, then the auth info
 * can be stored in different tables. And the tables represents the internal data structure of each auth info
 * object.
 *
 * @param passwordInfoDAO The password info DAO implementation.
 * @param oauth1InfoDAO The OAuth1 info DAO implementation.
 * @param oauth2InfoDAO The OAuth2 info DAO implementation.
 */
class DefaultAuthInfoService @Inject() (
    passwordInfoDAO: PasswordInfoDAO,
    oauth1InfoDAO: OAuth1InfoDAO,
    oauth2InfoDAO: OAuth2InfoDAO) extends AuthInfoService {

  /**
   * Saves auth info.
   *
   * This method gets called when a user logs in(social auth) or registers. This is the change
   * to persist the auth info for a provider in the backing store. If the application supports
   * the concept of "merged identities", i.e., the same user being able to authenticate through
   * different providers, then make sure that the auth info for every linked login info gets
   * stored separately.
   *
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo The auth info to save.
   * @return The saved auth info or None if the auth info couldn't be saved.
   */
  def save[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[Option[T]] = authInfo match {
    case a: PasswordInfo => passwordInfoDAO.save(loginInfo, a).map(_.map(_.asInstanceOf[T]))
    case a: OAuth1Info => oauth1InfoDAO.save(loginInfo, a).map(_.map(_.asInstanceOf[T]))
    case a: OAuth2Info => oauth2InfoDAO.save(loginInfo, a).map(_.map(_.asInstanceOf[T]))
    case a => throw new Exception(SaveError.format(a.getClass))
  }

  /**
   * Retrieves the auth info which is linked with the specified login info.
   *
   * @param loginInfo The linked login info.
   * @param tag The class tag of the auth info.
   * @return The retrieved auth info or None if no auth info could be retrieved for the given login info.
   */
  def retrieve[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Option[T]] = tag match {
    case a if a.runtimeClass == classOf[PasswordInfo] => passwordInfoDAO.find(loginInfo).map(_.map(_.asInstanceOf[T]))
    case a if a.runtimeClass == classOf[OAuth1Info] => oauth1InfoDAO.find(loginInfo).map(_.map(_.asInstanceOf[T]))
    case a if a.runtimeClass == classOf[OAuth2Info] => oauth2InfoDAO.find(loginInfo).map(_.map(_.asInstanceOf[T]))
    case a => throw new Exception(RetrieveError.format(a.runtimeClass))
  }
}

/**
 * The companion object.
 */
object DefaultAuthInfoService {

  /**
   * The error messages.
   */
  val SaveError = "Cannot save auth info of type: %s"
  val RetrieveError = "Cannot search for auth info of type: %s"
}
