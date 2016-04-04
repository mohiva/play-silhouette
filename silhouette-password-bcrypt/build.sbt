import Dependencies._

libraryDependencies ++= Seq(
  Library.jbcrypt,
  Library.Specs2.core % Test,
  Library.Play.Specs2.mock % Test
)

enablePlugins(Doc)
