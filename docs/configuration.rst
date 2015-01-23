Configuration
=============

Introduction
------------

Silhouette doesn’t dictate the form and the location of the configuration.
So you are able to configure it over a database, a configuration server or
the Play Framework configuration mechanism.

Generally, configuration in Silhouette is handled over configuration
objects. These objects must be filled with data and then passed to the
instance to configure.

.. Note::
   For reasons of simplification we use the Play Framework
   configuration syntax in our examples.

OAuth1 based providers
----------------------

To configure OAuth1 based providers you must use the ``OAuth1Settings``
class. This class has the following form:

.. code-block:: scala

    case class OAuth1Settings(
      requestTokenURL: String,
      accessTokenURL: String,
      authorizationURL: String,
      callbackURL: String,
      consumerKey: String,
      consumerSecret: String)

=========================    ===================================================================
Property                     Description
=========================    ===================================================================
``requestTokenURL``          The request token URL provided by the OAuth provider
``accessTokenURL``           The access token URL provided by the OAuth provider
``authorizationURL``         The authorization URL provided by the OAuth provider
``callbackURL``              The callback URL to the application after a successful
                             authentication on the OAuth provider
``consumerKey``              The consumer ID provided by the OAuth provider
``consumerSecret``           The consumer secret provided by the OAuth provider
=========================    ===================================================================

Callback URL
^^^^^^^^^^^^

The ``callbackURL`` must point to your action which is responsible for
the authentication over your defined providers. So if you define the
following route as example:

.. code-block:: scala

    GET  /authenticate/:provider  @controllers.SocialAuthController.authenticate(provider)

Then your ``callbackURL`` must have the following format:

.. code-block:: scala

    callbackURL="https://your.domain.tld/authenticate/linkedin"

Example
^^^^^^^

Your configuration could then have this format:

.. code-block:: js

    linkedin {
      requestTokenURL="https://api.linkedin.com/uas/oauth/requestToken"
      accessTokenURL="https://api.linkedin.com/uas/oauth/accessToken"
      authorizationURL="https://api.linkedin.com/uas/oauth/authenticate"
      callbackURL="https://your.domain.tld/authenticate/linkedin"
      consumerKey="your.consumer.key"
      consumerSecret="your.consumer.secret"
    }

    twitter {
      requestTokenURL="https://twitter.com/oauth/request_token"
      accessTokenURL="https://twitter.com/oauth/access_token"
      authorizationURL="https://twitter.com/oauth/authenticate"
      callbackURL="https://your.domain.tld/authenticate/twitter"
      consumerKey="your.consumer.key"
      consumerSecret="your.consumer.secret"
    }

    xing {
      requestTokenURL="https://api.xing.com/v1/request_token"
      accessTokenURL="https://api.xing.com/v1/access_token"
      authorizationURL="https://api.xing.com/v1/authorize"
      callbackURL="https://your.domain.tld/authenticate/xing"
      consumerKey="your.consumer.key"
      consumerSecret="your.consumer.secret"
    }

To get the consumerKey/consumerSecret keys you need to log into the
developer site of each service and register your application.


OAuth2 based providers
----------------------

To configure OAuth2 based providers you must use the ``OAuth2Settings``
class. This class has the following form:

.. code-block:: scala

    case class OAuth2Settings(
      authorizationURL: String,
      accessTokenURL: String,
      redirectURL: String,
      clientID: String,
      clientSecret: String,
      scope: Option[String] = None,
      authorizationParams: Map[String, String] = Map(),
      accessTokenParams: Map[String, String] = Map(),
      customProperties: Map[String, String] = Map())

=========================    ===================================================================
Property                     Description
=========================    ===================================================================
``authorizationURL``         The authorization URL provided by the OAuth provider
``accessTokenURL``           The access token URL provided by the OAuth provider
``redirectURL``              The redirect URL to the application after a successful
                             authentication on the OAuth provider
``clientID``                 The client ID provided by the OAuth provider
``clientSecret``             The client secret provided by the OAuth provider
``scope``                    The OAuth2 scope parameter provided by the OAuth provider
``authorizationParams``      Additional params to add to the authorization request
``accessTokenParams``        Additional params to add to the access token request
``customProperties``         A map of custom properties for the different providers
=========================    ===================================================================

Redirect URL
^^^^^^^^^^^^

The ``redirectURL`` must point to your action which is responsible for
the authentication over your defined providers. So if you define the
following route as example:

.. code-block:: scala

    GET  /authenticate/:provider  @controllers.SocialAuthController.authenticate(provider)

Then your ``redirectURL`` must have the following format:

.. code-block:: scala

    redirectURL="https://your.domain.tld/authenticate/facebook"

Example
^^^^^^^

Your configuration could then have this format:

