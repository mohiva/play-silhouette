#!/usr/bin/env bash
#
# Publishes the coverage report to http://coveralls.io.
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

if [ "$TRAVIS_SCALA_VERSION" == "$SCALA_VERSION" ]; then
  echo ""
  echo "Publish coverage report"
  ${SCRIPTS_DIR}/sbt.sh coveralls

  echo ""
  echo "Report published"
  echo ""
else
  echo ""
  echo "Skipping coverage publishing"
fi
