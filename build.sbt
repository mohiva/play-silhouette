import play.PlayScala
import play.core.PlayVersion
import mohiva.sbt.Helper._
import com.typesafe.sbt.SbtScalariform._
import xerial.sbt.Sonatype._

import scalariform.formatter.preferences.{PreserveDanglingCloseParenthesis, DoubleIndentClassDeclaration, FormatXml}

//*******************************
// Play settings
//*******************************

name := "play-silhouette"

version := "2.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.mindrot" % "jbcrypt" % "0.3m",
  "com.atlassian.jwt" % "jwt-core" % "1.2.1",
  "com.atlassian.jwt" % "jwt-api" % "1.2.1",
  "org.mockito" % "mockito-core" % "1.9.5" % "test",
  "net.codingwell" %% "scala-guice" % "4.0.0-beta4" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test",
  cache,
  ws
)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

//*******************************
// Coveralls settings
//*******************************

instrumentSettings

CoverallsPlugin.coverallsSettings

//*******************************
// Maven settings
//*******************************

sonatypeSettings

organization := "com.mohiva"

description := "Authentication library for Play Framework applications that supports several authentication methods, including OAuth1, OAuth2, OpenID, Credentials or custom authentication schemes"

homepage := Some(url("http://silhouette.mohiva.com/"))

licenses := Seq("Apache License" -> url("https://github.com/mohiva/play-silhouette/blob/master/LICENSE"))

val pom = <scm>
    <url>git@github.com:mohiva/play-silhouette.git</url>
    <connection>scm:git:git@github.com:mohiva/play-silhouette.git</connection>
  </scm>
  <developers>
    <developer>
      <id>akkie</id>
      <name>Christian Kaps</name>
      <url>http://mohiva.com</url>
    </developer>
    <developer>
      <id>fernandoacorreia</id>
      <name>Fernando Correia</name>
      <url>http://www.fernandocorreia.info/</url>
    </developer>
  </developers>

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := pom

credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")

//*******************************
// Test settings
//*******************************

parallelExecution in Test := false

//*******************************
// Compiler settings
//*******************************

scalaVersion := "2.11.1"

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
  "-Ywarn-numeric-widen" // Warn when numerics are widened.
)

scalacOptions in Test ~= { (options: Seq[String]) =>
  options filterNot ( _ == "-Ywarn-dead-code" )  // Allow dead code in tests (to support using mockito).
}

scalacOptions in ScoverageTest ~= { (options: Seq[String]) =>
  options filterNot ( _ == "-Ywarn-dead-code" )  // The same when running under scoverage.
}

//*******************************
// Scalariform settings
//*******************************

defaultScalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(FormatXml, false)
  .setPreference(DoubleIndentClassDeclaration, false)
  .setPreference(PreserveDanglingCloseParenthesis, true)

//*******************************
// ScalaDoc settings
//*******************************

autoAPIMappings := true

apiURL := Some(url(s"http://silhouette.mohiva.com/api/$version/"))

apiMappings ++= {
  implicit val cp = (fullClasspath in Compile).value
  Map (
    jarFor("com.typesafe.play", "play") -> url(s"http://www.playframework.com/documentation/${PlayVersion.current}/api/scala/"),
    scalaInstance.value.libraryJar      -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/")
  )
}
