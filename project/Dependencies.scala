import sbt._

object Dependencies {

  object Versions {
    val crossScala = Seq("2.11.4", "2.10.4")
    val scalaVersion = crossScala.head
  }

  val resolvers = Seq(
    "Atlassian Releases" at "https://maven.atlassian.com/public/"
  )

  object Library {

    object Play {
      val version = play.core.PlayVersion.current
      val ws = "com.typesafe.play" %% "play-ws" % version
      val cache = "com.typesafe.play" %% "play-cache" % version
      val test = "com.typesafe.play" %% "play-test" % version
    }

    val jbcrypt = "org.mindrot" % "jbcrypt" % "0.3m"
    val jwtCore = "com.atlassian.jwt" % "jwt-core" % "1.2.3"
    val jwtApi = "com.atlassian.jwt" % "jwt-api" % "1.2.3"
    val mockito = "org.mockito" % "mockito-core" % "1.9.5"
    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.0.0-beta5"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.3.3"
  }
}
