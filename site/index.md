---
layout: default
title: Silhouette
---

[![Build Status](https://travis-ci.org/mohiva/play-silhouette.png)](https://travis-ci.org/mohiva/play-silhouette) [![Coverage Status](https://coveralls.io/repos/mohiva/play-silhouette/badge.png)](https://coveralls.io/r/mohiva/play-silhouette) [![Doc](https://readthedocs.org/projects/silhouette/badge/?version=latest)](http://docs.silhouette.mohiva.com/en/latest/)

**Silhouette** is an authentication library for Play Framework applications that supports several authentication
methods, including OAuth1, OAuth2, OpenID, Credentials, Basic Authentication or custom authentication schemes.

It can be integrated as is, or used as a building block and customized to meet specific application requirements,
thanks to its loosely coupled design.

The project is named after the fictional crime fighter character [Silhouette](http://www.comicvine.com/silhouette/4005-35807/),
from the Watchmen [graphic novel](http://en.wikipedia.org/wiki/Watchmen) and [movie](http://en.wikipedia.org/wiki/Watchmen_%28film%29).


## Features

#### Easy to integrate

Silhouette comes with either an [Activator template for traditional web pages](https://github.com/mohiva/play-silhouette-seed)
or an [Activator template for single-page applications](https://github.com/mohiva/play-silhouette-angular-seed)
that gives you a complete sample application which is 100% customizable. Simply select the template ``play-silhouette-seed``
or ``play-silhouette-angular-seed`` in your Activator UI. It has never been easier to start a new Silhouette
application.

For other examples, please visit the [Examples](http://docs.silhouette.mohiva.com/en/latest/basics/examples.html)
section in the [Documentation](http://docs.silhouette.mohiva.com/).

#### Authentication support

Out of the box support for leading social services such as Twitter, Facebook, Google, LinkedIn and GitHub. Silhouette
also includes a credentials and basic authentication provider that supports local login functionality.

#### Client agnostic

Silhouette comes with a set of stateless as well as stateful [authenticator implementations](http://docs.silhouette.mohiva.com/en/latest/how-it-works/authenticator.html#list-of-authenticators)
which allows an application to handle a wide range of different clients like traditional web browsers as also
native(desktop, mobile, ...) apps.

#### Asynchronous, non-blocking operations

We follow the [Reactive Manifesto](http://www.reactivemanifesto.org/). This means that all requests and web service
calls are asynchronous, non-blocking operations. For the event handling part of Silhouette we use
[Akka's Event Bus](http://doc.akka.io/docs/akka/2.2.4/scala/event-bus.html) implementation. Lastly, all
persistence interfaces are defined to return Scala Futures.

#### Very customizable, extendable and testable

From the ground up Silhouette was designed to be as customizable, extendable and testable as possible. All components
can be enhanced via inheritance or replaced based on their traits, thanks to its loosely coupled design.

#### Internationalization support

Silhouette makes it very easy to internationalize your application by making the Play Framework's ``Request`` and
``Lang`` available where they are needed.

#### Well tested

Silhouette is a security component which protects your users from being compromised by attackers. Therefore we aim
for complete code coverage with unit and integration tests.

#### Follows the OWASP Authentication Cheat Sheet

Silhouette implements and promotes best practices such as described by the [OWASP Authentication Cheat Sheet](https://www.owasp.org/index.php/Authentication_Cheat_Sheet)
like Password Strength Controls, SSL Client Authentication or use of authentication protocols that require no password.

## Documentation

See [the project documentation](http://docs.silhouette.mohiva.com/en/latest/) for more information. If you need help
with the integration of Silhouette into your project, don't hesitate to ask questions in our
[mailing list](https://groups.google.com/forum/#!forum/play-silhouette) or on
[Stack Overflow](http://stackoverflow.com/questions/tagged/playframework).

## License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0) and the documentation
under [CC BY 3.0](http://creativecommons.org/licenses/by/3.0/).

Unless otherwise stated, all artifacts are Copyright 2014 Mohiva Organisation (license at mohiva dot com).
