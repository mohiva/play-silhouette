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
class. This object has the following form:

.. code-block:: scala

    case class OAuth1Settings(
      requestTokenURL: String,
      accessTokenURL: String,
      authorizationURL: String,
      callbackURL: String,
      consumerKey: String,
      consumerSecret: String)

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

.. code-block:: scala

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
class. This object has the following form:

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

.. code-block:: scala

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


Authenticators
--------------

.. _cookie_authenticator_settings:

CookieAuthenticator
^^^^^^^^^^^^^^^^^^^

.. _session_authenticator_settings:

SessionAuthenticator
^^^^^^^^^^^^^^^^^^^^

.. _header_authenticator_settings:

HeaderAuthenticator
^^^^^^^^^^^^^^^^^^^
