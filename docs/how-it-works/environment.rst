Environment
===========

The environment defines the key components needed by a Silhouette application.
It consists of
the :ref:`identity implementation <identity_impl>`,
the :ref:`authenticator implementation <authenticator_impl>`,
the :ref:`identity service implementation <identity_service_impl>`,
the :ref:`authenticator service implementation <authenticator_service_impl>`,
the :ref:`provider implementation <provider_impl>` and
the :ref:`event bus implementation <event_bus_impl>`.

A configured environment is needed in every derived `Silhouette controller`_ implementation
and should be provided through dependency injection. Silhouette plays well with both compile time
and runtime dependency injection. You can find examples of both approaches
on the :ref:`example page <examples>`.

.. _Silhouette controller: https://github.com/mohiva/play-silhouette/blob/master/app/com/mohiva/play/silhouette/api/Silhouette.scala
