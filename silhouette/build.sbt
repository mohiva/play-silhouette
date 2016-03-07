import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.cache,
  Library.Play.ws,
  Library.jwtCore,
  Library.jwtApi,
  Library.casClient,
  Library.casClientSupportSAML,
  Library.Play.specs2 % Test,
  Library.Play.Specs2.matcherExtra % Test,
  Library.Play.Specs2.mock % Test,
  Library.scalaGuice % Test,
  Library.akkaTestkit % Test
)

enablePlugins(PlayScala, Doc)
