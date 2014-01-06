Silhouette Website
==================

This website is developed with [Jekyll](http://jekyllrb.com/).

Instructions
------------

First, [install Jekyll](http://jekyllrb.com/docs/installation/) and [Pygments](http://pygments.org/docs/installation/).

To preview the generated site locally, run:

    $ cd site
    $ jekyll serve --watch

This will host the website at [localhost:4000](http://localhost:4000/), regenerating automatically in case of changes.

Commits to this directory will be automatically pushed to the [gh-pages](https://github.com/mohiva/play-silhouette/tree/gh-pages) branch by the Travis CI build.

Additionally, the `api/master` subdirectory of the `gh-pages` branch will be automatically updated with the current API documentation.
