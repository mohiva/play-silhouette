Testing
=======

.. versionadded:: 2.0

Test Helpers
------------

Silhouette provides some test helpers that can be used to easily test your Silhouette
application. These helpers are located in the additional TestKit dependency.

.. code-block:: scala

    libraryDependencies ++= Seq(
      "com.mohiva" %% "play-silhouette-testkit" % "version" % "test"
    )

After providing the dependency, the helpers can be used by importing the following package
into your test scenarios.

.. code-block:: scala

  import com.mohiva.play.silhouette.test._

All helpers are test framework agnostic. You can use it with `Specs2`_, `ScalaTest`_ or every
other testing framework. In our examples we use `Specs2`_ to demonstrate how to test Silhouette
applications, because it's the default framework shipped with Play and we use it to test Silhouette
itself.

.. _Specs2: http://etorreborre.github.io/specs2/
.. _ScalaTest: http://www.scalatest.org/

Test Silhouette actions
-----------------------

With the previous mentioned test helpers it's really easy to test your Silhouette actions.
As first lets look how a typical controller instance could look like:

.. code-block:: scala

  class UserController(
    val env: Environment[User, CookieAuthenticator])
    extends Silhouette[User, CookieAuthenticator] {

    /**
     * Gets a user.
     */
    def user = SecuredAction { implicit request =>
      Ok(Json.toJson(request.identity))
    }

    /**
     * Checks if a user is authenticated.
     */
    def isAuthenticated = UserAwareAction { implicit request =>
      request.identity match {
        case Some(identity) => Ok
        case None => Unauthorized
      }
    }
  }

If you like to test this controller, you must provide an environment that can handle your
``User`` implementation and the ``CookieAuthenticator`` which is used to track your user
after a successful authentication. For this case Silhouette provides a ``FakeEnvironment``
which sets up automatically all components needed to test your specific actions.

FakeEnvironment
^^^^^^^^^^^^^^^

The fake environment does all the annoying steps for you to create and instantiate all
dependencies that you need for your test. You must only specify the identity that gets
returned by calling ``request.identity`` in your action and the authenticator instance
that should track this user.

.. code-block:: scala

  val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
  implicit val env = FakeEnvironment[User, CookieAuthenticator](identity)


Under the hood, the environment instantiates a ``FakeIdentityService`` which stores
your given identity and returns it if needed. It instantiates also the appropriate
``AuthenticatorService`` based on your defined ``Authenticator`` type. All Authenticator
services are real authenticator service instances set up with their default values and
dependencies.

FakeRequest
^^^^^^^^^^^

Let us summarize briefly how an ``Action`` in Play works. An action is basically
an anonymous function that handles a ``Request`` and returns a ``Result``. With
this in mind we must now create a request that we can pass to our action. Play
ships with a test helper called ``FakeRequest`` which does exactly what we want.
But this helper cannot embed an authenticator into the created fake request.
Therefore we have spend Play's fake request helper two additional methods.

The first method accepts an authenticator instance which will then embedded into
the request.

.. code-block:: scala

  val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
  implicit val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
  val authenticator = new CookieAuthenticator("test", identity.loginInfo, ...)
  val request = FakeRequest().withAuthenticator(authenticator)


The second method accepts a ``LoginInfo`` instance for which then an authenticator
will be created and embedded into the request.

.. code-block:: scala

  val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
  implicit val env = FakeEnvironment[FakeIdentity, CookieAuthenticator](identity)
  val request = FakeRequest().withAuthenticator(identity.loginInfo)

.. Note::

  To embed an authenticator into a request you need an implicit environment in scope.

Tying the Pieces Together
^^^^^^^^^^^^^^^^^^^^^^^^^

So far, we've learned how to setup a test environment and how to create a request
which contains an embedded authenticator. Now we combine these techniques and create
a complete controller test.

Simulate a missing authenticator
````````````````````````````````

To simulate that an authenticator couldn't be found for a request you must only
submit a request without an authenticator.

.. code-block:: scala

  class UserSpec extends PlaySpecification {

    "The `user` method" should {
      "return status 401 if no authenticator was found" in new WithApplication {
        val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
        val env = FakeEnvironment[User, CookieAuthenticator](identity)
        val request = FakeRequest()

        val controller = new UserController(env)
        val result = controller.user(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }

    "The `isAuthenticated` method" should {
      "return status 401 if no authenticator was found" in new WithApplication {
        val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
        val env = FakeEnvironment[User, CookieAuthenticator](identity)
        val request = FakeRequest()

        val controller = new UserController(env)
        val result = controller.isAuthenticated(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }
  }


Simulate a missing identity
```````````````````````````

To simulate that an identity couldn't be found for a valid authenticator you must pass
different login information to the user and the authenticator.

.. code-block:: scala

  class UserSpec extends PlaySpecification {

    "The `user` method" should {
      "return status 401 if authenticator but no identity was found" in new WithApplication {
        val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
        implicit val env = FakeEnvironment[User, CookieAuthenticator](identity)
        val request = FakeRequest()
          .withAuthenticator(LoginInfo("xing", "comedian@watchmen.com"))

        val controller = new UserController(env)
        val result = controller.user(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }

    "The `isAuthenticated` method" should {
      "return status 401 if authenticator but no identity was found" in new WithApplication {
        val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
        implicit val env = FakeEnvironment[User, CookieAuthenticator](identity)
        val request = FakeRequest()
          .withAuthenticator(LoginInfo("xing", "comedian@watchmen.com"))

        val controller = new UserController(env)
        val result = controller.isAuthenticated(request)

        status(result) must equalTo(UNAUTHORIZED)
      }
    }
  }

Simulate an authenticated identity
``````````````````````````````````

To simulate an authenticated identity we must submit a valid authenticator and
the login information of both the authenticator and the identity must be the same.

.. code-block:: scala

  class UserSpec extends PlaySpecification {

    "The `user` method" should {
      "return status 200 if authenticator and identity was found" in new WithApplication {
        val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
        implicit val env = FakeEnvironment[User, CookieAuthenticator](identity)
        val request = FakeRequest().withAuthenticator(identity.loginInfo)

        val controller = new UserController(env)
        val result = controller.user(request)

        status(result) must equalTo(OK)
      }
    }

    "The `isAuthenticated` method" should {
      "return status 200 if authenticator and identity was found" in new WithApplication {
        val identity = User(LoginInfo("facebook", "apollonia.vanova@watchmen.com"))
        implicit val env = FakeEnvironment[User, CookieAuthenticator](identity)
        val request = FakeRequest().withAuthenticator(identity.loginInfo)

        val controller = new UserController(env)
        val result = controller.isAuthenticated(request)

        status(result) must equalTo(OK)
      }
    }
  }


Test default Play actions
-------------------------

Typically Silhouette authentication code is implemented inside default Play actions. To test
such actions you don't need specific helper classes. Here you could use `Mockito`_ to mock the
Silhouette instances or other related testing tools.

.. _Mockito: https://code.google.com/p/mockito/
