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
import com.mohiva.play.silhouette.impl.providers.OpenIDInfo
import org.specs2.concurrent.ExecutionEnv
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Test case for the [[OpenIDInfoDAO]] class.
 */
class OpenIDInfoDAOSpec(implicit ev: ExecutionEnv) extends Specification with NoLanguageFeatures {

  "The `find` method" should {
    "find an OAuth1 info for the given login info" in new Context {
      Await.result(dao.save(loginInfo, authInfo), 10 seconds)

      dao.find(loginInfo) must beSome(authInfo).await
    }

    "return None if no OAuth1 info for the given login info exists" in new Context {
      dao.find(loginInfo.copy(providerKey = "new.key")) should beNone.await
    }
  }

  "The `add` method" should {
    "add a new OAuth1 info" in new Context {
      dao.add(loginInfo.copy(providerKey = "new.key"), authInfo) must beEqualTo(authInfo).await
      dao.find(loginInfo.copy(providerKey = "new.key")) must beSome(authInfo).await
    }
  }

  "The `update` method" should {
    "update an existing OAuth1 info" in new Context {
      val updatedInfo = authInfo.copy(attributes = authInfo.attributes.updated("fullname", "updated"))

      dao.update(loginInfo, updatedInfo) must beEqualTo(updatedInfo).await
      dao.find(loginInfo) must beSome(updatedInfo).await
    }
  }

  "The `save` method" should {
    "insert a new OAuth1 info" in new Context {
      dao.save(loginInfo.copy(providerKey = "new.key"), authInfo) must beEqualTo(authInfo).await
      dao.find(loginInfo.copy(providerKey = "new.key")) must beSome(authInfo).await
    }

    "update an existing OAuth1 info" in new Context {
      val updatedInfo = authInfo.copy(attributes = authInfo.attributes.updated("fullname", "updated"))

      dao.update(loginInfo, updatedInfo) must beEqualTo(updatedInfo).await
      dao.find(loginInfo) must beSome(updatedInfo).await
    }
  }

  "The `remove` method" should {
    "remove an OAuth1 info" in new Context {
      Await.result(dao.remove(loginInfo), 10 seconds)
      dao.find(loginInfo) must beNone.await
    }
  }

  /**
   * The context.
   */
  trait Context extends Scope {

    /**
     * The OAuth1 info DAO implementation.
     */
    lazy val dao = new OpenIDInfoDAO

    /**
     * A login info.
     */
    lazy val loginInfo = LoginInfo("provider", "https://me.yahoo.com/a/Xs6hPjazdrMvmbn4jhQjkjkhcasdGdsKajq9we")

    /**
     * A OAuth1 info.
     */
    lazy val authInfo = OpenIDInfo("https://me.yahoo.com/a/Xs6hPjazdrMvmbn4jhQjkjkhcasdGdsKajq9we", Map(
      "fullname" -> "Apollonia Vanova",
      "email" -> "apollonia.vanova@watchmen.com",
      "image" -> "https://s.yimg.com/dh/ap/social/profile/profile_b48.png"
    ))
  }
}
