import sbt._

object Build extends Build {

  val silhouette = Project(
    id = "silhouette",
    base = file("silhouette")
  )

  val silhouetteTestkit = Project(
    id = "silhouette-testkit",
    base = file("silhouette-testkit"),
    dependencies = Seq(silhouette)
  )

  val root = Project(
    id = "root",
    base = file("."),
    aggregate = Seq(silhouette, silhouetteTestkit)
  )

}
