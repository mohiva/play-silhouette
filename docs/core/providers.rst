Providers
=========

In Silhouette a provider is a service that handles the authentication of
an identity. It typically reads authorization information and returns
information about an identity.

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


Credentials provider
--------------------

Silhouette supports also local authentication with the credentials
provider. This provider accepts credentials and returns the login
information for an identity after a successful authentication. Typically
credentials consists of an identifier(a username or email address) and a
password.

The provider supports the change of password hashing algorithms on the
fly. Sometimes it may be possible to change the hashing algorithm used
by the application. But the hashes stored in the backing store can’t be
converted back into plain text passwords, to hash them again with the
new algorithm. So if a user successfully authenticates after the
application has changed the hashing algorithm, the provider hashes the
entered password again with the new algorithm and stores the
authentication info in the backing store.

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

    FacebookProvider(cacheLayer, httpLayer, oAuthSettings)

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

    case class CustomSocialProfile[A <: AuthInfo](
      loginInfo: LoginInfo,
      authInfo: A,
      firstName: Option[String] = None,
      lastName: Option[String] = None,
      fullName: Option[String] = None,
      email: Option[String] = None,
      avatarURL: Option[String] = None,
      gender: Option[String] = None) extends SocialProfile[A]

Now we create a profile builder which can be mixed into to the Facebook
provider to return our previous defined custom profile.

.. code-block:: scala

    trait CustomFacebookProfileBuilder extends SocialProfileBuilder[OAuth2Info] {
      self: FacebookProvider =>

      /**
       * Defines the profile to return by the provider.
       */
      override type Profile = CustomSocialProfile[OAuth2Info]

      /**
       * Parses the profile from the Json response returned by the Facebook API.
       */
      override protected def parseProfile(parser: JsonParser, json: JsValue): Try[Profile] = Try {
        val commonProfile = parser(json)
        val gender = (json \ "gender").asOpt[String]

        CustomSocialProfile(
          loginInfo = commonProfile.loginInfo,
          authInfo = commonProfile.authInfo,
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

    new FacebookProvider(cacheLayer, httpLayer, oAuthSettings) with CustomFacebookProfileBuilder
