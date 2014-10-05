Contribute
==========

How to contribute
-----------------

Silhouette is an open source project. Contributions are appreciated.

See the current `list of contributors`_ and join them!

Some ways in which you can contribute are: reporting errors, improving
documentation, adding examples, adding support for more services, fixing
bugs, suggesting new features, adding test cases, translating messages,
and whatever else you can think of that may be helpful. If in doubt,
just ask.

.. _list of contributors: https://github.com/mohiva/play-silhouette/graphs/contributors


The project structure
---------------------

The core package describes the interfaces of Silhouette and the
contrib package contains the concrete implementations of all the
interfaces.


Development workflow
--------------------

Development is coordinated via `GitHub`_. Ideas for improvements are
discussed using `issues`_.

For a more streamlined experience for all people involved, we encourage
contributors to follow the practices described at `GitHub workflow for
submitting pull requests`_.

Scala source code should follow the conventions documented in the `Scala
Style Guide`_. Additionally, acronyms should be capitalized. To have
your code automatically reformatted, run this command before committing
your changes:

.. code-block:: bash

    scripts/reformat

.. Important::
   After submitting your pull request, please `watch the result`_ of the
   automated Travis CI build and correct any reported errors or
   inconsistencies.

.. _GitHub: https://github.com/mohiva/play-silhouette
.. _issues: https://github.com/mohiva/play-silhouette/issues
.. _GitHub workflow for submitting pull requests: https://www.openshift.com/wiki/github-workflow-for-submitting-pull-requests
.. _Scala Style Guide: http://docs.scala-lang.org/style/
.. _watch the result: https://travis-ci.org/mohiva/play-silhouette/pull_requests


Help to improve the documentation
---------------------------------

Every software project is only as good as its documentation. So we do
our best to cover all the code with a good structured, meaningful and
up-to-date documentation. But like in the most open source projects,
time is a precious commodity. So every help is appreciated to improve
the documentation.


Edit on GitHub
^^^^^^^^^^^^^^

For small typo changes the documentation can be edited directly on
GitHub. This is very easy by following the "Edit on GitHub" button
at the top of th page. After you have made your changes you can commit
it with an meaningful commit message. It will then create automatically
a new pull request with your proposed changes.


Edit it locally
^^^^^^^^^^^^^^^

The documentation is written in `RST`_ which can be rendered by `Sphinx`_
into many output formats like HTML, PDF and `a lot more`_. To render
the documentation into static HTML which looks the same as this
documentation on `"Read the docs"`_, you must install Sphinx and the
`"Read the Docs" theme`_.

.. code-block:: bash

    pip install sphinx sphinx_rtd_theme

.. Note::
   Python must be installed on your system. Please consult the documentation
   of your OS on how to do it.

If you have edited the documentation files, you can create the static
HTML files by executing the following command in the ``docs`` directory.

.. code-block:: bash

    make html

The documentation can now be found in the ``_build/html`` directory.
Browse the ``index.html`` with your favorite browser to view the changes.

.. _RST: http://docutils.sourceforge.net/docs/user/rst/quickref.html
.. _Sphinx: http://sphinx-doc.org/
.. _a lot more: http://sphinx-doc.org/builders.html
.. _"Read the docs": https://readthedocs.org/
.. _"Read the Docs" theme: https://github.com/snide/sphinx_rtd_theme


License and Copyright
---------------------

By submitting work via pull requests, issues, wiki, or any other means,
contributors indicate their agreement to publish their work under this
project’s license and also attest that they are the authors of the work
and grant a copyright license to the Mohiva Organisation, unless the
contribution clearly states a different copyright notice (e.g., it
contains original work by a third party).

The code is licensed under `Apache License v2.0`_ and the documentation
under `CC BY 3.0`_.

This project is derived from `SecureSocial`_, Copyright 2013 Jorge Aliss
(jaliss at gmail dot com) - twitter: @jaliss. Thanks to `Jorge Aliss`_
for his great work.

.. _Apache License v2.0: http://www.apache.org/licenses/LICENSE-2.0
.. _CC BY 3.0: http://creativecommons.org/licenses/by/3.0/
.. _SecureSocial: https://github.com/jaliss/securesocial
.. _Jorge Aliss: https://github.com/jaliss