.. code-block:: js

    dropbox {
      authorizationUrl="https://www.dropbox.com/1/oauth2/authorize"
      accessTokenUrl="https://api.dropbox.com/1/oauth2/token"
      redirectURL="https://your.domain.tld/authenticate/dropbox"
      clientId="your.client.id"
      clientSecret="your.client.secret"
    }

    facebook {
      authorizationUrl="https://graph.facebook.com/oauth/authorize"
      accessTokenUrl="https://graph.facebook.com/oauth/access_token"
      redirectURL="https://your.domain.tld/authenticate/facebook"
      clientId="your.client.id"
      clientSecret="your.client.secret"
      scope=email
    }

    foursquare {
      authorizationUrl="https://foursquare.com/oauth2/authenticate"
      accessTokenUrl="https://foursquare.com/oauth2/access_token"
      redirectURL="https://your.domain.tld/authenticate/foursquare"
      clientId="your.client.id"
      clientSecret="your.client.secret"
    }

    github {
      authorizationUrl="https://github.com/login/oauth/authorize"
      accessTokenUrl="https://github.com/login/oauth/access_token"
      redirectURL="https://your.domain.tld/authenticate/github"
      clientId="your.client.id"
      clientSecret="your.client.secret"
    }

    google {
      authorizationUrl="https://accounts.google.com/o/oauth2/auth"
      accessTokenUrl="https://accounts.google.com/o/oauth2/token"
      redirectURL="https://your.domain.tld/authenticate/google"
      clientId="your.client.id"
      clientSecret="your.client.secret"
      scope="profile email"
    }

    instagram {
      authorizationUrl="https://api.instagram.com/oauth/authorize"
      accessTokenUrl="https://api.instagram.com/oauth/access_token"
      redirectURL="https://your.domain.tld/authenticate/instagram"
      clientId="your.client.id"
      clientSecret="your.client.secret"
    }

    linkedin {
      authorizationUrl="https://www.linkedin.com/uas/oauth2/authorization"
      accessTokenUrl="https://www.linkedin.com/uas/oauth2/accessToken"
      redirectURL="https://your.domain.tld/authenticate/linkedin"
      clientId="your.client.id"
      clientSecret="your.client.secret"
    }

    vk {
      authorizationUrl="http://oauth.vk.com/authorize"
      accessTokenUrl="https://oauth.vk.com/access_token"
      redirectURL="https://your.domain.tld/authenticate/vk"
      clientId="your.client.id"
      clientSecret="your.client.secret"
    }

To get the clientId/clientSecret keys you need to log into the developer
site of each service and register your application.

OAuth2 state
------------

.. _oaut2_cookie_state_settings:

CookieState
^^^^^^^^^^^

To configure the ``CookieState`` provider you must use the ``CookieStateSettings``
class. This class has the following form:

.. code-block:: scala

   case class CookieStateSettings(
     cookieName: String = "OAuth2State",
     cookiePath: String = "/",
     cookieDomain: Option[String] = None,
     secureCookie: Boolean = Play.isProd,
     httpOnlyCookie: Boolean = true,
     expirationTime: Int = 5 * 60)

=========================    ===================================================================
Property                     Description
=========================    ===================================================================
``cookieName``               The cookie name
``cookiePath``               The cookie path
``cookieDomain``             The cookie domain
``secureCookie``             Whether this cookie is secured, sent only for HTTPS requests.
                             Default to sending only for HTTPS in production, but not for
                             development and test
``httpOnlyCookie``           Whether this cookie is HTTP only, i.e. not accessible from
                             client-side JavaScript code
``expirationTime``           State expiration. Defaults to 5 minutes which provides sufficient
                             time to log in, but not too much. This is a balance between
                             convenience and security
=========================    ===================================================================


OpenID based providers
----------------------

To configure OpenID based providers you must use the ``OpenIDSettings``
class. This class has the following form:

.. code-block:: scala

    case class OpenIDSettings(
      providerURL: String,
      callbackURL: String,
      axRequired: Seq[(String, String)] = Seq.empty,
      axOptional: Seq[(String, String)] = Seq.empty,
      realm: Option[String] = None)

=========================    ===================================================================
Property                     Description
=========================    ===================================================================
``providerURL``              The OpenID provider URL used if no openID was given
``callbackURL``              The callback URL to the application after a successful authentication
                             on the OpenID provider.
``axRequired``               Required attributes to return from the provider after a successful
                             authentication
``axOptional``               Optional attributes to return from the provider after a successful
                             authentication
``realm``                    An URL pattern that represents the part of URL-space for which an
                             OpenID Authentication request is valid
=========================    ===================================================================

Callback URL
^^^^^^^^^^^^

The ``callbackURL`` must point to your action which is responsible for
the authentication over your defined providers. So if you define the
following route as example:

.. code-block:: scala

    GET  /authenticate/:provider  @controllers.SocialAuthController.authenticate(provider)

Then your ``callbackURL`` must have the following format:

.. code-block:: scala

    callbackURL="https://your.domain.tld/authenticate/yahoo"

Example
^^^^^^^

Your configuration could then have this format:

.. code-block:: js

    steam {
      providerURL="https://steamcommunity.com/openid/"
      callbackURL="https://your.domain.tld/authenticate/steam"
      realm="https://your.domain.tld"
    }

    yahoo {
      providerURL="https://me.yahoo.com/"
      callbackURL="https://your.domain.tld/authenticate/yahoo"
      axRequired={
        "fullname": "http://axschema.org/namePerson",
        "email": "http://axschema.org/contact/email",
        "image": "http://axschema.org/media/image/default"
      }
      realm="https://your.domain.tld"
    }


