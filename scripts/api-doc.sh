#!/usr/bin/env bash
#
# Updates the API-Doc in the 'gh-pages' branch.
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
  [ "$TRAVIS_SCALA_VERSION" == "$SCALA_VERSION" ]; then

  echo ""
  echo "Starting API-Doc update process"

  GH_PAGES_DIR="$HOME/.sbt/ghpages/group.minutemen/root"
  ENCRYPTED_KEY_VAR="encrypted_${ENCRYPTION_ID}_key"
  ENCRYPTED_IV_VAR="encrypted_${ENCRYPTION_ID}_iv"
  ENCRYPTED_KEY_FILE="${SCRIPTS_DIR}/api-doc.key.enc"
  DECRYPTED_KEY_FILE="${SCRIPTS_DIR}/api-doc.key"
  ENCRYPTED_KEY=${!ENCRYPTED_KEY_VAR}
  ENCRYPTED_IV=${!ENCRYPTED_IV_VAR}

  openssl aes-256-cbc -K ${ENCRYPTED_KEY} -iv ${ENCRYPTED_IV} -in ${ENCRYPTED_KEY_FILE} -out ${DECRYPTED_KEY_FILE} -d
  chmod 600 ${DECRYPTED_KEY_FILE}

  printf "%s\n" \
    "Host github.com" \
    "    HostName github.com" \
    "    IdentityFile ${DECRYPTED_KEY_FILE}" \
    "    IdentitiesOnly yes" \
    >> ~/.ssh/config

  rm -rf "$(dirname ${GH_PAGES_DIR})"
  mkdir -p "$(dirname ${GH_PAGES_DIR})"
  git clone --quiet --branch=gh-pages git@github.com:${TRAVIS_REPO_SLUG}.git "$GH_PAGES_DIR" > /dev/null
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"
  git config --global push.default simple

  ${SCRIPTS_DIR}/sbt.sh ghpages-push-site

  echo ""
  echo "Finished API-Doc update process"
else
  echo ""
  echo "Skipping API-Doc update"
fi
