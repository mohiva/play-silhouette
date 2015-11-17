package com.mohiva.play.silhouette.impl.providers

import scala.reflect.ClassTag
import scala.concurrent.Future
import org.specs2.matcher.MatchResult
import play.api.test.PlaySpecification
import play.api.mvc.Result
import com.mohiva.play.silhouette.api.services.AuthInfo
import com.mohiva.play.silhouette.impl.providers.cas.CASAuthInfo

/**
 * @author nshaw
 */
trait SharedSpecification[A <: AuthInfo] extends PlaySpecification {
  
  /**
   * Applies a matcher on a simple result.
   *
   * @param providerResult The result from the provider.
   * @param b The matcher block to apply.
   * @return A specs2 match result.
   */
  def result(providerResult: Future[Either[Result, A]])(b: Future[Result] => MatchResult[_]) = {
    await(providerResult) must beLeft[Result].like {
      case result => b(Future.successful(result))
    }
  }
  
  /**
   * Matches a partial function against a failure message.
   *
   * This method checks if an exception was thrown in a future.
   * @see https://groups.google.com/d/msg/specs2-users/MhJxnvyS1_Q/FgAK-5IIIhUJ
   *
   * @param providerResult The result from the provider.
   * @param f A matcher function.
   * @return A specs2 match result.
   */
  def failed[E <: Throwable: ClassTag](providerResult: Future[_])(f: => PartialFunction[Throwable, MatchResult[_]]) = {
    implicit class Rethrow(t: Throwable) {
      def rethrow = { throw t; t }
    }

    lazy val result = await(providerResult.failed)

    result must not(throwAn[E])
    result.rethrow must throwAn[E].like(f)
  }
  
  /**
   * Applies a matcher on a auth info.
   *
   * @param providerResult The result from the provider.
   * @param b The matcher block to apply.
   * @return A specs2 match result.
   */
  def authInfo(providerResult: Future[Either[Result, A]])(b: A => MatchResult[_]) = {
    await(providerResult) must beRight[A].like {
      case authInfo => b(authInfo)
    }
  }
  
}