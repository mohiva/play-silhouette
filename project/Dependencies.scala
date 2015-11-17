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
    val crossScala = Seq("2.11.6", "2.10.5")
    val scalaVersion = crossScala.head
  }

  val resolvers = Seq(
    "Atlassian Releases" at "https://maven.atlassian.com/public/",
    "Sonatype snapshots repository" at "https://oss.sonatype.org/content/repositories/snapshots/"
  )

  object Library {

    object Play {
      val version = play.core.PlayVersion.current
      val ws = "com.typesafe.play" %% "play-ws" % version
      val cache = "com.typesafe.play" %% "play-cache" % version
      val test = "com.typesafe.play" %% "play-test" % version
    }

    val jbcrypt = "org.mindrot" % "jbcrypt" % "0.3m"
    val jwtCore = "com.atlassian.jwt" % "jwt-core" % "1.2.3"
    val jwtApi = "com.atlassian.jwt" % "jwt-api" % "1.2.3"
    val mockito = "org.mockito" % "mockito-core" % "1.9.5"
    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.0.0-beta5"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.3.3"
    
    //val pac4jCAS = "org.pac4j" % "pac4j-cas" % "1.8.0-RC1"
    //val pac4jPlay = "org.pac4j" % "play-pac4j-java" % "2.0.0-SNAPSHOT"
    val casClient = "org.jasig.cas.client" % "cas-client-core" % "3.4.1"
    val casClientSupportSAML = "org.jasig.cas.client" % "cas-client-support-saml" % "3.4.1"
    val inject = "javax-inject" % "javax-inject"
  }
}
