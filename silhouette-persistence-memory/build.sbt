import Dependencies._

libraryDependencies ++= Seq(
  Library.scalaGuice,
  Library.Specs2.core % Test
)

enablePlugins(Doc)
