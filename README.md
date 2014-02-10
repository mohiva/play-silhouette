Silhouette [![Build Status](https://travis-ci.org/mohiva/play-silhouette.png)](https://travis-ci.org/mohiva/play-silhouette) [![Coverage Status](https://coveralls.io/repos/mohiva/play-silhouette/badge.png)](https://coveralls.io/r/mohiva/play-silhouette)
==========

**Silhouette** is an authentication library for Play Framework applications that supports several authentication methods, including OAuth, OAuth2, OpenID and password. It is a fork of [SecureSocial](http://securesocial.ws/), the widely known authentication module for Play Framework applications, with the intention to built a more customizable, non-blocking and well tested implementation.

It can be integrated as is, or used as a building block and customized to meet specific application requirements, thanks to its loosely coupled design.

The project is named after the fictional crime fighter character [Silhouette](http://www.comicvine.com/silhouette/4005-35807/), from the Watchmen [graphic novel](http://en.wikipedia.org/wiki/Watchmen) and [movie](http://en.wikipedia.org/wiki/Watchmen_%28film%29).


## Features

* Asynchronous, non-blocking operations
* Customizable: components can be enhanced via inheritance or replaced based on their traits
* Internationalization support
* Non-disclosure of sensitive data (such as existence of a given user)
* Optional user registration support
* Persistence agnostic
* Scala API
* Testable


## Installation

Note: The module is currently only available as snapshot.

In your build.sbt:
```scala
libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "1.0"
)
```

If you want to use the latest snapshot, add the following instead:
```scala
resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "1.0-SNAPSHOT"
)
```


## Documentation

See [the project wiki](https://github.com/mohiva/play-silhouette/wiki) for more information.


## License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0) and the documentation under [CC BY 3.0](http://creativecommons.org/licenses/by/3.0/).

Unless otherwise stated, all artifacts are Copyright 2014 Mohiva Organisation (license at mohiva dot com).

This project is derived from [SecureSocial](https://github.com/jaliss/securesocial), Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss. Thanks to [Jorge Aliss](https://github.com/jaliss) for his great work.
