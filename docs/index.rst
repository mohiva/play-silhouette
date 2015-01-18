Welcome to Silhouette's documentation!
======================================

.. ifconfig:: release.endswith('-SNAPSHOT')

    .. warning::
       This documentation is for version |release| of Silhouette currently under
       development. Were you looking for version |last_stable| documentation?

**Silhouette** is an authentication library for Play Framework applications that supports
several authentication methods, including OAuth1, OAuth2, OpenID, Credentials, Basic
Authentication or custom authentication schemes.

It can be integrated as is, or used as a building block and customized
to meet specific application requirements, thanks to its loosely coupled
design.

The project is named after the fictional crime fighter character
`Silhouette`_, from the Watchmen `graphic novel`_ and `movie`_.

.. _Silhouette: http://www.comicvine.com/silhouette/4005-35807/
.. _graphic novel: http://en.wikipedia.org/wiki/Watchmen
.. _movie: http://en.wikipedia.org/wiki/Watchmen_%28film%29

Basics
------

.. toctree::
   :maxdepth: 1

   basics/features
   basics/examples
   basics/releases
   basics/contribute
   basics/help


How it works
------------

.. toctree::
   :maxdepth: 3

   how-it-works/environment
   how-it-works/actions
   how-it-works/identity
   how-it-works/authenticator
   how-it-works/providers
   how-it-works/error-handling
   how-it-works/caching
   how-it-works/events
   how-it-works/logging
   how-it-works/testing


Configuration
-------------

.. toctree::
   :maxdepth: 2

   configuration
