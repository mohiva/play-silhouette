.. _provider_impl:

Providers
=========

In Silhouette a provider is a service that handles the authentication of
an identity. It typically reads authorization information and returns
information about an identity.


Credentials provider
--------------------

Silhouette supports local form authentication with the credentials provider.
This provider accepts credentials and returns the login information for an
identity after a successful authentication. Typically credentials consists
of an identifier(a username or email address) and a password.

The provider supports the change of password hashing algorithms on the
fly. Sometimes it may be possible to change the hashing algorithm used
by the application. But the hashes stored in the backing store can’t be
converted back into plain text passwords, to hash them again with the
new algorithm. So if a user successfully authenticates after the
application has changed the hashing algorithm, the provider hashes the
entered password again with the new algorithm and stores the
authentication info in the backing store.


Social providers
----------------

A social provider allows an identity to authenticate it on your website
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
which contains the most common profile data a provider can return. But it is also
possible to define an own social profile which can be consists of more
data.

.. _CommonSocialProfile: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/impl/providers/SocialProvider.scala#L168

Social profile builders
-----------------------

It can be the case that more than the common profile information
supported by Silhouette should be returned from a social provider.
Additional profile information could be, as example, the location or the
gender of a user. To solve this issue Silhouette has a very neat
solution called “profile builders”. The advantage of this solution is
that it prevents a developer to duplicate existing code by overriding
the providers to return the additional profile information. Instead the
developer can mixin a profile builder which adds only the programming
logic for the additional fields.

The default social profile builder
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Silhouette ships with a built-in profile builder called
``CommonSocialProfileBuilder`` which returns the ``CommonSocialProfile``
object after successful authentication. This profile builder is mixed
into all provider implementations by instantiating it with their
companion objects. So instead of instantiating the provider with the new
keyword, you must call the apply method on its companion object.

.. code-block:: scala

    FacebookProvider(httpLayer, stateProvider, settings)

.. Hint::
   All provider implementations are abstract, so they cannot be
   instantiated without a profile builder. This is because of the fact that
   a concrete type in Scala cannot be overridden by a different concrete
   type. And therefore we cannot mixin the common profile builder by
   default, because it couldn’t be overridden by a custom profile builder.

Write a custom social profile builder
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

As noted above it is very easy to write your own profile builder
implementations. Lets take a look on the following code examples. The
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
provider to return our previous defined custom profile.

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

The OAuth2 protocol supports the `state parameter`_, that a client can be include in the request
and the server returns as a parameter unmodified in the response. This parameter `should be used mainly`_
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

We provide some built in states, but as noted above an own state can be implemented to remember
some state about a user.

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

The authentication information contains secure data like access tokens, hashed passwords and so on, which
should never be exposed to the public. To retrieve other than by Silhouette supported information from a
provider, try to connect again with this information and fetch the missing data.

Due its nature, the information will be represented by different implementations. Mostly every provider
implementation defines its own `AuthInfo`_ implementation.

.. _AuthInfo: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/api/services/AuthInfoService.scala#L60
