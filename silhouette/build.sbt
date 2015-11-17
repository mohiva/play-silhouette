import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.cache,
  Library.Play.ws,
  Library.jbcrypt,
  Library.jwtCore,
  Library.jwtApi,
  Library.casClient,
  Library.casClientSupportSAML,
  Library.mockito % Test,
  Library.scalaGuice % Test,
  Library.akkaTestkit % Test
)

enablePlugins(PlayScala, Doc)
