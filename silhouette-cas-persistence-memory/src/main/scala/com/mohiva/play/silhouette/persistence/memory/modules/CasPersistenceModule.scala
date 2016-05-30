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
package com.mohiva.play.silhouette.persistence.memory.modules

import javax.inject

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.impl.providers.CasInfo
import com.mohiva.play.silhouette.persistence.daos._
import com.mohiva.play.silhouette.persistence.memory.daos.CasInfoDAO
import net.codingwell.scalaguice.ScalaModule

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Provides Guice bindings for the CAS persistence module.
 */
class CasPersistenceModule extends AbstractModule with ScalaModule {

  /**
   * Configures the module.
   */
  def configure(): Unit = {
    bind[DelegableAuthInfoDAO[CasInfo]].to[CasInfoDAO].in[inject.Singleton]
  }
}
