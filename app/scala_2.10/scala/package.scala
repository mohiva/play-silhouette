package scala

import scala.util.{ Failure, Success, Try }
import scala.language.implicitConversions

/**
 * Package to handle backward compatibility with Scala 2.10.
 *
 * @see http://day-to-day-stuff.blogspot.de/2013/04/fixing-code-and-binary.html
 */
package object concurrent {

  /**
   * Provides an `fromTry` method on a [[scala.concurrent.Future]] which maps a [[scala.util.Try]] to
   * a [[scala.concurrent.Future]].
   *
   * @see https://groups.google.com/forum/#!topic/scala-user/Mu4_lZAWxz0/discussion
   * @see http://stackoverflow.com/questions/17907772/scala-chaining-futures-try-blocks
   */
  implicit class FutureWithFromTry(f: Future.type) {
    def fromTry[T](result: Try[T]): Future[T] = result match {
      case Success(v) => Future.successful(v)
      case Failure(v) => Future.failed(v)
    }
  }
}
