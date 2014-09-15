Glossary
========

.. _glossary-identity:

Identity
--------

The `Identity`_ trait represents an authenticated user.

.. _glossary-social-profile:

Social profile
--------------

The social profile contains the profile data returned from the social
providers. Silhouette provides a default social profile which contains
the most common profile data a provider can return. But it is also
possible to define an own social profile which can be consists of more
data.

.. _glossary-login-info:

Login information
-----------------

Contains the data about the provider that authenticated an identity.
This information is mostly public available and it simply consists of a
unique provider ID and a unique key which identifies a user on this
provider (userID, email, …). This information will be represented by the
`LoginInfo`_ trait.

.. _glossary_auth-info:

Authentication information
--------------------------

The authentication information contains secure data like access tokens,
hashed passwords and so on, which should never be exposed to the public.
To retrieve other than by Silhouette supported information from a
provider, try to connect again with this information and fetch the
missing data. Due its nature, the information will be represented by
different implementations. Mostly every provider implementation defines
its own `AuthInfo`_ implementation.

.. _Identity: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/core/Identity.scala#L25
.. _LoginInfo: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/core/Identity.scala#L43
.. _AuthInfo: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/core/services/AuthInfoService.scala#L60
