.. _provider_impl:

Providers
=========

In Silhouette a provider is a service that handles the authentication of
an identity. It typically reads authorization information and returns
information about an identity.

Request providers
-----------------

Request providers are special types of providers. They can be hocked directly into
incoming requests which gets scanned for credentials and either gain or restrict
access to the protected resources. This can be useful for machine authentication.

When using a request provider, you should consider to use the :ref:`dummy authenticator
<dummy_authenticator>`, because it doesn't have any network throughput or memory footprint
compared to other authenticators.

Credentials provider
--------------------

Silhouette supports local authentication, typically via an HTML form,
with the credentials provider. This provider accepts credentials and returns
the login information for an identity after a successful authentication.
Typically credentials consist of an identifier (a username or email address)
and a password.

The credentials provider supports changing the password hashing algorithm on the
fly. Sometimes it may be possible to change the hashing algorithm used by the
application. But the hashes stored in the backing store can’t be converted back
into plain text passwords to hash them again with the new algorithm. So if a user
successfully authenticates after the application has changed the hashing algorithm,
the provider hashes the entered password again with the new algorithm and stores the
authentication info in the backing store.


Basic Authentication provider
-----------------------------

Silhouette supports `basic access authentication`_ as described in `RFC 2617`_.
This provider is an implementation of a request provider which accepts the current
request and returns the login information for an identity after a successful authentication.

The basic authentication provider supports changing the password hashing algorithm
on the fly. Sometimes it may be possible to change the hashing algorithm used by the
application. But the hashes stored in the backing store can’t be converted back into
plain text passwords to hash them again with the new algorithm. So if a user successfully
authenticates after the application has changed the hashing algorithm, the provider
hashes the entered password again with the new algorithm and stores the authentication
info in the backing store.

.. _basic access authentication: http://en.wikipedia.org/wiki/Basic_access_authentication
.. _RFC 2617: https://www.ietf.org/rfc/rfc2617.txt

Social providers
----------------

A social provider allows a user to authenticate an identity on your website
with an existing account from an external social website like Facebook,
Google or Twitter. Following you can find a list of all supported
providers grouped by authentication protocol.

OAuth1
^^^^^^

-  LinkedInProvider (www.linkedin.com)
-  TwitterProvider (www.twitter.com)
-  XingProvider (www.xing.com)

OAuth2
^^^^^^

-  DropboxProvider (www.dropbox.com)
-  FacebookProvider (www.facebook.com)
-  FoursquareProvider (www.foursquare.com)
-  GitHubProvider (www.github.com)
-  GoogleProvider (www.google.com)
-  InstagramProvider (www.instagram.com)
-  LinkedInProvider (www.linkedin.com)
-  VKProvider (www.vk.com)


Social profile
--------------

The social profile contains the profile data returned from the social providers.
Silhouette provides a default social profile called `CommonSocialProfile`_,
which contains the most common profile data providers return.

.. _CommonSocialProfile: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/impl/providers/SocialProvider.scala#L168

Social profile builders
-----------------------

Some providers return a superset of the information in `CommonSocialProfile`_,
for example the location or gender of the user. Silouette's “profile builders”
allow you to construct custom profiles without duplicating existing code. Instead
of overriding the provider to return the additional profile information, developers
can mix in a profile builder which adds only the programming logic for the additional
fields.

The default social profile builder
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

All provider implementations are abstract because a profile builder must be
specified before an object can be instantiated. Silhouette ships with a
default profile builder, ``CommonSocialProfileBuilder``, which returns a
``CommonSocialProfile`` after successful authentication. However, it can't
be mixed into the provider by default because Scala doesn't allow us to override
a concrete type with a different concrete type. That is, mixing in a default builder
would prevent customization.

Silhouette handles this problem by leaving the provider class abstract but making the
companion object's ``apply`` method handle the most common case --
instantiating a provider with a ``CommonSocialProfileBuilder``. So in most
cases you should simply call the apply method and instead of using the ``new`` keyword:

