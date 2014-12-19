import sbt.Keys._
import sbt._

object BuildSettings {

  object Basic {
    val settings = Seq(
      organization := "com.mohiva",
      version := "2.0-SNAPSHOT",
      scalaVersion := Dependencies.Versions.scalaVersion,
      crossScalaVersions := Dependencies.Versions.crossScala,
      description := "Authentication library for Play Framework applications that supports several authentication methods, including OAuth1, OAuth2, OpenID, Credentials or custom authentication schemes",
      homepage := Some(url("http://silhouette.mohiva.com/")),
      licenses := Seq("Apache License" -> url("https://github.com/mohiva/play-silhouette/blob/master/LICENSE")),
      //      resolvers ++= Dependencies.resolvers,
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
      ),
      scalacOptions in Test ~= { (options: Seq[String]) =>
        options filterNot (_ == "-Ywarn-dead-code") // Allow dead code in tests (to support using mockito).
      },
      //      scalacOptions in ScoverageTest ~= { (options: Seq[String]) =>
      //        options filterNot ( _ == "-Ywarn-dead-code" )  // The same when running under scoverage.
      //      },
      javacOptions ++= Seq(
        "-source", "1.7",
        "-target", "1.7"
      ),
      parallelExecution in Test := false,
      shellPrompt := { s => Project.extract(s).currentProject.id + " > "}
    )
  }

  object Publish extends AutoPlugin {

    override lazy val projectSettings = Seq(
      pomExtra := silhouettePomExtra,
      credentials += silhouetteCredentials,
      organizationName := "Typesafe Inc.",
      organizationHomepage := Some(url("http://www.typesafe.com")),
      publishMavenStyle := true,
      publishArtifact in Test := false,
      pomIncludeRepository := { _ => false}
    )

    val silhouettePomExtra = {
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
          <developer>
            <id>fernandoacorreia</id>
            <name>Fernando Correia</name>
            <url>http://www.fernandocorreia.info/</url>
          </developer>
        </developers>
    }

    private def silhouetteCredentials: Credentials = {
      Option(System.getProperty("silhouette.publish.credentials", null))
        .map(f => Credentials(new File(f)))
        .getOrElse(Credentials(Path.userHome / ".sbt" / "sonatype.credentials"))
    }

  }

  object Formatting {

  }
}
