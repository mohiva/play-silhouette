/**
 * Copyright 2014 Christian Kaps (christian.kaps at mohiva dot com)
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
package test

import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around

/**
 * Executes a before method in the context of the around method.
 */
trait BeforeWithinAround extends Around {
  def before: Any
  abstract override def around[T : AsResult](t: =>T): Result = super.around {
    try { before; t }
  }
}

/**
 * Executes a after method in the context of the around method.
 */
trait AfterWithinAround extends Around {
  def after: Any
  abstract override def around[T : AsResult](t: =>T): Result = super.around {
    try { t } finally { after }
  }
}

/**
 * Executes before and after methods in the context of the around method.
 */
trait BeforeAfterWithinAround extends Around {
  def before: Any
  def after: Any
  abstract override def around[T : AsResult](t: =>T): Result = super.around {
    try { before; t } finally { after }
  }
}
