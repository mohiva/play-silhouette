import sbt._

object Build extends Build {

  val core = Project(
    id = "silhouette-core",
    base = file("silhouette-core")
  )

  val play = Project(
    id = "silhouette-play",
    base = file("silhouette-play"),
    dependencies = Seq(core)
  )

  val playTestkit = Project(
    id = "silhouette-play-testkit",
    base = file("silhouette-play-testkit"),
    dependencies = Seq(core)
  )

  val akkaHttp = Project(
    id = "silhouette-akka-http",
    base = file("silhouette-akka-http"),
    dependencies = Seq(core)
  )

  val root = Project(
    id = "silhouette",
    base = file("."),
    aggregate = Seq(core, play, playTestkit, akkaHttp)
  )

}
