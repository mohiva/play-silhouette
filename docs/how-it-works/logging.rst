Logging
=======

Silhouette uses named loggers for logging. This gives your application 
fine-grained control over the log entries logged by Silhouette.

Configure logging
-----------------

.. Note::
   Play uses `Logback`_ as its logging framework. Please see the `Play documentation`_ on
   how to configure base logging in Play.

As an example, in your logging.xml you can set the logging level for the
complete ``com.mohiva`` namespace to ``ERROR`` and then define a more
detailed level for some components.

.. code-block:: xml

    <configuration debug="false">
        ...
        <logger name="com.mohiva" level="ERROR" />
        <logger name="com.mohiva.play.silhouette.api.exceptions.AccessDeniedException" level="INFO" />
        ...
    </configuration>


.. _Logback: http://logback.qos.ch/
.. _Play documentation: https://www.playframework.com/documentation/latest/SettingsLogger

Use the logger
--------------

To use the named logger you only need to mix the ``Logger`` trait into your
class or trait. Then you can use the ``logger`` property to access the `Play
logging API`_.

.. code-block:: scala

   import com.mohiva.play.silhouette.core.Logger

   class LoggerExample extends Logger {
     def log {
       logger.error("My log message")
     }
   }


.. _Play logging API: https://www.playframework.com/documentation/latest/ScalaLogging
