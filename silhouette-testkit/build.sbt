import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.test,
  Library.Play.specs2 % Test,
  Library.Specs2.matcherExtra % Test,
  Library.Specs2.mock % Test,
  Library.scalaGuice % Test,
  Library.akkaTestkit % Test
)

enablePlugins(PlayScala, Doc)
