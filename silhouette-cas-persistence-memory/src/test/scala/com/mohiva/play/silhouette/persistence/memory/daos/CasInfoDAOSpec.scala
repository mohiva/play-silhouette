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
package com.mohiva.play.silhouette.persistence.memory.daos

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CasInfo
import com.mohiva.play.silhouette.test.WaitPatience
import org.specs2.concurrent.ExecutionEnv
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Test case for the [[CasInfoDAO]] class.
 */
class CasInfoDAOSpec(implicit ev: ExecutionEnv) extends Specification with NoLanguageFeatures with WaitPatience {

  "The `find` method" should {
    "find an CAS info for the given login info" in new Context {
      Await.result(dao.save(loginInfo, authInfo), 10 seconds)

      dao.find(loginInfo) must beSome(authInfo).awaitWithPatience
    }

    "return None if no CAS info for the given login info exists" in new Context {
      dao.find(loginInfo.copy(providerKey = "new.key")) should beNone.awaitWithPatience
    }
  }

  "The `add` method" should {
    "add a new CAS info" in new Context {
      dao.add(loginInfo.copy(providerKey = "new.key"), authInfo) must beEqualTo(authInfo).awaitWithPatience
      dao.find(loginInfo.copy(providerKey = "new.key")) must beSome(authInfo).awaitWithPatience
    }
  }

  "The `update` method" should {
    "update an existing CAS info" in new Context {
      val updatedInfo = authInfo.copy(ticket = "updated-ticket")

      dao.update(loginInfo, updatedInfo) must beEqualTo(updatedInfo).awaitWithPatience
      dao.find(loginInfo) must beSome(updatedInfo).awaitWithPatience
    }
  }

  "The `save` method" should {
    "insert a new CAS info" in new Context {
      dao.save(loginInfo.copy(providerKey = "new.key"), authInfo) must beEqualTo(authInfo).awaitWithPatience
      dao.find(loginInfo.copy(providerKey = "new.key")) must beSome(authInfo).awaitWithPatience
    }

    "update an existing CAS info" in new Context {
      val updatedInfo = authInfo.copy(ticket = "updated-ticket")

      dao.update(loginInfo, updatedInfo) must beEqualTo(updatedInfo).awaitWithPatience
      dao.find(loginInfo) must beSome(updatedInfo).awaitWithPatience
    }
  }

  "The `remove` method" should {
    "remove an CAS info" in new Context {
      Await.result(dao.remove(loginInfo), 10 seconds)
      dao.find(loginInfo) must beNone.awaitWithPatience
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The CAS info DAO implementation.
     */
    lazy val dao = new CasInfoDAO

    /**
     * A login info.
     */
    lazy val loginInfo = LoginInfo("cas", "134405962728980")

    /**
     * A CAS info.
     */
    lazy val authInfo = CasInfo("my-cas-ticket")
  }
}
