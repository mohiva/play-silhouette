// Comment to get more information during initialization
logLevel := Level.Warn

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.5.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.3")
