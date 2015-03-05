.. _identity_impl:

Identity
========

Silhouette defines a user through its ``Identity`` trait. This trait
doesn't define any defaults. Thus, you are free to design your user
according to your beliefs.

.. _login-info:

Login information
-----------------

The `LoginInfo`_ acts as Silhouette's identity ID and it's needed to identify
a user in the Silhouette workflow. It contains the data about the provider that
authenticated an identity. This information is mostly publicly available
and it simply consists of a unique provider ID and a unique key which
identifies a user to this provider (userID, email, …).

If the application supports the concept of “merged identities”, i.e.,
the same user being able to authenticate through different providers,
then make sure that the login information gets stored separately. Later
you can see how this can be implemented.

.. _LoginInfo: https://github.com/mohiva/play-silhouette/blob/master/silhouette/app/com/mohiva/play/silhouette/api/LoginInfo.scala#L28

Implement an identity
---------------------

To define a user for your application, create a class that extends the
``Identity`` trait. Your user class may contain any information, without
restriction.

Below we define a simple user with a unique ID, the login information for
the provider which authenticates that user and some basic information like
name and email.

.. code-block:: scala

    case class User(
      userID: Long,
      loginInfo: LoginInfo,
      name: String,
      email: Option[String]) extends Identity


.. _identity_service_impl:

The identity service
--------------------

Silhouette relies on an implementation of ``IdentityService`` to handle
all the operations related to retrieving identities. Using this
delegation model means you are not forced to use a particular model object or
persistence mechanism. Instead, you provide a service that translates
back and forth between your models and what Silhouette understands.

The ``IdentityService`` defines a raw type which must be derived from
``Identity``. This has the advantage that your service implementation
returns always your implementation of ``Identity``.

Here's a sample implementation:

.. code-block:: scala

    /**
     * A custom user service which relies on the previous defined `User`.
     */
    class UserService(userDAO: UserDAO) extends IdentityService[User] {

      /**
       * Retrieves a user that matches the specified login info.
       *
       * @param loginInfo The login info to retrieve a user.
       * @return The retrieved user or None if no user could be retrieved for the given login info.
       */
      def retrieve(loginInfo: LoginInfo): Future[Option[User]] = userDAO.findByLoginInfo(loginInfo)
    }

Link an identity to multiple login information
----------------------------------------------

Silhouette doesn't provide built-in functionality to link multiple
identities to a single user, but it makes this task very easy
by providing the basics. In the abstract this task can be done by
linking the different login information returned by each provider,
with a single user identified by an unique ID. The unique user will
be represented by your implementation of ``Identity`` and the login
information will be returned by every provider implementation after
a successful authentication. Now with this basic knowledge it’s up
to you to implement the linking in such a way that it fits into your
application architecture.
