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
import sbt._

object Dependencies {

  object Versions {
    val crossScala = Seq("2.12.3", "2.11.11")
    val scalaVersion = crossScala.head
  }

  val resolvers = Seq(
    "Atlassian Releases" at "https://maven.atlassian.com/public/"
  )

  object Library {

    object Play {
      val version = play.core.PlayVersion.current
      val ws = "com.typesafe.play" %% "play-ws" % version
      val cache = "com.typesafe.play" %% "play-cache" % version
      val test = "com.typesafe.play" %% "play-test" % version
      val specs2 = "com.typesafe.play" %% "play-specs2" % version
      val openid = "com.typesafe.play" %% "play-openid" % version
      val jsonJoda = "com.typesafe.play" %% "play-json-joda" % version
      object Specs2 {
        private val version = "3.8.9"
        val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
        val mock = "org.specs2" %% "specs2-mock" % version
      }
    }

    object Specs2 {
      private val version = "3.8.9"
      val core = "org.specs2" %% "specs2-core" % version
      val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
      val mock = "org.specs2" %% "specs2-mock" % version
    }

    val jbcrypt = "de.svenkubiak" % "jBCrypt" % "0.4.1"
    val jwtCore = "com.atlassian.jwt" % "jwt-core" % "1.6.1"
    val jwtApi = "com.atlassian.jwt" % "jwt-api" % "1.6.1"
    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.1.0"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.5.4"
    val casClient = "org.jasig.cas.client" % "cas-client-core" % "3.4.1"
    val casClientSupportSAML = "org.jasig.cas.client" % "cas-client-support-saml" % "3.4.1"
  }
}
