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

import com.google.inject.{ AbstractModule, Guice, Provides }
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.{ AuthInfo, AuthInfoService }
import com.mohiva.play.silhouette.impl.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth2Info, PasswordInfo }
import com.mohiva.play.silhouette.impl.services.DelegableAuthInfoService._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.specification.Scope
import play.api.test.PlaySpecification

import scala.collection.mutable
import scala.concurrent.Future

/**
 * Test case for the [[com.mohiva.play.silhouette.impl.services.DelegableAuthInfoService]] trait.
 */
class DelegableAuthInfoServiceSpec extends PlaySpecification {

  "The service" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, passwordInfo)) must be equalTo passwordInfo
      await(service.retrieve[PasswordInfo](loginInfo)) must beSome(passwordInfo)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, oauth1Info)) must be equalTo oauth1Info
      await(service.retrieve[OAuth1Info](loginInfo)) must beSome(oauth1Info)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, oauth2Info)) must be equalTo oauth2Info
      await(service.retrieve[OAuth2Info](loginInfo)) must beSome(oauth2Info)
    }

    "throw an Exception if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.save(loginInfo, new UnsupportedInfo)) must throwA[Exception].like {
        case e => e.getMessage must startWith(SaveError.format(""))
      }
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
     * The Guice injector.
     */
    val injector = Guice.createInjector(new BaseModule)

    /**
     * The auth info.
     */
    val passwordInfo = PasswordInfo("test.hasher", "test.password")
    val oauth1Info = OAuth1Info("test.token", "test.secret")
    val oauth2Info = OAuth2Info("test.token")

    /**
     * The cache instance to store the different auth information instances.
     */
    val service = injector.getInstance(classOf[AuthInfoService])
  }

  /**
   * An unsupported auth info.
   */
  class UnsupportedInfo extends AuthInfo

  /**
   * The Guice module.
   *
   * This is to test if the [[com.mohiva.play.silhouette.impl.daos.DelegableAuthInfoDAO]] can be used for
   * dependency injection because it depends on an implicit [[scala.reflect.ClassTag]] parameter which the
   * compiler injects at compile time.
   */
  class BaseModule extends AbstractModule with ScalaModule {

    /**
     * Configures the module.
     */
    def configure() {
      bind[DelegableAuthInfoDAO[PasswordInfo]].to[PasswordInfoDAO]
      bind[DelegableAuthInfoDAO[OAuth1Info]].to[OAuth1InfoDAO]
      bind[DelegableAuthInfoDAO[OAuth2Info]].to[OAuth2InfoDAO]
    }

    /**
     * Provides the auth info service.
     *
     * @param passwordInfoDAO The implementation of the delegable password auth info DAO.
     * @param oauth1InfoDAO The implementation of the delegable OAuth1 auth info DAO.
     * @param oauth2InfoDAO The implementation of the delegable OAuth2 auth info DAO.
     * @return The auth info service instance.
     */
    @Provides
    def provideAuthInfoService(
      passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo],
      oauth1InfoDAO: DelegableAuthInfoDAO[OAuth1Info],
      oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info]): AuthInfoService = {

      new DelegableAuthInfoService(passwordInfoDAO, oauth1InfoDAO, oauth2InfoDAO)
    }
  }
}

/**
 * The DAO to store the password information.
 */
class PasswordInfoDAO extends DelegableAuthInfoDAO[PasswordInfo] {

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
  def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = {
    data += (loginInfo -> authInfo)
    Future.successful(authInfo)
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
class OAuth1InfoDAO extends DelegableAuthInfoDAO[OAuth1Info] {

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
  def save(loginInfo: LoginInfo, authInfo: OAuth1Info): Future[OAuth1Info] = {
    data += (loginInfo -> authInfo)
    Future.successful(authInfo)
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
class OAuth2InfoDAO extends DelegableAuthInfoDAO[OAuth2Info] {

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
  def save(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = {
    data += (loginInfo -> authInfo)
    Future.successful(authInfo)
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
