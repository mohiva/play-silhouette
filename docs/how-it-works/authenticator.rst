.. _authenticator_impl:

Authenticator
=============

An `Authenticator`_ is a key part of a Silhouette application, because it tracks
a user after a successful authentication. The Authenticator itself is a small
class which stores only some data like its validity and the linked login information
for an identity.

.. _Authenticator: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/api/Authenticator.scala#L25

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
embedded into a Play framework request or result. This can by done by creating a cookie,
storing data into the user session or sending the artifacts in a user defined header. If
the service uses a backing store, then the authenticator instance will be stored in it.
The initialization should be done after a successful authentication with a new created
authenticator.

Embedding the authenticator related data into the result means that the data will be send
to the client. It may also be useful to embed the authenticator related data into an incoming
request to lead the ``SecuredAction`` to believe that the request is a new request which
contains a valid authenticator. This can be useful in Play filters.

.. Attention::
   The following actions are only used for internal purposes inside a :ref:`Silhouette
   action <silhouette_actions>`. But it might be useful to know how an authenticator
   will be handled.

Touch an authenticator
^^^^^^^^^^^^^^^^^^^^^^

If an authenticator uses sliding window expiration then this method updates the last used
time on the authenticator. So to mark an authenticator as used it will be touched on every
request to a :ref:`Silhouette action <silhouette_actions>`. If sliding window expiration
is disabled then the authenticator will not be updated.

Update an authenticator
^^^^^^^^^^^^^^^^^^^^^^^

Automatically updates the state of an authenticator on every request to a :ref:`Silhouette
action <silhouette_actions>`. Updated authenticator artifacts will be embed into the Play
framework result, before sending it to the client. If the service uses a backing store, then
the authenticator instance will be updated in the store too.

Renew an authenticator
^^^^^^^^^^^^^^^^^^^^^^

Authenticators have a fix expiration date. So with this method it's possible to renew the
expiration of an authenticator by discarding the old one and creating a new one. Based on
the implementation, the renew method revokes the given authenticator first, before creating
a new one. If the authenticator was updated, then the updated artifacts will be embedded
into the response.

.. Note::
   To renew an authenticator you must call the `renew` method of the authenticator instance
   inside a :ref:`Silhouette action <silhouette_actions>`. This method accepts a `Result`
   and returns a wrapped `Renew` result, which notifies the action to renew the
   authenticator instead of updating it.

Discard an authenticator
^^^^^^^^^^^^^^^^^^^^^^^^

If an authenticator is invalid then Silhouette automatically discards an authenticator on
every request to a :ref:`Silhouette action <silhouette_actions>`. Discarding means that all
client side stored artifacts will be removed. If the service uses a backing store, then the
authenticator will also be removed from it. To logout a user from a Silhouette application,
the authenticator must also be discarded manually.

.. Note::
   To discard an authenticator you must call the `discard` method of the authenticator
   instance inside a :ref:`Silhouette action <silhouette_actions>`. This method accepts a
   `Result` and returns a wrapped `Discard` result, which notifies the action to discard
   the authenticator instead of updating it.

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
.. _authenticator implementations: https://github.com/mohiva/play-silhouette/tree/master/app/com/mohiva/play/silhouette/impl/authenticators

CookieAuthenticator
^^^^^^^^^^^^^^^^^^^

An authenticator that uses a stateful, cookie based approach. It works by storing the unique
ID of the authenticator in a cookie. This ID gets then mapped to an authenticator instance
in the server side backing store. This approach can also be named "server side session".

The authenticator can use sliding window expiration. This means that the authenticator times
out after a certain time if it wasn't used. This can be controlled with the :ref:`authenticatorIdleTimeout
<cookie_authenticator_settings>` property of the settings class.

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

The authenticator can use sliding window expiration. This means that the authenticator times
out after a certain time if it wasn't used. This can be controlled with the :ref:`authenticatorIdleTimeout
<session_authenticator_settings>` property of the settings class.

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

BearerTokenAuthenticator
^^^^^^^^^^^^^^^^^^^^^^^^

An authenticator that uses a header based approach with the help of a bearer token. It works by
transporting a token in a user defined header to track the authenticated user and a server side
backing store that maps the token to an authenticator instance.

The authenticator can use sliding window expiration. This means that the authenticator times out
after a certain time if it wasn't used. This can be controlled with the :ref:`authenticatorIdleTimeout
<bearer_token_authenticator_settings>` property of the settings class.

**Pro**

* Small network throughput on client side
* Ideal for mobile or single page apps
* Not vulnerable against `CSRF`_ attacks
* Plays well with `CORS`_

**Cons**

* Larger network throughput on the server side
* Not stateless (needs a synchronized backing store)
* Less than ideal for traditional browser based websites
* No client fingerprinting

.. Tip::
   Please take a look on the :ref:`configuration settings <bearer_token_authenticator_settings>`, on
   how to configure this authenticator.

JWTAuthenticator
^^^^^^^^^^^^^^^^

An authenticator that uses a header based approach with the help of a `JWT`_. It works by using a
JWT to transport the authenticator data inside a user defined header. It can be stateless with the
disadvantages that the JWT can't be invalidated.

The authenticator can use sliding window expiration. This means that the authenticator times out
after a certain time if it wasn't used. This can be controlled with the :ref:`authenticatorIdleTimeout
<jwt_authenticator_settings>` property of the settings class. If this feature is activated then a
new token will be generated on every update. Make sure your application can handle this case.

**Pro**

* Ideal for mobile or single page apps
* Can be stateless (with the disadvantages it can't be invalidated)
* Not vulnerable against `CSRF`_ attacks
* Plays well with `CORS`_

**Cons**

* Larger network throughput on client side
* Larger network throughput on the server side (if backing store is used)
* Less than ideal for traditional browser based websites
* No client fingerprinting

.. Tip::
   Please take a look on the :ref:`configuration settings <jwt_authenticator_settings>`, on
   how to configure this authenticator.

.. _CSRF: http://en.wikipedia.org/wiki/Cross-site_request_forgery
.. _CORS: http://en.wikipedia.org/wiki/Cross-origin_resource_sharing
.. _JWT: https://tools.ietf.org/html/draft-ietf-oauth-json-web-token-27


.. ========================
   Some useful links as reference for the pro and cons sections

   http://stackoverflow.com/questions/21357182/csrf-token-necessary-when-using-stateless-sessionless-authentication
   https://auth0.com/blog/2014/01/07/angularjs-authentication-with-cookies-vs-token/
   https://auth0.com/blog/2014/01/27/ten-things-you-should-know-about-tokens-and-cookies/
   http://sitr.us/2011/08/26/cookies-are-bad-for-you.html
   =======================
