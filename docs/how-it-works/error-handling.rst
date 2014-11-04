Error handling
==============

Every provider implementation in Silhouette throws either an
``AccessDeniedException`` or an ``AuthenticationException``. The
``AccessDeniedException`` will be thrown if a user provides incorrect
credentials or a social provider denies an authentication attempt. In
any other case the ``AuthenticationException`` will be thrown.

Due to the asynchronous nature of Silhouette, all exceptions will be thrown
in Futures. This means that exceptions may not be caught in a
``try catch`` block because Futures may be executed in separate Threads.
To solve this problem, Futures have their own error handling mechanism.
They can be recovered from an error state into desirable state with the
help of the ``recover`` and ``recoverWith`` methods. Both methods
accept a partial function which transforms the ``Exception`` into any
other type. For more information please visit the ``Future`` trait's 
`documentation`_.

All controller implementations which are derived from the ``Silhouette``
base controller can use the ``exceptionHandler`` method.
This is a default recover implementation which transforms an
``AccessDeniedException`` into a 403 Forbidden result and an
``AuthenticationException`` into a 401 Unauthorized result. The
following code snippet shows how an error can be recovered from a failed
authentication.

.. code-block:: scala

    authenticate().map { result =>
      // Do something with the result
    }.recoverWith(exceptionHandler)

.. Note::
   The ``exceptionHandler`` method calls the local or global
   fallback methods under the hood to return the appropriate result.

.. _documentation: http://www.scala-lang.org/api/current/#scala.concurrent.Future
