import Dependencies._

libraryDependencies ++= Seq(  
  Library.casClient,
  Library.casClientSupportSAML,  
  Library.Play.specs2 % Test,
  Library.Play.Specs2.matcherExtra % Test,
  Library.Play.Specs2.mock % Test,
  Library.scalaGuice % Test
)

enablePlugins(Doc)