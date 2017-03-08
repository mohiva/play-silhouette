package com.mohiva.play.silhouette.impl.providers

import com.mohiva.play.silhouette.api.AuthInfo
import org.specs2.matcher.{ MatchResult }
import play.api.mvc.Result

import scala.concurrent.Future

abstract class SocialStateProviderSpec[A <: AuthInfo, S <: SocialStateItem] extends SocialProviderSpec {

  /**
   * Applies a matcher on a simple result.
   *
   * @param providerResult The result from the provider.
   * @param b The matcher block to apply.
   * @return A specs2 match result.
   */
  def statefulResult(providerResult: Future[Either[Result, StatefulAuthInfo[A, S]]])(b: Future[Result] => MatchResult[_]) = {
    await(providerResult) must beLeft[Result].like {
      case result => b(Future.successful(result))
    }
  }

  /**
   * Applies a matcher on a stateful auth info.
   *
   * @param providerResult The result from the provider.
   * @param b The matcher block to apply.
   * @return A specs2 match result.
   */
  def statefulAuthInfo(providerResult: Future[Either[Result, StatefulAuthInfo[A, S]]])(b: StatefulAuthInfo[A, S] => MatchResult[_]) = {
    await(providerResult) must beRight[StatefulAuthInfo[A, S]].like {
      case statefulAuthInfo => b(statefulAuthInfo)
    }
  }
}