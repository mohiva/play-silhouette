package com.mohiva

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * An authentication library for Play Framework applications that supports several authentication methods,
 * including OAuth1, OAuth2, OpenID, Credentials or custom authentication schemes.
 */
package object silhouette {

  /**
   * Provides an `from` method on a [[scala.concurrent.Future]] which maps a [[scala.util.Try]] to
   * a [[scala.concurrent.Future]].
   *
   * @see https://groups.google.com/forum/#!topic/scala-user/Mu4_lZAWxz0/discussion
   * @see http://stackoverflow.com/questions/17907772/scala-chaining-futures-try-blocks
   */
  implicit class FutureWithFromTry(f: Future.type) {
    def from[T](result: Try[T]): Future[T] = result match {
      case Success(v) => Future.successful(v)
      case Failure(v) => Future.failed(v)
    }
  }
}
