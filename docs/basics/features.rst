Features
========

**Easy to integrate**

Silhouette comes with an `Activator template`_ that gives you a complete
sample application which is 100% customizable. You must only select the
template ``play-silhouette-seed`` in your Activator UI. It was never
been easier to start a new Silhouette application.

**Authentication support**

Out of the box support for leading social services such as Twitter,
Facebook, Google, LinkedIn and GitHub. It also provides a credentials
provider with supports local login functionality.

**Asynchronous, non-blocking operations**

We follow the `Reactive Manifesto`_. This means that all requests and
web service calls are asynchronous, non blocking operations. For the
event handling part of Silhouette we use `Akka’s Event Bus`_
implementation. And lastly all persistence interfaces are defined to
return Scala Futures.

**Very customizable, extendable and testable**

From the ground up Silhouette was designed to be as customizable,
extendable and testable as possible. All components can be enhanced via
inheritance or replaced based on their traits, thanks to its loosely
coupled design.

**Internationalization support**

Silhouette makes it very easy to internationalize your application by
passing the Play Framework ``Request`` and ``Lang`` objects around, if
internationalization comes into play.

**Well tested** |Coverage Status|

Silhouette is a security component which protects your users from being
compromised by attackers. Therefore we try to cover the complete code
with unit and integration tests.

**Follows the OWASP Authentication Cheat Sheet**

Silhouette implements and promotes best practices such as described by
the `OWASP Authentication Cheat Sheet`_ like Password Strength Controls,
SSL Client Authentication or use of authentication protocols that
require no password.

.. _Activator template: https://github.com/mohiva/play-silhouette-seed
.. _Reactive Manifesto: http://www.reactivemanifesto.org/
.. _Akka’s Event Bus: http://doc.akka.io/docs/akka/2.2.4/scala/event-bus.html
.. _OWASP Authentication Cheat Sheet: https://www.owasp.org/index.php/Authentication_Cheat_Sheet

.. |Coverage Status| image:: https://coveralls.io/repos/mohiva/play-silhouette/badge.png
                     :target: https://coveralls.io/r/mohiva/play-silhouette
