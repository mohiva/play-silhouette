import Dependencies._

libraryDependencies ++= Seq(
  Library.Specs2.core % Test,
  Library.Specs2.matcherExtra % Test
)

enablePlugins(Doc)