Authenticators
--------------

.. _cookie_authenticator_settings:

CookieAuthenticator
^^^^^^^^^^^^^^^^^^^

To configure the ``CookieAuthenticator`` service you must use the ``CookieAuthenticatorSettings``
class. This class has the following form:

.. code-block:: scala

   case class CookieAuthenticatorSettings(
     cookieName: String = "id",
     cookiePath: String = "/",
     cookieDomain: Option[String] = None,
     secureCookie: Boolean = Play.isProd,
     httpOnlyCookie: Boolean = true,
     useFingerprinting: Boolean = true,
     cookieMaxAge: Option[Int] = Some(12 * 60 * 60),
     authenticatorIdleTimeout: Option[Int] = Some(30 * 60),
     authenticatorExpiry: Int = 12 * 60 * 60)

============================    ===================================================================
Property                        Description
============================    ===================================================================
``cookieName``                  The cookie name
``cookiePath``                  The cookie path
``cookieDomain``                The cookie domain
``secureCookie``                Whether this cookie is secured, sent only for HTTPS requests.
                                Default to sending only for HTTPS in production, but not for
                                development and test
``httpOnlyCookie``              Whether this cookie is HTTP only, i.e. not accessible from
                                client-side JavaScript code
``useFingerprinting``           Indicates if a fingerprint of the user should be stored in the
                                authenticator
``cookieMaxAge``                The cookie expiration date in seconds, `None` for a transient
                                cookie. Defaults to 12 hours
``authenticatorIdleTimeout``    The time in seconds an authenticator can be idle before it timed
                                out. Defaults to 30 minutes
``authenticatorExpiry``         The expiry of the authenticator in seconds. Defaults to 12 hours
============================    ===================================================================

.. _session_authenticator_settings:

SessionAuthenticator
^^^^^^^^^^^^^^^^^^^^

To configure the ``SessionAuthenticator`` service you must use the ``SessionAuthenticatorSettings``
class. This class has the following form:

.. code-block:: scala

   case class SessionAuthenticatorSettings(
     sessionKey: String = "authenticator",
     encryptAuthenticator: Boolean = true,
     useFingerprinting: Boolean = true,
     authenticatorIdleTimeout: Option[Int] = Some(30 * 60),
     authenticatorExpiry: Int = 12 * 60 * 60)

============================    ===================================================================
Property                        Description
============================    ===================================================================
``sessionKey``                  The key of the authenticator in the session
``encryptAuthenticator``        Indicates if the authenticator should be encrypted in session
``useFingerprinting``           Indicates if a fingerprint of the user should be stored in the
``authenticatorIdleTimeout``    The time in seconds an authenticator can be idle before it timed
                                out. Defaults to 30 minutes
``authenticatorExpiry``         The expiry of the authenticator in seconds. Defaults to 12 hours
============================    ===================================================================

.. _bearer_token_authenticator_settings:

BearerTokenAuthenticator
^^^^^^^^^^^^^^^^^^^^^^^^

To configure the ``BearerTokenAuthenticator`` service you must use the ``BearerTokenAuthenticatorSettings``
class. This class has the following form:

.. code-block:: scala

   case class BearerTokenAuthenticatorSettings(
     headerName: String = "X-Auth-Token",
     authenticatorIdleTimeout: Option[Int] = Some(30 * 60),
     authenticatorExpiry: Int = 12 * 60 * 60)

============================    ===================================================================
Property                        Description
============================    ===================================================================
``headerName``                  The name of the header in which the token will be transfered
``authenticatorIdleTimeout``    The time in seconds an authenticator can be idle before it timed
                                out. Defaults to 30 minutes
``authenticatorExpiry``         The expiry of the authenticator in seconds. Defaults to 12 hours
============================    ===================================================================

.. _jwt_authenticator_settings:

JWTAuthenticator
^^^^^^^^^^^^^^^^

To configure the ``JWTAuthenticator`` service you must use the ``JWTAuthenticatorSettings``
class. This class has the following form:

.. code-block:: scala

   case class JWTAuthenticatorSettings(
     headerName: String = "X-Auth-Token",
     issuerClaim: String = "play-silhouette",
     encryptSubject: Boolean = true,
     authenticatorIdleTimeout: Option[Int] = None,
     authenticatorExpiry: Int = 12 * 60 * 60,
     sharedSecret: String)

============================    ===================================================================
Property                        Description
============================    ===================================================================
``headerName``                  The name of the header in which the token will be transfered
``issuerClaim``                 The issuer claim identifies the principal that issued the JWT
``encryptSubject``              Indicates if the subject should be encrypted in JWT
``authenticatorIdleTimeout``    The time in seconds an authenticator can be idle before it timed
                                out. This feature is disabled by default to prevent the generation
                                of a new JWT on every request
``authenticatorExpiry``         The expiry of the authenticator in seconds. Defaults to 12 hours
``sharedSecret``                The shared secret to sign the JWT
============================    ===================================================================
