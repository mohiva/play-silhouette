resolvers ++= Seq(
    Classpaths.typesafeResolver,
    "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"
)

addSbtPlugin("reaktor" % "sbt-scct" % "0.2-SNAPSHOT")

addSbtPlugin("com.github.theon" %% "xsbt-coveralls-plugin" % "0.0.4")
