#!/bin/bash

# This script will be run by bazel when the build process starts to
# generate key-value information that represents the status of the
# workspace. The output should be like
#
# KEY1 VALUE1
# KEY2 VALUE2
#
# If the script exits with non-zero code, it's considered as a failure
# and the output will be discarded.

function rev() {
  cd $1; git describe --always --match "v[0-9].*" --dirty
}

echo STABLE_BUILD_OWNERS-AUTOASSIGN_LABEL $(rev .)
echo STABLE_BUILD_OWNERS_LABEL $(rev .)
