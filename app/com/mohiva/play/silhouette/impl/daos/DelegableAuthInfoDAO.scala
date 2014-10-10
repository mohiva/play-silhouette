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
package com.mohiva.play.silhouette.impl.daos

import com.mohiva.play.silhouette.api.services.AuthInfo

import scala.reflect.ClassTag

/**
 * An implementation of the auth info DAO.
 *
 * This abstract implementation of the [[com.mohiva.play.silhouette.impl.daos.AuthInfoDAO]] trait
 * allows us to get the class tag of the auth info it is responsible for. Based on the class tag
 * the [[com.mohiva.play.silhouette.impl.services.DelegableAuthInfoService]] class can delegate
 * operations to the DAO which is responsible for the currently handled auth info.
 *
 * @param classTag The class tag for the type parameter.
 * @tparam T The type of the auth info to store.
 */
abstract class DelegableAuthInfoDAO[T <: AuthInfo](implicit val classTag: ClassTag[T]) extends AuthInfoDAO[T]
