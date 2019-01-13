#!/usr/bin/env bash
#
# Creates some additional build variables.
#
# Currently it's not possible to start a single script for a build
# matrix. https://github.com/travis-ci/travis-ci/issues/929
#
# Therefore we create the vars only for one build by using a locking
# approach. All other builds will wait until the vars.sh file is
# created. This approach prevents us for race conditions with multiple
# builds.
#
# http://www.davidpashley.com/articles/writing-robust-shell-scripts/
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
CACHE_FILE="/tmp/travis.build.vars.sh"
LOCK_FILE="/tmp/travis.build.vars.lock"
COUNTER=0
TIMEOUT=20

if [ -f ${CACHE_FILE} ]; then
  source ${CACHE_FILE}
elif ( set -o noclobber; echo "$$" > "$LOCK_FILE") 2> /dev/null; then
  # BK: this will cause the lock file to be deleted in case of other exit
  trap 'rm -f "$LOCK_FILE"; exit $?' INT TERM EXIT

  # critical-section BK: (the protected bit)
  OUTPUT=$(${SCRIPTS_DIR}/sbt.sh --error 'set showSuccess := false' buildVersions 2>&- | tail -2)
  PROJECT_VERSION=$(echo "${OUTPUT//$'\n'/ }" | sed "s/.*PROJECT_VERSION \([^ ]*\).*$/\1/")
  SCALA_VERSION=$(echo "${OUTPUT//$'\n'/ }" | sed "s/.*SCALA_VERSION \([^ ]*\).*$/\1/")

  printf "%s\n" \
    "#!/usr/bin/env bash" \
    "PROJECT_VERSION=\"${PROJECT_VERSION}\"" \
    "SCALA_VERSION=\"${SCALA_VERSION}\"" \
  > ${CACHE_FILE}

  rm -f "$LOCK_FILE"
  trap - INT TERM EXIT
else
  echo "The process $(cat ${LOCK_FILE}) currently tries to create the file $CACHE_FILE"
  echo "Wait for the file to be created "
  while [ "$COUNTER" -lt ${TIMEOUT} -a ! -e ${CACHE_FILE} ]; do
    COUNTER=$((COUNTER+1))
    echo -n "."
    sleep 1
  done
  echo ""
  if [ -e ${CACHE_FILE} ]; then
    source ${CACHE_FILE}
  else
    echo "It seems that the file couldn't be created by process $(cat ${LOCK_FILE}) within $TIMEOUT seconds "
    exit 1
  fi
fi
