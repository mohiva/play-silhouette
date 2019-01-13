#!/usr/bin/env bash
#
# Builds the project in the continuous integration environment.
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

echo ""
echo "Validating code formatting"
${SCRIPTS_DIR}/validate-format.sh

echo ""
echo "Validating code style"
${SCRIPTS_DIR}/validate-style.sh

echo ""
echo "Testing and generating documentation"
${SCRIPTS_DIR}/sbt.sh clean coverage test doc

echo ""
echo "Aggregate coverage from sub-projects"
${SCRIPTS_DIR}/sbt.sh coverageReport coverageAggregate

echo ""
echo "Build finished"
echo ""
