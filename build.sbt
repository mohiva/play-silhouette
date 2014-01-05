import play.Project._

name := "silhouette"

version := "master-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  "com.typesafe" %% "play-plugins-util" % "2.2.0",
  "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.mockito" % "mockito-core" % "1.9.5" % "test"
)

resolvers ++= Seq(
  Resolver.typesafeRepo("releases")
)

publishMavenStyle := false

publishArtifact in Test := false

parallelExecution in Test := false

publishTo <<= (version) { v: String =>
  val status = if(v.trim.endsWith("-SNAPSHOT")) "snapshots" else "releases"
  Some(Resolver.sbtPluginRepo(status))
}

playScalaSettings ++ ScoverageSbtPlugin.instrumentSettings

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked"
)

CoverallsPlugin.singleProject
