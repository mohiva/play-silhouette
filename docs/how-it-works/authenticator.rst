.. _authenticator_impl:

Authenticator
=============

An `Authenticator`_ is a key part of a Silhouette application, because it tracks
a user after a successful authentication. The Authenticator itself is a small
class which stores only some data like its validity and the linked login information
for an identity.

.. _Authenticator: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/core/Authenticator.scala#L25

.. _authenticator_service_impl:

Authenticator service
---------------------

To every Authenticator pertains an associated service which handles this authenticator.
This service is responsible for the following actions:

Create an authenticator
^^^^^^^^^^^^^^^^^^^^^^^

Creates a new authenticator for the login information of an identity. This step should
be executed after a successful authentication. The created authenticator can then be
used to track the user on every subsequent request.

Retrieve an authenticator
^^^^^^^^^^^^^^^^^^^^^^^^^

Based on the authenticator a service is responsible for, it checks the incoming request
for an existing authenticator artifact. This artifact can be embedded in every part(user
session, cookie, user defined header field, request param) of an incoming HTTP request. Some
services can validate a retrieved authenticator, based on its ID, against a backing store.
Silhouette automatically tries to retrieve an authenticator on every request to a
:ref:`Silhouette action <silhouette_actions>`.

Init an authenticator
^^^^^^^^^^^^^^^^^^^^^

The initialization of an authenticator means, that all authenticator artifacts will be
embedded into a Play framework result. This can by done by creating a cookie, storing
data into the user session or sending the artifacts in a user defined header. If the
service uses a backing store, then the authenticator instance will be stored in it. The
initialization should be done after a successful authentication with a new created
authenticator.

Update an authenticator
^^^^^^^^^^^^^^^^^^^^^^^

Automatically updates the state of an authenticator on every request to a :ref:`Silhouette
action <silhouette_actions>`. Updated authenticator artifacts will be embed into the Play
framework result, before sending it to the client. If the service uses a backing store, then
the authenticator instance will be updated in the store too.

Discard an authenticator
^^^^^^^^^^^^^^^^^^^^^^^^

If an authenticator is invalid then Silhouette automatically discards an authenticator on
every request to a :ref:`Silhouette action <silhouette_actions>`. Discarding means that all
client side stored artifacts will be removed. If the service uses a backing store, then the
authenticator will also be removed from it. To logout a user from a Silhouette application,
the authenticator must also be discarded manually.


List of authenticators
----------------------

We have put a great effort to build a whole set of stateless as well as stateful `authenticator
implementations`_, which cover the most use cases. Now it's up to you to decide which
authenticator fits best into your application architecture.

.. Hint::
  A good decision aid can give you the blog posts `Cookies vs Tokens. Getting auth right with
  Angular.JS`_ and `10 Things You Should Know about Tokens`_ from Auth0.

.. _Cookies vs Tokens. Getting auth right with Angular.JS: https://auth0.com/blog/2014/01/07/angularjs-authentication-with-cookies-vs-token/
.. _10 Things You Should Know about Tokens: https://auth0.com/blog/2014/01/27/ten-things-you-should-know-about-tokens-and-cookies/
.. _authenticator implementations: https://github.com/mohiva/play-silhouette/tree/master/app/com/mohiva/play/silhouette/contrib/authenticators

CookieAuthenticator
^^^^^^^^^^^^^^^^^^^

An authenticator that uses a stateful, cookie based approach. It works by storing the unique
ID of the authenticator in a cookie. This ID gets then mapped to an authenticator instance
in the server side backing store. This approach can also be named "server side session".

**Pro**

* Small network throughput on client side
* Ideal for traditional browser based websites
* Client fingerprinting

**Cons**

* Larger network throughput on the server side
* Not stateless (needs a synchronized backing store)
* Less than ideal for mobile or single page apps
* Can be vulnerable for `CSRF`_ attacks
* Plays not well with `CORS`_

.. Tip::
   Please take a look on the :ref:`configuration settings <cookie_authenticator_settings>`, on
   how to configure this authenticator.

SessionAuthenticator
^^^^^^^^^^^^^^^^^^^^

An authenticator that uses a stateless, session based approach. It works by storing a serialized
authenticator instance in the Play Framework session cookie.

**Pro**

* No network throughput on the server side
* Ideal for traditional browser based websites
* Client fingerprinting
* Stateless

**Cons**

* Larger network throughput on client side
* Less than ideal for mobile or single page apps
* Can be vulnerable for `CSRF`_ attacks
* Plays not well with `CORS`_

.. Tip::
   Please take a look on the :ref:`configuration settings <session_authenticator_settings>`, on
   how to configure this authenticator.

HeaderAuthenticator
^^^^^^^^^^^^^^^^^^^

An authenticator that uses a stateful, header based approach. It works by using a user defined
header to track the authenticated user and a server side backing store that maps the header to
an authenticator instance.

**Pro**

* Small network throughput on client side
* Ideal for mobile or single page apps
* Not vulnerable against `CSRF`_ attacks
* Play well with `CORS`_

**Cons**

* Larger network throughput on the server side
* Not stateless (needs a synchronized backing store)
* Less than ideal for traditional browser based websites
* No client fingerprinting

.. Tip::
   Please take a look on the :ref:`configuration settings <header_authenticator_settings>`, on
   how to configure this authenticator.

.. _CSRF: http://en.wikipedia.org/wiki/Cross-site_request_forgery
.. _CORS: http://en.wikipedia.org/wiki/Cross-origin_resource_sharing


.. ========================
   Some useful links as reference for the pro and cons sections

   http://stackoverflow.com/questions/21357182/csrf-token-necessary-when-using-stateless-sessionless-authentication
   https://auth0.com/blog/2014/01/07/angularjs-authentication-with-cookies-vs-token/
   https://auth0.com/blog/2014/01/27/ten-things-you-should-know-about-tokens-and-cookies/
   http://sitr.us/2011/08/26/cookies-are-bad-for-you.html
   =======================
