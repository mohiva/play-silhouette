/**
 * Licensed to the Minutemen Group under one or more contributor license
 * agreements. See the COPYRIGHT file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
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
  val resolvers = Seq(Resolver.sonatypeRepo("snapshots"))

  object Versions {
    val silhouette = "1.0.0-SNAPSHOT"
    val play = _root_.play.core.PlayVersion.current
    val specs2 = "4.3.6"
  }

  object Library {
    object Play {
      val core = "com.typesafe.play" %% "play" % Versions.play
      val ws = "com.typesafe.play" %% "play-ws" % Versions.play
      val test = "com.typesafe.play" %% "play-test" % Versions.play
      val specs2 = "com.typesafe.play" %% "play-specs2" % Versions.play
    }

    object Silhouette {
      val core = "group.minutemen" %% "silhouette-core" % Versions.silhouette
      val http = "group.minutemen" %% "silhouette-http" % Versions.silhouette
      val authenticator = "group.minutemen" %% "silhouette-authenticator" % Versions.silhouette
      val authorization = "group.minutemen" %% "silhouette-authorization" % Versions.silhouette
      val provider = "group.minutemen" %% "silhouette-provider" % Versions.silhouette
      val specs2 = "group.minutemen" %% "silhouette-specs2" % Versions.silhouette
    }

    object Specs2 {
      val core = "org.specs2" %% "specs2-core" % Versions.specs2
      val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % Versions.specs2
      val mock = "org.specs2" %% "specs2-mock" % Versions.specs2
    }

    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.2.1"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.5.19"
  }
}
