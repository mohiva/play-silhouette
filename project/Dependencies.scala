import sbt._

object Dependencies {

  object Versions {
    val crossScala = Seq("2.10.4", "2.11.4")
    val scalaVersion = crossScala.head
  }

  private val jodaTime = "joda-time" % "joda-time" % "2.6"
  private val jodaConvert = "org.joda" % "joda-convert" % "1.7"
  private val jbcrypt = "org.mindrot" % "jbcrypt" % "0.3m"
  private val jwtCore = "com.atlassian.jwt" % "jwt-core" % "1.2.1"
  private val jwtApi = "com.atlassian.jwt" % "jwt-api" % "1.2.1"

  private object Akka {
    private val version = "2.3.8"
    val actor = "com.typesafe.akka" %% "akka-actor" % version
    val testkit = "com.typesafe.akka" %% "akka-testkit" % version
    val http = "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "1.0-M1"
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Dependecies
  // -------------------------------------------------------------------------------------------------------------------
  val coreDependencies = Seq(
    jodaTime, jodaConvert,
    jbcrypt,
    jwtCore, jwtApi,
    Akka.actor % "provided"
  )

  val playDependencies = Seq(
    "com.typesafe.play" %% "play-test" % "2.3.6",
    "org.mockito" % "mockito-core" % "1.9.5" % "test",
    "net.codingwell" %% "scala-guice" % "4.0.0-beta4" % "test",
    Akka.testkit % "test",
    play.PlayImport.cache,
    play.PlayImport.ws
  )

  val playTestkitDependecies = Seq(
    "com.typesafe.play" %% "play-test" % "2.3.6"
  )

  val akkaHttpDependencies = Seq(
    Akka.http
  )
}
