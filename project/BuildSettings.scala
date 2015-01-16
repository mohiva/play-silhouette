import sbt.Keys._
import sbt._

object BasicSettings extends AutoPlugin {
  override def trigger = allRequirements

  override def projectSettings = Seq(
    organization := "com.mohiva",
    version := "2.0-SNAPSHOT",
    resolvers ++= Dependencies.resolvers,
    scalaVersion := Dependencies.Versions.scalaVersion,
    crossScalaVersions := Dependencies.Versions.crossScala,
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
    parallelExecution in Test := false
  )
}

////*******************************
//// Scalariform settings
////*******************************
object CodeFormatter extends AutoPlugin {

  import com.typesafe.sbt.SbtScalariform._
  import scalariform.formatter.preferences.{DoubleIndentClassDeclaration, FormatXml, PreserveDanglingCloseParenthesis}

  override def trigger = allRequirements

  override def projectSettings = defaultScalariformSettings ++ Seq(
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(FormatXml, false)
      .setPreference(DoubleIndentClassDeclaration, false)
      .setPreference(PreserveDanglingCloseParenthesis, true)
  )
}

////*******************************
//// ScalaDoc settings
////*******************************
object Doc extends AutoPlugin {

  import play.core.PlayVersion

  override def projectSettings = Seq(
    autoAPIMappings := true,
    apiURL := Some(url(s"http://silhouette.mohiva.com/api/$version/")),
    apiMappings ++= {
      implicit val cp = (fullClasspath in Compile).value
      Map(
        jarFor("com.typesafe.play", "play") -> url(s"http://www.playframework.com/documentation/${PlayVersion.current}/api/scala/"),
        scalaInstance.value.libraryJar -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/")
      )
    }
  )

  /**
   * Gets the JAR file for a package.
   *
   * @param organization The organization name.
   * @param name The name of the package.
   * @param cp The class path.
   * @return The file which points to the JAR.
   * @see http://stackoverflow.com/a/20919304/2153190
   */
  private def jarFor(organization: String, name: String)(implicit cp: Seq[Attributed[File]]): File = {
    (for {
      entry <- cp
      module <- entry.get(moduleID.key)
      if module.organization == organization
      if module.name.startsWith(name)
      jarFile = entry.data
    } yield jarFile).head
  }
}

////*******************************
//// Maven settings
////*******************************
object Publish extends AutoPlugin {

  import xerial.sbt.Sonatype._

  override def trigger = allRequirements

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
        <developer>
          <id>fernandoacorreia</id>
          <name>Fernando Correia</name>
          <url>http://www.fernandocorreia.info/</url>
        </developer>
      </developers>
  }

  override def projectSettings = sonatypeSettings ++ Seq(
    description := "Authentication library for Play Framework applications that supports several authentication methods, including OAuth1, OAuth2, OpenID, Credentials or custom authentication schemes",
    homepage := Some(url("http://silhouette.mohiva.com/")),
    licenses := Seq("Apache License" -> url("https://github.com/mohiva/play-silhouette/blob/master/LICENSE")),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false},
    pomExtra := pom,
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials")
  )
}
