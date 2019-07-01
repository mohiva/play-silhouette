import Dependencies._

libraryDependencies ++= Seq(
  Library.googleAuth,
  Library.Play.specs2 % Test
)

enablePlugins(Doc)
