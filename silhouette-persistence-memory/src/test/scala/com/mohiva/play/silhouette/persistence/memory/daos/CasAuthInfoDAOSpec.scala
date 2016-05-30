package com.mohiva.play.silhouette.persistence.memory.daos

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.test.WaitPatience
import org.specs2.concurrent.ExecutionEnv
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import com.mohiva.play.silhouette.impl.providers.cas.CasAuthInfo

/**
 * Test case for the [[CasAuthInfoDAO]] class.
 */
class CasAuthInfoDAOSpec(implicit ev: ExecutionEnv) extends Specification with NoLanguageFeatures with WaitPatience {

  "The `find` method" should {
    "find an CasAuth info for the given login info" in new Context {
      Await.result(dao.save(loginInfo, authInfo), 10 seconds)

      dao.find(loginInfo) must beSome(authInfo).awaitWithPatience
    }

    "return None if no CasAuth info for the given login info exists" in new Context {
      dao.find(loginInfo.copy(providerKey = "new.key")) should beNone.awaitWithPatience
    }

    "The `add` method" should {
      "add a new CasAuth info" in new Context {
        dao.add(loginInfo.copy(providerKey = "new.key"), authInfo) must beEqualTo(authInfo).awaitWithPatience
        dao.find(loginInfo.copy(providerKey = "new.key")) must beSome(authInfo).awaitWithPatience
      }
    }

    "The `update` method" should {
      "update an existing CasAuth info" in new Context {
        val updatedInfo = authInfo.copy(ticket = "updated-ticket")

        dao.update(loginInfo, updatedInfo) must beEqualTo(updatedInfo).awaitWithPatience
        dao.find(loginInfo) must beSome(updatedInfo).awaitWithPatience
      }
    }

    "The `save` method" should {
      "insert a new CasAuth info" in new Context {
        dao.save(loginInfo.copy(providerKey = "new.key"), authInfo) must beEqualTo(authInfo).awaitWithPatience
        dao.find(loginInfo.copy(providerKey = "new.key")) must beSome(authInfo).awaitWithPatience
      }

      "update an existing CasAuth info" in new Context {
        val updatedInfo = authInfo.copy(ticket = "updated-ticket")

        dao.update(loginInfo, updatedInfo) must beEqualTo(updatedInfo).awaitWithPatience
        dao.find(loginInfo) must beSome(updatedInfo).awaitWithPatience
      }
    }

    "The `remove` method" should {
      "remove an CasAuth info" in new Context {
        Await.result(dao.remove(loginInfo), 10 seconds)
        dao.find(loginInfo) must beNone.awaitWithPatience
      }
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The OAuth2 info DAO implementation.
     */
    lazy val dao = new CasAuthInfoDAO

    /**
     * A login info.
     */
    lazy val loginInfo = LoginInfo("cas", "134405962728980")

    /**
     * A OAuth2 info.
     */
    lazy val authInfo = CasAuthInfo(
      ticket = "my-cas-ticket"
    )
  }

}