Environment
===========

The environment defines the key components which are needed by a Silhouette application.
This components consists of
the :ref:`identity implementation <identity_impl>`,
the :ref:`authenticator implementation <authenticator_impl>`,
the :ref:`identity service implementation <identity_service_impl>`,
the :ref:`authenticator service implementation <authenticator_service_impl>`,
the :ref:`provider implementation <provider_impl>` and
the :ref:`event bus implementation <event_bus_impl>`.

A configured environment is needed in every derived `Silhouette controller`_ implementation
and it should be provided through dependency injection. Regardless of whether compile time
or runtime dependency injection is preferred, Silhouette plays well with both of them. You
can find an example of both, runtime or compile time dependency injection on the
:ref:`example page <examples>`.

.. _Silhouette controller: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/api/Silhouette.scala
