import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.test,
  Library.mockito % Test,
  Library.akkaTestkit % Test
)

enablePlugins(PlayScala, Doc)
