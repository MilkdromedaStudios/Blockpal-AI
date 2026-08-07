#!/usr/bin/env bash
# Runs the Bedrock add-on's tests in every world configuration that matters.
set -u
cd "$(dirname "$0")"
status=0
run() {
  echo "--- $1 ---"
  shift
  if ! env "$@" node --import ./register.mjs run-tests.mjs; then status=1; fi
}
run "a normal world (stable APIs)"                     BLOCKPAL_TEST_X=0
run "a world with Beta APIs enabled"                   BLOCKPAL_TEST_BETA=1
run "an older runtime (no custom commands)"            BLOCKPAL_TEST_NO_CUSTOM_COMMANDS=1
exit $status
