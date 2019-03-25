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
package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util._
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.test.PlaySpecification

/**
 * Abstract test case for the [[com.mohiva.play.silhouette.impl.providers.PasswordProvider]] based class.
 */
trait PasswordProviderSpec extends PlaySpecification with Mockito {

  /**
   * The context.
   */
  trait BaseContext extends Scope {

    /**
     * The default password hasher.
     */
    lazy val fooHasher = hasher("foo")

    /**
     * A deprecated password hasher.
     */
    lazy val barHasher = hasher("bar")

    /**
     * The auth info repository mock.
     */
    lazy val authInfoRepository = mock[AuthInfoRepository]

    /**
     * The password hasher registry.
     */
    lazy val passwordHasherRegistry = new PasswordHasherRegistry(fooHasher, Seq(barHasher))

    /**
     * Helper method to create a hasher mock.
     *
     * @param id The ID of the hasher.
     * @return A hasher mock.
     */
    private def hasher(id: String) = {
      val h = mock[PasswordHasher]
      h.id returns id
      h.isSuitable(any()) answers { p: Any =>
        p.asInstanceOf[PasswordInfo].hasher == h.id
      }
      h.isDeprecated(any()) returns Some(false)
      h
    }
  }
}
