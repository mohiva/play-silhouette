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

import com.google.inject.{ AbstractModule, Guice, Provides }
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.api.{ AuthInfo, LoginInfo }
import com.mohiva.play.silhouette.impl.providers.{ OAuth1Info, OAuth2Info }
import com.mohiva.play.silhouette.persistence.daos.{ DelegableAuthInfoDAO, InMemoryAuthInfoDAO }
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository._
import com.mohiva.play.silhouette.test.WaitPatience
import net.codingwell.scalaguice.ScalaModule
import org.specs2.concurrent.ExecutionEnv
import org.specs2.control.NoLanguageFeatures
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Test case for the [[DelegableAuthInfoRepository]] trait.
 */
class DelegableAuthInfoRepositorySpec(implicit ev: ExecutionEnv)
  extends Specification with Mockito with NoLanguageFeatures with WaitPatience {

  "The `find` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(passwordInfoDAO.add(loginInfo, passwordInfo), 10 seconds)

      service.find[PasswordInfo](loginInfo) must beSome(passwordInfo).awaitWithPatience
      there was one(passwordInfoDAO).find(loginInfo)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(oauth1InfoDAO.add(loginInfo, oauth1Info), 10 seconds)

      service.find[OAuth1Info](loginInfo) must beSome(oauth1Info).awaitWithPatience
      there was one(oauth1InfoDAO).find(loginInfo)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(oauth2InfoDAO.add(loginInfo, oauth2Info), 10 seconds)

      service.find[OAuth2Info](loginInfo) must beSome(oauth2Info).awaitWithPatience
      there was one(oauth2InfoDAO).find(loginInfo)
    }

    "throw a ConfigurationException if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(service.find[UnsupportedInfo](loginInfo), 5 seconds) must throwA[ConfigurationException].like {
        case e => e.getMessage must startWith(FindError.format(classOf[UnsupportedInfo]))
      }
    }
  }

  "The `add` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.add(loginInfo, passwordInfo) must beEqualTo(passwordInfo).awaitWithPatience
      there was one(passwordInfoDAO).add(loginInfo, passwordInfo)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.add(loginInfo, oauth1Info) must beEqualTo(oauth1Info).awaitWithPatience
      there was one(oauth1InfoDAO).add(loginInfo, oauth1Info)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.add(loginInfo, oauth2Info) must beEqualTo(oauth2Info).awaitWithPatience
      there was one(oauth2InfoDAO).add(loginInfo, oauth2Info)
    }

    "throw a ConfigurationException if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(service.add(loginInfo, new UnsupportedInfo), 5 seconds) must throwA[ConfigurationException].like {
        case e => e.getMessage must startWith(AddError.format(classOf[UnsupportedInfo]))
      }
    }
  }

  "The `update` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.update(loginInfo, passwordInfo) must beEqualTo(passwordInfo).awaitWithPatience
      there was one(passwordInfoDAO).update(loginInfo, passwordInfo)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.update(loginInfo, oauth1Info) must beEqualTo(oauth1Info).awaitWithPatience
      there was one(oauth1InfoDAO).update(loginInfo, oauth1Info)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.update(loginInfo, oauth2Info) must beEqualTo(oauth2Info).awaitWithPatience
      there was one(oauth2InfoDAO).update(loginInfo, oauth2Info)
    }

    "throw a ConfigurationException if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(service.update(loginInfo, new UnsupportedInfo), 5 seconds) must throwA[ConfigurationException].like {
        case e => e.getMessage must startWith(UpdateError.format(classOf[UnsupportedInfo]))
      }
    }
  }

  "The `save` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.save(loginInfo, passwordInfo) must beEqualTo(passwordInfo).awaitWithPatience
      there was one(passwordInfoDAO).save(loginInfo, passwordInfo)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.save(loginInfo, oauth1Info) must beEqualTo(oauth1Info).awaitWithPatience
      there was one(oauth1InfoDAO).save(loginInfo, oauth1Info)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      service.save(loginInfo, oauth2Info) must beEqualTo(oauth2Info).awaitWithPatience
      there was one(oauth2InfoDAO).save(loginInfo, oauth2Info)
    }

    "throw a ConfigurationException if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(service.save(loginInfo, new UnsupportedInfo), 5 seconds) must throwA[ConfigurationException].like {
        case e => e.getMessage must startWith(SaveError.format(classOf[UnsupportedInfo]))
      }
    }
  }

  "The `remove` method" should {
    "delegate the PasswordInfo to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(passwordInfoDAO.add(loginInfo, passwordInfo), 10 seconds)

      service.remove[PasswordInfo](loginInfo) must beEqualTo(()).awaitWithPatience
      there was one(passwordInfoDAO).remove(loginInfo)
    }

    "delegate the OAuth1Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(oauth1InfoDAO.add(loginInfo, oauth1Info), 10 seconds)

      service.remove[OAuth1Info](loginInfo) must beEqualTo(()).awaitWithPatience
      there was one(oauth1InfoDAO).remove(loginInfo)
    }

    "delegate the OAuth2Info to the correct DAO" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(oauth2InfoDAO.add(loginInfo, oauth2Info), 10 seconds)

      service.remove[OAuth2Info](loginInfo) must beEqualTo(()).awaitWithPatience
      there was one(oauth2InfoDAO).remove(loginInfo)
    }

    "throw a ConfigurationException if an unsupported type was given" in new Context {
      val loginInfo = LoginInfo("credentials", "1")

      Await.result(service.remove[UnsupportedInfo](loginInfo), 5 seconds) must throwA[ConfigurationException].like {
        case e => e.getMessage must startWith(RemoveError.format(classOf[UnsupportedInfo]))
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
     * This is to test if the [[com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO]] can be used for
     * dependency injection because it depends on an implicit [[scala.reflect.ClassTag]] parameter which the
     * compiler injects at compile time.
     */
    class BaseModule extends AbstractModule with ScalaModule {

      /**
       * Configures the module.
       */
      override def configure(): Unit = {
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
  }
}
