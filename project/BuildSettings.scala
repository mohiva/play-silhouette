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
import sbt.Keys._
import sbt.{ Credentials, _ }
import xerial.sbt.Sonatype.SonatypeKeys.sonatypePublishTo

////*******************************
//// Basic settings
////*******************************
object BasicSettings extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Seq(
    organization := "group.minutemen",
    version := "7.0.0-SNAPSHOT",
    resolvers ++= Dependencies.resolvers,
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := Seq("2.13.0", "2.12.9"),
    //updateOptions := updateOptions.value.withLatestSnapshots(true),
    scalacOptions ++= Seq(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Xfatal-warnings", // Fail the compilation if there are any warnings.
      "-Xlint", // Enable recommended additional warnings.
      "-Ywarn-dead-code", // Warn when dead code is identified.
      "-Ywarn-numeric-widen" // Warn when numerics are widened.
    ),
    scalacOptions in Test ~= { options: Seq[String] =>
      options filterNot (_ == "-Ywarn-dead-code") // Allow dead code in tests (to support using mockito).
    },
    parallelExecution in Test := false,
    fork in Test := true,
    // Needed to avoid https://github.com/travis-ci/travis-ci/issues/3775 in forked tests
    // in Travis with `sudo: false`.
    // See https://github.com/sbt/sbt/issues/653
    // and https://github.com/travis-ci/travis-ci/issues/3775
    javaOptions += "-Xmx1G"
  )
}

////*******************************
//// Maven settings
////*******************************
object Publish extends AutoPlugin {

  import xerial.sbt.Sonatype._

  override def trigger: PluginTrigger = allRequirements

  private val pom = {
    <scm>
      <url>git@github.com:mohiva/play-silhouette.git</url>
      <connection>scm:git:git@github.com:mohiva/play-silhouette.git</connection>
    </scm>
      <developers>
        <developer>
          <id>akkie</id>
          <name>Christian Kaps</name>
          <url>http://mohiva.com</url>
        </developer>
      </developers>
  }

  override def projectSettings: Seq[Setting[_]] = sonatypeSettings ++ Seq(
    description := "Silhouette binding for the Play Framework",
    homepage := Some(url("http://www.silhouette.rocks/")),
    licenses := Seq("Apache License" -> url("https://github.com/mohiva/play-silhouette/blob/master/LICENSE")),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := pom,
    publishTo := sonatypePublishTo.value,
    credentials ++= (for {
      username <- Option(System.getenv().get("SONATYPE_USERNAME"))
      password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
    } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
  )
}
