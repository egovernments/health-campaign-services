#!/bin/bash

BASEDIR="$(cd "$(dirname "$0")" && pwd)"

msg() {
  echo -e "\n\n\033[32;32m$1\033[0m"
}


# msg "Pre-building all packages"
# yarn build
# sleep 5

msg "Building and publishing css"
cd "$BASEDIR/packages/css" && rm -rf dist && yarn && npm publish --tag campaign-1.0


# msg "Building and publishing libraries"
# cd "$BASEDIR/packages/modules/workbench-hcm" &&   rm -rf dist && yarn&& npm publish --tag workbench-1.0

