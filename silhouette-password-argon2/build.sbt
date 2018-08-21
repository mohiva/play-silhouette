import Dependencies._

libraryDependencies ++= Seq(
  Library.argon2Jvm,
  Library.Specs2.core % Test
)

enablePlugins(Doc)
