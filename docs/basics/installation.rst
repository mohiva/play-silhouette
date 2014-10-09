Installation
============

**For Play 2.2 use version 0.9**

.. code-block:: scala

    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette" % "0.9"
    )

**For Play 2.3 use version 1.0**

This version is cross compiled against Scala 2.10.4 and 2.11.0

.. code-block:: scala

    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette" % "1.0"
    )

If you want to use the latest snapshot, add the following instead:

.. code-block:: scala

    resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette" % "2.0-SNAPSHOT"
    )
