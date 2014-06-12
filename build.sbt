import play.Project._
import mohiva.sbt.Helper._
import com.typesafe.sbt.SbtScalariform._
import xerial.sbt.Sonatype._

//*******************************
// Play settings
//*******************************

name := "play-silhouette"

version := "0.9"

libraryDependencies ++= Seq(
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.mockito" % "mockito-core" % "1.9.5" % "test",
  "com.google.inject" % "guice" % "4.0-beta" % "test",
  "net.codingwell" %% "scala-guice" % "4.0.0-beta" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test",
  cache
)

playScalaSettings

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

//*******************************
// ScalaDoc settings
//*******************************

autoAPIMappings := true

apiURL := Some(url(s"http://silhouette.mohiva.com/api/$version/"))

apiMappings ++= {
  implicit val cp = (fullClasspath in Compile).value
  Map (
    jarFor("com.typesafe.play", "play") -> url(s"http://www.playframework.com/documentation/${playVersion.value}/api/scala/"),
    scalaInstance.value.libraryJar      -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/")
  )
}
