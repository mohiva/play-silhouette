import sbt._
import Keys._

object Build extends Build {

  val silhouette = Project(
    id = "play-silhouette",
    base = file("silhouette")
  )

  val silhouetteTestkit = Project(
    id = "play-silhouette-testkit",
    base = file("silhouette-testkit"),
    dependencies = Seq(silhouette)
  )

  val root = Project(
    id = "root",
    base = file("."),
    aggregate = Seq(silhouette, silhouetteTestkit),
    settings = Defaults.coreDefaultSettings ++ Seq(
      publishLocal := {},
      publishM2 := {},
      publishArtifact := false
    )
  )
}
