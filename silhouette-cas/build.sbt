import Dependencies._

libraryDependencies ++= Seq(  
  Library.casClient,
  Library.casClientSupportSAML,  
  Library.Play.specs2 % Test,
  Library.Specs2.matcherExtra % Test,
  Library.Specs2.mock % Test,
  Library.scalaGuice % Test
)

enablePlugins(Doc)
