Logging
=======

Silhouette uses named loggers for logging. So in your application you
have a more fine-grained control over the log entries logged by
Silhouette.

As an example, in your logging.xml you can set the logging level for the
complete ``com.mohiva`` namespace to ``ERROR`` and then define a more
detailed level for some components.

.. code-block:: xml

    <configuration debug="false">
        ...
        <logger name="com.mohiva" level="ERROR" />
        <logger name="com.mohiva.play.silhouette.core.AccessDeniedException" level="INFO" />
        ...
    </configuration>
