#!/usr/bin/env bash
# Build the launcher from launcher/ (with launcher/app/src/main/res/values/config.xml) and install it as Home.
# Needs USB debugging on, JDK 17 and the Android SDK (see README).
#
# Usage: TV_HOST=<tv ip> scripts/build-and-install.sh
#
# Leave HOME_KEY_SERVICE off: on the Mi TV 4A it makes the remote's PatchWall button open Settings, but a held
# Volume key then stops repeating. Run with HOME_KEY_SERVICE=1 only if you want that trade.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export TV_HOST
launcher/build-and-install.sh
