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

import org.specs2.specification.Scope
import play.api.test.PlaySpecification
import scala.collection.mutable
import scala.concurrent.Future
import com.mohiva.play.silhouette.core.LoginInfo
import com.mohiva.play.silhouette.core.providers.{ PasswordInfo, OAuth2Info, OAuth1Info }
import com.mohiva.play.silhouette.contrib.daos.{ OAuth2InfoDAO, OAuth1InfoDAO, PasswordInfoDAO }
import com.mohiva.play.silhouette.core.services.AuthInfo
import DefaultAuthInfoService._

/**
 * Test case for the [[com.mohiva.play.silhouette.contrib.services.DefaultAuthInfoService]] trait.
 */
class DefaultAuthInfoServiceSpec extends PlaySpecification {

  "The save method" should {
    "save the PasswordInfo in the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, passwordInfo)) must beSome(passwordInfo)
      passwordInfoDAO.data.apply(loginInfo) must be equalTo passwordInfo
    }

    "save the OAuth1Info in the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, oauth1Info)) must beSome(oauth1Info)
      oauth1InfoDAO.data.apply(loginInfo) must be equalTo oauth1Info
    }

    "save the OAuth2Info in the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, oauth2Info)) must beSome(oauth2Info)
      oauth2InfoDAO.data.apply(loginInfo) must be equalTo oauth2Info
    }

    "throw an Exception if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, new UnsupportedInfo)) must throwA[Exception].like {
        case e => e.getMessage must startWith(SaveError.format(""))
      }
    }
  }

  "The retrieve method" should {
    "retrieve the PasswordInfo from the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")
      passwordInfoDAO.data += (loginInfo -> passwordInfo)

      await(service.retrieve[PasswordInfo](loginInfo)) must beSome(passwordInfo)
    }

    "retrieve the OAuth1Info from the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")
      oauth1InfoDAO.data += (loginInfo -> oauth1Info)

      await(service.retrieve[OAuth1Info](loginInfo)) must beSome(oauth1Info)
    }

    "retrieve the OAuth2Info from the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")
      oauth2InfoDAO.data += (loginInfo -> oauth2Info)

      await(service.retrieve[OAuth2Info](loginInfo)) must beSome(oauth2Info)
    }

    "throw an Exception if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.retrieve[UnsupportedInfo](loginInfo)) must throwA[Exception].like {
        case e => e.getMessage must startWith(RetrieveError.format(""))
      }
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The DAOs.
     */
    val passwordInfoDAO = new PasswordInfoDAOImpl
    val oauth1InfoDAO = new OAuth1InfoDAOImpl
    val oauth2InfoDAO = new OAuth2InfoDAOImpl

    /**
     * The auth info.
     */
    val passwordInfo = PasswordInfo("test.hasher", "test.password")
    val oauth1Info = OAuth1Info("test.token", "test.secret")
    val oauth2Info = OAuth2Info("test.token")

    /**
     * The cache instance to store the different auth information instances.
     */
    val service = new DefaultAuthInfoService(passwordInfoDAO, oauth1InfoDAO, oauth2InfoDAO)
  }

  /**
   * An unsupported auth info.
   */
  class UnsupportedInfo extends AuthInfo

  /**
   * The DAO to store the password information.
   */
  class PasswordInfoDAOImpl extends PasswordInfoDAO {

    /**
     * The data store for the password info.
     */
    var data: mutable.HashMap[LoginInfo, PasswordInfo] = mutable.HashMap()

    /**
     * Saves the password info.
     *
     * @param loginInfo The login info for which the auth info should be saved.
     * @param authInfo The password info to save.
     * @return The saved password info or None if the password info couldn't be saved.
     */
    def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[Option[PasswordInfo]] = {
      data += (loginInfo -> authInfo)
      Future.successful(Some(authInfo))
    }

    /**
     * Finds the password info which is linked with the specified login info.
     *
     * @param loginInfo The linked login info.
     * @return The retrieved password info or None if no password info could be retrieved for the given login info.
     */
    def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] = {
      Future.successful(Option(data.apply(loginInfo)))
    }
  }

  /**
   * The DAO to store the OAuth1 information.
   */
  class OAuth1InfoDAOImpl extends OAuth1InfoDAO {

    /**
     * The data store for the OAuth1 info.
     */
    var data: mutable.HashMap[LoginInfo, OAuth1Info] = mutable.HashMap()

    /**
     * Saves the OAuth1 info.
     *
     * @param loginInfo The login info for which the auth info should be saved.
     * @param authInfo The OAuth1 info to save.
     * @return The saved OAuth1 info or None if the OAuth1 info couldn't be saved.
     */
    def save(loginInfo: LoginInfo, authInfo: OAuth1Info): Future[Option[OAuth1Info]] = {
      data += (loginInfo -> authInfo)
      Future.successful(Some(authInfo))
    }

    /**
     * Finds the OAuth1 info which is linked with the specified login info.
     *
     * @param loginInfo The linked login info.
     * @return The retrieved OAuth1 info or None if no OAuth1 info could be retrieved for the given login info.
     */
    def find(loginInfo: LoginInfo): Future[Option[OAuth1Info]] = {
      Future.successful(Option(data.apply(loginInfo)))
    }
  }

  /**
   * The DAO to store the OAuth2 information.
   */
  class OAuth2InfoDAOImpl extends OAuth2InfoDAO {

    /**
     * The data store for the OAuth2 info.
     */
    var data: mutable.HashMap[LoginInfo, OAuth2Info] = mutable.HashMap()

    /**
     * Saves the OAuth2 info.
     *
     * @param loginInfo The login info for which the auth info should be saved.
     * @param authInfo The OAuth2 info to save.
     * @return The saved OAuth2 info or None if the OAuth2 info couldn't be saved.
     */
    def save(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[Option[OAuth2Info]] = {
      data += (loginInfo -> authInfo)
      Future.successful(Some(authInfo))
    }

    /**
     * Finds the OAuth2 info which is linked with the specified login info.
     *
     * @param loginInfo The linked login info.
     * @return The retrieved OAuth2 info or None if no OAuth2 info could be retrieved for the given login info.
     */
    def find(loginInfo: LoginInfo): Future[Option[OAuth2Info]] = {
      Future.successful(Option(data.apply(loginInfo)))
    }
  }
}
