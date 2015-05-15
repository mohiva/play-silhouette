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
package com.mohiva.play.silhouette.impl.repositories

import com.google.inject.{ AbstractModule, Guice, Provides }
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.api.{ AuthInfo, LoginInfo }
import com.mohiva.play.silhouette.impl.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth2Info }
import com.mohiva.play.silhouette.impl.repositories.DelegableAuthInfoRepository._
import net.codingwell.scalaguice.ScalaModule
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Test case for the [[DelegableAuthInfoRepository]] trait.
 */
class DelegableAuthInfoRepositorySpec extends PlaySpecification with Mockito {

  "The `find` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(passwordInfoDAO.add(loginInfo, passwordInfo))

      await(service.find[PasswordInfo](loginInfo)) must beSome(passwordInfo)
      there was one(passwordInfoDAO).find(===(loginInfo))(any)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(oauth1InfoDAO.add(loginInfo, oauth1Info))

      await(service.find[OAuth1Info](loginInfo)) must beSome(oauth1Info)
      there was one(oauth1InfoDAO).find(===(loginInfo))(any)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(oauth2InfoDAO.add(loginInfo, oauth2Info))

      await(service.find[OAuth2Info](loginInfo)) must beSome(oauth2Info)
      there was one(oauth2InfoDAO).find(===(loginInfo))(any)
    }

    "throw an Exception if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.find[UnsupportedInfo](loginInfo)) must throwA[Exception].like {
        case e => e.getMessage must startWith(FindError.format(""))
      }
    }
  }

  "The `add` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.add(loginInfo, passwordInfo)) must be equalTo passwordInfo
      there was one(passwordInfoDAO).add(===(loginInfo), ===(passwordInfo))(any)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.add(loginInfo, oauth1Info)) must be equalTo oauth1Info
      there was one(oauth1InfoDAO).add(===(loginInfo), ===(oauth1Info))(any)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.add(loginInfo, oauth2Info)) must be equalTo oauth2Info
      there was one(oauth2InfoDAO).add(===(loginInfo), ===(oauth2Info))(any)
    }

    "throw an Exception if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.add(loginInfo, new UnsupportedInfo)) must throwA[Exception].like {
        case e => e.getMessage must startWith(AddError.format(""))
      }
    }
  }

  "The `update` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.update(loginInfo, passwordInfo)) must be equalTo passwordInfo
      there was one(passwordInfoDAO).update(===(loginInfo), ===(passwordInfo))(any)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.update(loginInfo, oauth1Info)) must be equalTo oauth1Info
      there was one(oauth1InfoDAO).update(===(loginInfo), ===(oauth1Info))(any)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.update(loginInfo, oauth2Info)) must be equalTo oauth2Info
      there was one(oauth2InfoDAO).update(===(loginInfo), ===(oauth2Info))(any)
    }

    "throw an Exception if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.update(loginInfo, new UnsupportedInfo)) must throwA[Exception].like {
        case e => e.getMessage must startWith(UpdateError.format(""))
      }
    }
  }

  "The `remove` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(passwordInfoDAO.add(loginInfo, passwordInfo))

      await(service.remove[PasswordInfo](loginInfo)) must be equalTo (())
      there was one(passwordInfoDAO).remove(===(loginInfo))(any)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(oauth1InfoDAO.add(loginInfo, oauth1Info))

      await(service.remove[OAuth1Info](loginInfo)) must be equalTo (())
      there was one(oauth1InfoDAO).remove(===(loginInfo))(any)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(oauth2InfoDAO.add(loginInfo, oauth2Info))

      await(service.remove[OAuth2Info](loginInfo)) must be equalTo (())
      there was one(oauth2InfoDAO).remove(===(loginInfo))(any)
    }

    "throw an Exception if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      await(service.remove[UnsupportedInfo](loginInfo)) must throwA[Exception].like {
        case e => e.getMessage must startWith(RemoveError.format(""))
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
     * The DAOs.
     */
    lazy val passwordInfoDAO = spy(new PasswordInfoDAO)
    lazy val oauth1InfoDAO = spy(new OAuth1InfoDAO)
    lazy val oauth2InfoDAO = spy(new OAuth2InfoDAO)

    /**
     * The cache instance to store the different auth information instances.
     */
    val service = injector.getInstance(classOf[AuthInfoRepository])

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
        bind[DelegableAuthInfoDAO[PasswordInfo]].toInstance(passwordInfoDAO)
        bind[DelegableAuthInfoDAO[OAuth1Info]].toInstance(oauth1InfoDAO)
        bind[DelegableAuthInfoDAO[OAuth2Info]].toInstance(oauth2InfoDAO)
      }

      /**
       * Provides the auth info repository.
       *
       * @param passwordInfoDAO The implementation of the delegable password auth info DAO.
       * @param oauth1InfoDAO The implementation of the delegable OAuth1 auth info DAO.
       * @param oauth2InfoDAO The implementation of the delegable OAuth2 auth info DAO.
       * @return The auth info repository instance.
       */
      @Provides
      def provideAuthInfoService(
        passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo],
        oauth1InfoDAO: DelegableAuthInfoDAO[OAuth1Info],
        oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info]): AuthInfoRepository = {

        new DelegableAuthInfoRepository(passwordInfoDAO, oauth1InfoDAO, oauth2InfoDAO)
      }
    }

    /**
     * The DAO to store the password information.
     */
    class PasswordInfoDAO extends InMemoryAuthInfoDAO[PasswordInfo]

    /**
     * The DAO to store the OAuth1 information.
     */
    class OAuth1InfoDAO extends InMemoryAuthInfoDAO[OAuth1Info]

    /**
     * The DAO to store the OAuth2 information.
     */
    class OAuth2InfoDAO extends InMemoryAuthInfoDAO[OAuth2Info]

    /**
     * An abstract in-memory test helper.
     */
    abstract class InMemoryAuthInfoDAO[T <: AuthInfo: ClassTag] extends DelegableAuthInfoDAO[T] {

      /**
       * The data store for the OAuth1 info.
       */
      var data: mutable.HashMap[LoginInfo, T] = mutable.HashMap()

      /**
       * Finds the OAuth1 info which is linked with the specified login info.
       *
       * @param loginInfo The linked login info.
       * @return The retrieved OAuth1 info or None if no OAuth1 info could be retrieved for the given login info.
       */
      def find(loginInfo: LoginInfo)(implicit ec: ExecutionContext): Future[Option[T]] = {
        Future.successful(Option(data.apply(loginInfo)))
      }

      /**
       * Adds new auth info for the given login info.
       *
       * @param loginInfo The login info for which the auth info should be added.
       * @param authInfo The auth info to add.
       * @return The added auth info.
       */
      def add(loginInfo: LoginInfo, authInfo: T)(implicit ec: ExecutionContext): Future[T] = {
        data += (loginInfo -> authInfo)
        Future.successful(authInfo)
      }

      /**
       * Updates the auth info for the given login info.
       *
       * @param loginInfo The login info for which the auth info should be updated.
       * @param authInfo The auth info to update.
       * @return The updated auth info.
       */
      def update(loginInfo: LoginInfo, authInfo: T)(implicit ec: ExecutionContext): Future[T] = {
        data += (loginInfo -> authInfo)
        Future.successful(authInfo)
      }

      /**
       * Removes the auth info for the given login info.
       *
       * @param loginInfo The login info for which the auth info should be removed.
       * @return A future to wait for the process to be completed.
       */
      def remove(loginInfo: LoginInfo)(implicit ec: ExecutionContext): Future[Unit] = {
        data -= loginInfo
        Future.successful(())
      }
    }
  }
}