.. code-block:: scala

    FacebookProvider(httpLayer, stateProvider, settings)

Write a custom social profile builder
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

As noted above it is very easy to write your own profile builder
implementations. Let's take a look on the following code examples. The
first one defines a custom social profile that differs from the common
social profile by the additional gender field.

.. code-block:: scala

    case class CustomSocialProfile(
      loginInfo: LoginInfo,
      firstName: Option[String] = None,
      lastName: Option[String] = None,
      fullName: Option[String] = None,
      email: Option[String] = None,
      avatarURL: Option[String] = None,
      gender: Option[String] = None) extends SocialProfile

Now we create a profile builder which can be mixed into the Facebook
provider to return our previously defined custom profile.

.. code-block:: scala

    trait CustomFacebookProfileBuilder extends SocialProfileBuilder {
      self: FacebookProvider =>

      /**
       * Defines the profile to return by the provider.
       */
      type Profile = CustomSocialProfile

      /**
       * Parses the profile from the Json response returned by the Facebook API.
       */
      protected def parseProfile(parser: JsonParser, json: JsValue): Try[Profile] = Try {
        val commonProfile = parser(json)
        val gender = (json \ "gender").asOpt[String]

        CustomSocialProfile(
          loginInfo = commonProfile.loginInfo,
          firstName = commonProfile.firstName,
          lastName = commonProfile.lastName,
          fullName = commonProfile.fullName,
          avatarURL = commonProfile.avatarURL,
          email = commonProfile.email,
          gender = gender)
      }
    }

As you can see there is no need to duplicate any Json parsing. The only
thing to do is to query the gender field from the Json response returned
by the Facebook API.

Now you can mixin the profile builder by instantiating the Facebook
provider with the profile builder.

.. code-block:: scala

    new FacebookProvider(httpLayer, stateProvider, settings) with CustomFacebookProfileBuilder


OAuth2 state
------------

.. versionadded:: 2.0

The OAuth2 protocol supports the `state parameter`_, a value the client can include in the request
and that the server returns as a parameter unmodified in the response. This parameter `should be used mainly`_
to protect an application against `CSRF attacks`_. But it can also be used to remember some
state about the user.

To maintain the state in Silhouette, a state provider must be passed to every OAuth2 authentication
provider. All state provider implementations can be found in the `impl package`_.

.. _state parameter: http://tools.ietf.org/html/rfc6749#section-4.1.1
.. _CSRF attacks: http://www.oauthsecurity.com/#authorization-code-flow
.. _should be used mainly: http://www.thread-safe.com/2014/05/the-correct-use-of-state-parameter-in.html
.. _impl package: https://github.com/mohiva/play-silhouette/tree/master/app/com/mohiva/play/silhouette/impl/providers/oauth2/state

List of OAuth2 states
^^^^^^^^^^^^^^^^^^^^^

We provide some built in state providers, but as noted above a customized
state can be implemented to remember some state about a user.

CookieState
'''''''''''

The cookie state works by embedding the state in a cookie. This is one of the preferred methods
from the `OAuth2 RFC`_ and it provides a stateless/scalable approach.

.. Tip::
   Please take a look on the :ref:`configuration settings <oaut2_cookie_state_settings>`, on how
   to configure the provider for this state.

.. _OAuth2 RFC: https://tools.ietf.org/html/rfc6749#section-10.12


Authentication information
--------------------------

The `AuthInfo`_ implementation contains authentication information such
as access tokens, hashed passwords, and so on -- which
should never be exposed to the public. Each provider defines its own
`AuthInfo`_ implementation.

As with other Silhouette structures that vary in their implementation,
`AuthInfo`_ is managed by a `AuthInfoService`_ that saves and retrieves
the information as needed.

.. _AuthInfoService: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/api/services/AuthInfoService.scala#L31
.. _AuthInfo: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/api/services/AuthInfoService.scala#L61
