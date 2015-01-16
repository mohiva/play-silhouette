Releases
========

Release Versions
^^^^^^^^^^^^^^^^

Silhouette 1.0
--------------

This is the current stable release and it's available for Scala 2.10 / 2.11 and Play 2.3.

Documentation
`````````````

  * :silhouette-doc:`HTML <1.0>`
  * :silhouette-htmlzip-doc:`HTML (Zip) <1.0>`
  * :silhouette-pdf-doc:`PDF <1.0>`
  * :silhouette-epub-doc:`EPUB <1.0>`
  * :silhouette-api-doc:`API <1.0>`

Installation
````````````

To install this version add the following dependency to your build.sbt file.

.. code-block:: scala

    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette" % "1.0"
    )

Silhouette 0.9
--------------

This is the previous stable version and it's available for Scala 2.10 and Play 2.2.

Documentation
`````````````

  * :silhouette-doc:`HTML <0.9>`
  * :silhouette-htmlzip-doc:`HTML (Zip) <0.9>`
  * :silhouette-pdf-doc:`PDF <0.9>`
  * :silhouette-epub-doc:`EPUB <0.9>`
  * :silhouette-api-doc:`API <0.9>`

Installation
````````````

To install this version add the following dependency to your build.sbt file.

.. code-block:: scala

    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette" % "0.9"
    )

Snapshot Version
^^^^^^^^^^^^^^^^

Automatically published documentation for the latest SNAPSHOT version of Silhouette can be found here:

Silhouette 2.0-SNAPSHOT
-----------------------

This version is available for Scala 2.10 / 2.11 and Play 2.3.

Documentation
`````````````

  * :silhouette-doc:`HTML <latest>`
  * :silhouette-htmlzip-doc:`HTML (Zip) <latest>`
  * :silhouette-pdf-doc:`PDF <latest>`
  * :silhouette-epub-doc:`EPUB <latest>`
  * :silhouette-api-doc:`API <2.0-SNAPSHOT>`

Installation
````````````

To install this version add the following dependency to your build.sbt file.

.. code-block:: scala

    resolvers += Resolver.sonatypeRepo("snapshots")

    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette" % "2.0-SNAPSHOT",
      "com.mohiva" %% "play-silhouette-testkit" % "2.0-SNAPSHOT" % "test"
    )


Notes
^^^^^

The ``master`` branch contains the current development version. It
should be working and passing all tests at any time, but it’s unstable
and represents a work in progress.

Released versions are indicated by tags.

Release numbers will follow `Semantic Versioning`_.

.. _Semantic Versioning: http://semver.org/
