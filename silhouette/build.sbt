import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.cache,
  Library.Play.ws,
  Library.Play.openid,
  Library.Play.jsonJoda,
  Library.jwtCore,
  Library.jwtApi,
  Library.apacheCommonLang,
  Library.Play.specs2 % Test,
  Library.Specs2.matcherExtra % Test,
  Library.Specs2.mock % Test,
  Library.scalaGuice % Test,
  Library.akkaTestkit % Test
)

enablePlugins(PlayScala, Doc)
