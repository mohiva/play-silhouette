import Dependencies._

libraryDependencies ++= Seq(
  Library.jbcrypt,
  Library.Specs2.core % Test
)

enablePlugins(Doc)
