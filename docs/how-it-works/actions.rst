Actions
=======

.. _silhouette_actions:

Securing your Actions
---------------------

Silhouette provides a replacement for Play’s built in Action class named
``SecuredAction``. This action intercepts requests and checks if there
is an authenticated user. If there is one, the execution continues and
your code is invoked.

.. code-block:: scala

    class Application(env: Environment[User, CookieAuthenticator])
      extends Silhouette[User, CookieAuthenticator] {

      /**
       * Renders the index page.
       *
       * @returns The result to send to the client.
       */
      def index = SecuredAction { implicit request =>
        Ok(views.html.index(request.identity))
      }
    }

There is also a ``UserAwareAction`` that can be used for actions that
need to know if there is a current user but can be executed even if
there isn’t one.

.. code-block:: scala

    class Application(env: Environment[User, CookieAuthenticator])
      extends Silhouette[User, CookieAuthenticator] {

      /**
       * Renders the index page.
       *
       * @returns The result to send to the client.
       */
      def index = UserAwareAction { implicit request =>
        val userName = request.identity match {
          case Some(identity) => identity.fullName
          case None => "Guest"
        }
        Ok("Hello %s".format(userName))
      }
    }

For unauthenticated users you can implement a global or local fallback
action.

Global Fallback
^^^^^^^^^^^^^^^

You can mix the ``SecuredSettings`` trait into your ``Global``
object. This trait provides a method called ``onNotAuthenticated``. If
you implement this method, then every time a user calls a restricted
action, the result specified in the global fallback method will be
returned.

.. code-block:: scala

    object Global extends GlobalSettings with SecuredSettings {

      /**
       * Called when a user isn't authenticated.
       *
       * @param request The request header.
       * @param lang The current selected lang.
       * @return The result to send to the client.
       */
      override def onNotAuthenticated(request: RequestHeader, lang: Lang) = {
        Some(Future.successful(Unauthorized("No access")))
      }
    }

Local Fallback
^^^^^^^^^^^^^^

Every controller which is derived from the ``Silhouette`` base controller
has a method called ``notAuthenticated``. If you override these method,
then you can return a not-authenticated result similar to the global
fallback but only for this specific controller. The local fallback has
precedence over the global fallback.

.. code-block:: scala

    class Application(env: Environment[User, CookieAuthenticator])
      extends Silhouette[User, CookieAuthenticator] {

      /**
       * Called when a user isn't authenticated.
       *
       * @param request The request header.
       * @return The result to send to the client.
       */
      override def notAuthenticated(request: RequestHeader): Option[Future[SimpleResult]] = {
        Some(Future.successful(Unauthorized("No access")))
      }

      /**
       * Renders the index page.
       *
       * @returns The result to send to the client.
       */
      def index = SecuredAction { implicit request =>
        Ok(views.html.index(request.identity))
      }
    }

.. Note::
   If you don’t implement one or both of the fallback methods, a 401 response with a simple
   message will be displayed to the user.

Adding Authorization
--------------------

Silhouette provides a way to add authorization logic to your controller
actions. This is done by implementing an ``Authorization`` object that
is passed to ``SecuredAction`` as a parameter.

After checking if a user is authenticated the ``Authorization`` instance
is used to verify whether the execution should be allowed or not.

.. code-block:: scala

    /**
     * A trait to define Authorization objects that let you hook
     * an authorization implementation in SecuredActions.
     *
     * @tparam I The type of the identity.
     */
    trait Authorization[I <: Identity] {

      /**
       * Checks whether the user is authorized to execute an action or not.
       *
       * @param identity The identity to check for.
       * @param request The current request header.
       * @param lang The current lang.
       * @return True if the user is authorized, false otherwise.
       */
      def isAuthorized(identity: I)(implicit request: RequestHeader, lang: Lang): Boolean
    }

This is a sample implementation that only grants access to users that
logged in using a given provider:

.. code-block:: scala

    case class WithProvider(provider: String) extends Authorization[User] {
      def isAuthorized(user: User)(implicit request: RequestHeader, lang: Lang) = {
        user.identityId.providerId == provider
      }
    }

Here’s how you would use it:

.. code-block:: scala

    def myAction = SecuredAction(WithProvider("twitter")) { implicit request =>
        // do something here
    }

For unauthorized users you can implement a global or local fallback
action similar to the fallback for unauthenticated users.

Global Fallback
^^^^^^^^^^^^^^^

You can mix the ``SecuredSettings`` trait into your ``Global``
object. This trait provides a method called ``onNotAuthorized``. If you
implement this method, then every time a user calls an action on which
he isn’t authorized, the result specified in the global fallback method
will be returned.

