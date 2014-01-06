#!/bin/bash
#
# Updates the project's website from a Travis CI build.
#
# The contents of the 'gh-pages' branch will be replaced with the content of
# the 'site' directory of the 'master' branch.
#
# Additionally, the API documentation will be generated and stored in the
# 'api/master' directory of the 'gh-pages' branch.
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
  source_dir="$HOME/build/$TRAVIS_REPO_SLUG"
  cd "$source_dir"

  echo ""
  pwd
  ls -la

  echo ""
  echo "$source_dir/site:"
  ls -la "$source_dir/site"

  target_dir="$HOME/gh-pages"
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"
  git clone --quiet --branch=gh-pages https://${GH_PAGES}@github.com/$TRAVIS_REPO_SLUG.git "$target_dir" > /dev/null
  cd "$target_dir"
  echo ""
  pwd
  ls -la

  echo "Updating site contents"

  echo ""
  echo "executing mkdir -p $HOME/tmp"
  mkdir -p "$HOME/tmp"
  echo "exit code: $?"

  echo ""
  echo "before move: $HOME/tmp"
  ls -la "$HOME/tmp"

  echo ""
  echo "executing mv api $HOME/tmp"
  mv api "$HOME/tmp"
  echo "exit code: $?"

  echo ""
  echo "after move: $HOME/tmp"
  ls -la "$HOME/tmp"

  echo ""
  echo "$target_dir:"
  ls -la "$target_dir"

  echo ""
  echo "executing rm -Rf $target_dir/*"
  rm -Rf "$target_dir/*"
  echo "exit code: $?"

  echo ""
  echo "$target_dir:"
  ls -la "$target_dir"

  echo ""
  echo "executing mv $HOME/tmp/api" .
  mv "$HOME/tmp/api" .
  echo "exit code: $?"

  echo ""
  echo "$source_dir:"
  ls -la "$source_dir"

  echo ""
  echo "$source_dir/site:"
  ls -la "$source_dir/site"

  echo ""
  echo "copying $source_dir/site/* to $target_dir"
  cp -R "$source_dir/site/*" "$target_dir"
  echo "exit code: $?"

  echo ""
  echo "$target_dir:"
  ls -la "$target_dir"

  original_commit=`git rev-parse HEAD`
  git add -all .
  git commit -m "Update website from Travis build $TRAVIS_BUILD_NUMBER"
  git push -q origin gh-pages > /dev/null
  git log --name-status $original_commit..HEAD

  echo "Updating API documentation"
  api_master_dir="$target_dir/api/master"
  rm -Rf "$api_master_dir"
  mkdir -p "$api_master_dir"
  cp -Rf "$source_dir/target/scala-2.10/api/*" "$api_master_dir"
  original_commit=`git rev-parse HEAD`
  git add -all .
  git commit -m "Update API documentation from Travis build $TRAVIS_BUILD_NUMBER"
  git push -q origin gh-pages > /dev/null
  git log --name-status $original_commit..HEAD
fi
