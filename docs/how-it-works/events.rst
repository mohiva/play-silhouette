.. _event_bus_impl:

Events
======

Silhouette provides event handling based on `Akka’s Event Bus`_. The
following events are provided by Silhouette, although only the three
marked of them are fired from core.

* SignUpEvent
* LoginEvent
* LogoutEvent
* AccessDeniedEvent
* AuthenticatedEvent \*
* NotAuthenticatedEvent \*
* NotAuthorizedEvent \*

It is very easy to propagate own events over the event bus by
implementing the ``SilhouetteEvent`` trait.

.. code-block:: scala

    case class CustomEvent() extends SilhouetteEvent

Use the event bus
-----------------

The event bus is available in every Silhouette controller over the
environment variable ``env.eventBus``. You can also inject the event bus
into other classes like services or DAOs. You must only take care that
you use the same event bus. There exists a singleton event bus by
calling ``EventBus()``.

Listen for events
-----------------

To listen for events you must implement a listener based on an ``Actor``
and then register the listener, with the event to listen, on the event
bus instance:

.. code-block:: scala

    val listener = system.actorOf(Props(new Actor {
      def receive = {
        case e @ LoginEvent(identity: User, request, lang) => println(e)
        case e @ LogoutEvent(identity: User, request, lang) => println(e)
      }
    }))

    val eventBus = EventBus()
    eventBus.subscribe(listener, classOf[LoginEvent[User]])
    eventBus.subscribe(listener, classOf[LogoutEvent[User]])

Publish events
--------------

Publishing events is also simple:

.. code-block:: scala

    val eventBus = EventBus()
    eventBus.publish(LoginEvent[User](identity, request, lang))

.. _Akka’s Event Bus: http://doc.akka.io/docs/akka/2.2.4/scala/event-bus.html
