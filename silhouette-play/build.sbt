//import play.PlayScala
//import play.core.PlayVersion
//import mohiva.sbt.Helper._
//import com.typesafe.sbt.SbtScalariform._
//import xerial.sbt.Sonatype._
//
//import scalariform.formatter.preferences.{PreserveDanglingCloseParenthesis, DoubleIndentClassDeclaration, FormatXml}
//
////*******************************
//// Play settings
////*******************************

libraryDependencies ++= Dependencies.playDependencies

enablePlugins(PlayScala)
//
////*******************************
//// Coveralls settings
////*******************************
//
//instrumentSettings
//
//CoverallsPlugin.coverallsSettings
//
////*******************************
//// Maven settings
////*******************************
//
//sonatypeSettings
//
////*******************************
//// Test settings
////*******************************
//
//parallelExecution in Test := false
//
////*******************************
//// Compiler settings
////*******************************
//
//scalaVersion := "2.11.1"
//
//crossScalaVersions := Seq("2.10.4", "2.11.1")
//
//scalacOptions ++= Seq(
//  "-deprecation", // Emit warning and location for usages of deprecated APIs.
//  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
//  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
//  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
//  "-Xlint", // Enable recommended additional warnings.
//  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
//  "-Ywarn-dead-code", // Warn when dead code is identified.
//  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
//  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
//  "-Ywarn-numeric-widen" // Warn when numerics are widened.
//)
//
//scalacOptions in Test ~= { (options: Seq[String]) =>
//  options filterNot ( _ == "-Ywarn-dead-code" )  // Allow dead code in tests (to support using mockito).
//}
//
//scalacOptions in ScoverageTest ~= { (options: Seq[String]) =>
//  options filterNot ( _ == "-Ywarn-dead-code" )  // The same when running under scoverage.
//}
//
////*******************************
//// Scalariform settings
////*******************************
//
//defaultScalariformSettings
//
//ScalariformKeys.preferences := ScalariformKeys.preferences.value
//  .setPreference(FormatXml, false)
//  .setPreference(DoubleIndentClassDeclaration, false)
//  .setPreference(PreserveDanglingCloseParenthesis, true)
//
////*******************************
//// ScalaDoc settings
////*******************************
//
//autoAPIMappings := true
//
//apiURL := Some(url(s"http://silhouette.mohiva.com/api/$version/"))
//
//apiMappings ++= {
//  implicit val cp = (fullClasspath in Compile).value
//  Map (
//    jarFor("com.typesafe.play", "play") -> url(s"http://www.playframework.com/documentation/${PlayVersion.current}/api/scala/"),
//    scalaInstance.value.libraryJar      -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/")
//  )
//}
