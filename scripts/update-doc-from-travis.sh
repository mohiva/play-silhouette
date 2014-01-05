#!/bin/bash
#
# Updates the documentation in the project's GitHub Pages website from a Travis CI build.
#
# Original work:
# Copyright 2013 Xiaohao Ma (maxiaohao) - https://github.com/treelogic-swe/aws-mock/blob/master/.utility/push-to-gh-pages.sh
# Copyright 2013 Luke Spragg (Wulfspider) - https://github.com/Spoutcraft/travis-ci-resources/blob/master/update-gh-pages.sh
# Copyright 2013 Ben Limmer (l1m5) - http://benlimmer.com/2013/12/26/automatically-publish-javadoc-to-gh-pages-with-travis-ci/
#
# Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
# Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -o nounset -o errexit

if [ "$TRAVIS_REPO_SLUG" == "mohiva/play-silhouette" ] && [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ]; then

  echo "Starting documentation update process"
  cd $HOME
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"

  echo "Cloning gh-pages branch"
  git clone --quiet --branch=gh-pages https://${GH_PAGES}@github.com/$TRAVIS_REPO_SLUG.git gh-pages > /dev/null
  cd gh-pages

  echo "Copying documentation"
  target_dir="./api/master"
  mkdir -p "$target_dir"
  git rm -rf "$target_dir" || echo "No files to remove"
  cp -Rf $HOME/build/$TRAVIS_REPO_SLUG/target/scala-2.10/api "$target_dir"

  echo "Committing updated documentation"
  git add -f .
  git commit -m "Update documentation from Travis build $TRAVIS_BUILD_NUMBER"
  git push -q origin gh-pages > /dev/null

  echo "Documentation updated"
fi
