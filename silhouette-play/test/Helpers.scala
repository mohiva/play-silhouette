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
package test

import org.specs2.execute.{ AsResult, Result }
import org.specs2.mutable.Around
import play.api.Play
import play.api.Play.current
import play.api.libs.json.{ JsValue, Json }

import scala.io.{ Codec, Source }

/**
 * Executes a before method in the context of the around method.
 */
trait BeforeWithinAround extends Around {
  def before: Any
  abstract override def around[T: AsResult](t: => T): Result = super.around {
    before; t
  }
}

/**
 * Executes an after method in the context of the around method.
 */
trait AfterWithinAround extends Around {
  def after: Any
  abstract override def around[T: AsResult](t: => T): Result = super.around {
    try { t } finally { after }
  }
}

/**
 * Executes before and after methods in the context of the around method.
 */
trait BeforeAfterWithinAround extends Around {
  def before: Any
  def after: Any
  abstract override def around[T: AsResult](t: => T): Result = super.around {
    try { before; t } finally { after }
  }
}

/**
 * Some test-related helper methods.
 */
object Helper {

  /**
   * Loads a JSON file from class path.
   *
   * @param file The file to load.
   * @return The JSON value.
   */
  def loadJson(file: String): JsValue = {
    Play.application.resourceAsStream(file) match {
      case Some(is) => Json.parse(Source.fromInputStream(is)(Codec.UTF8).mkString)
      case None => throw new Exception("Cannot load file: " + file)
    }
  }
}
