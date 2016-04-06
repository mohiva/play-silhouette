package com.mohiva.play.silhouette.test

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers.FutureMatchable
import org.specs2.matcher.Matcher

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.languageFeature.implicitConversions

trait WaitPatience {

  def retries = 10

  def timeout = 1.second

  implicit class WaitWithPatienceFutureMatchable[T](m: Matcher[T])(implicit ee: ExecutionEnv) extends FutureMatchable[T](m)(ee) {
    def awaitWithPatience: Matcher[Future[T]] = {
      await(retries, timeout)
    }
  }
}
