#!/usr/bin/env bash
#
# Publishes the SNAPSHOT versions to the Sonatype OSS repository.
#
# Licensed to the Minutemen Group under one or more contributor license
# agreements. See the COPYRIGHT file distributed with this work for
# additional information regarding copyright ownership.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you
# may not use this file except in compliance with the License. You may
# obtain a copy of the License at
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

SCRIPTS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPTS_DIR/vars.sh"

if [ "$TRAVIS_REPO_SLUG" == "minutemen/silhouette" ] &&
  [ "$TRAVIS_PULL_REQUEST" == "false" ] &&
  [ "$TRAVIS_BRANCH" == "master" ] &&
  [ "$TRAVIS_SCALA_VERSION" == "$SCALA_VERSION" ] &&
  [[ "$PROJECT_VERSION" == *"SNAPSHOT" ]]; then

  echo ""
  echo "Starting publishing SNAPSHOT version: $PROJECT_VERSION"

  ${SCRIPTS_DIR}/sbt.sh +publish

  echo ""
  echo "Finished SNAPSHOT publishing process"
else
  echo ""
  echo "Skipping SNAPSHOT publishing"
fi