.. code-block:: scala

    object Global extends GlobalSettings with SecuredSettings {

      /**
       * Called when a user isn't authorized.
       *
       * @param request The request header.
       * @param lang The current selected lang.
       * @return The result to send to the client.
       */
      override def onNotAuthorized(request: RequestHeader, lang: Lang) = {
        Some(Future.successful(Forbidden("Not authorized")))
      }
    }

Local Fallback
^^^^^^^^^^^^^^

Every controller which is derived from ``Silhouette`` base controller
has a method called ``notAuthorized``. If you override these method,
then you can return a not-authorized result similar to the global
fallback but only for this specific controller. The local fallback has
precedence over the global fallback.

.. code-block:: scala

    class Application(env: Environment[User, CookieAuthenticator])
      extends Silhouette[User, CookieAuthenticator] {

      /**
       * Called when a user isn't authorized.
       *
       * @param request The request header.
       * @return The result to send to the client.
       */
      override def notAuthorized(request: RequestHeader): Option[Future[SimpleResult]] = {
        Some(Future.successful(Forbidden("Not authorized")))
      }

      /**
       * Renders the index page.
       *
       * @returns The result to send to the client.
       */
      def index = SecuredAction(WithProvider("twitter")) { implicit request =>
        Ok(views.html.index(request.identity))
      }
    }

.. Note::
   If you don’t implement one of the both fallback methods, a 403
   response with a simple message will be displayed to the user.

Handle Ajax requests
--------------------

Applications that accept both Ajax and normal requests should likely provide
a JSON result to the first and a different result to others. There are two different
approaches to achieve this. The first approach uses a non-standard HTTP
request header. The Play application can check for this header and
respond with a suitable result. The second approach uses `Content
negotiation`_ to serve different versions of a document based on the
``ACCEPT`` request header.

Non-standard header
^^^^^^^^^^^^^^^^^^^

The example below uses a non-standard HTTP request header inside a
secured action and inside a fallback method for unauthenticated users.

**The JavaScript part with JQuery**

.. code-block:: javascript

    $.ajax({
        headers: { 'IsAjax': 'true' },
        ...
    });

**The Play part with a local fallback method for unauthenticated users**

.. code-block:: scala

    class Application(env: Environment[User, CookieAuthenticator])
      extends Silhouette[User, CookieAuthenticator] {

      /**
       * Called when a user isn't authenticated.
       *
       * @param request The request header.
       * @return The result to send to the client.
       */
      override def notAuthenticated(request: RequestHeader): Option[Future[SimpleResult]] = {
        val result = request.headers.get("IsAjax") match {
          case Some("true") => Json.obj("result" -> "No access")
          case _ => "No access"
        }

        Some(Future.successful(Unauthorized(result)))
      }

      /**
       * Renders the index page.
       *
       * @returns The result to send to the client.
       */
      def index = SecuredAction { implicit request =>
        val result = request.headers.get("IsAjax") match {
          case Some("true") => Json.obj("identity" -> request.identity)
          case _ => views.html.index(request.identity)
        }

        Ok(result)
      }
    }

Content negotiation
^^^^^^^^^^^^^^^^^^^

By default Silhouette supports content negotiation for the most common
media types: ``text/plain``, ``text/html``, ``application/json`` and
``application/xml``. So if no local or global fallback methods are
implemented, Silhouette responds with the appropriate response based on
the ``ACCEPT`` header defined by the user agent. The response format
will default to plain text in case the request does not match one of the
known media types. The example below uses content negotiation inside a
secured action and inside a fallback method for unauthenticated users.

**The JavaScript part with JQuery**

.. code-block:: javascript

    $.ajax({
        headers: {
            Accept : "application/json; charset=utf-8",
            "Content-Type": "application/json; charset=utf-8"
        },
        ...
    })

**The Play part with a local fallback method for unauthenticated users**

.. code-block:: scala

    class Application(env: Environment[User, CookieAuthenticator])
      extends Silhouette[User, CookieAuthenticator] {

      /**
       * Called when a user isn't authenticated.
       *
       * @param request The request header.
       * @return The result to send to the client.
       */
      override def notAuthenticated(request: RequestHeader): Option[Future[SimpleResult]] = {
        val result = render {
          case Accepts.Json() => Json.obj("result" -> "No access")
          case Accepts.Html() => "No access"
        }

        Some(Future.successful(Unauthorized(result)))
      }

      /**
       * Renders the index page.
       *
       * @returns The result to send to the client.
       */
      def index = SecuredAction { implicit request =>
       val result = render {
          case Accepts.Json() => Json.obj("identity" -> request.identity)
          case Accepts.Html() => views.html.index(request.identity)
        }

        Ok(result)
      }
    }

.. _Content negotiation: http://www.playframework.com/documentation/2.2.1/ScalaContentNegotiation
