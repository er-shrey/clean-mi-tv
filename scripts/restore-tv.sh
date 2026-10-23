#!/usr/bin/env bash
# Put the TV back the way it was before debloat/xiaomi-mitv4i.txt and the new launcher.
# Needs USB debugging on (Settings > Device Preferences > Developer options) and the "Allow USB debugging?" prompt
# accepted on the TV.
#
# Usage: TV_HOST=<tv ip> scripts/restore-tv.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export TV_HOST
TV="tools/venv/bin/python -I tools/tv.py"

# 1. Re-enable the list. restore.sh replays the record tools/disable.sh saved in ~/.cleanyourtv/, so packages that
#    were already disabled before stay off; without a record it enables the whole list.
tools/restore.sh debloat/xiaomi-mitv4i.txt com.google.android.tvlauncher

# 2. The stock launcher: build-and-install disabled it outside the list, so turn it back on and give it Home again.
$TV pm enable com.google.android.tvlauncher
$TV cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity

# 3. The optional extras from the README, back to Android's defaults: Private DNS, and Location (device only),
#    which shows the Wi-Fi name in the control center.
$TV settings put global private_dns_mode opportunistic
$TV settings put secure location_providers_allowed -gps
$TV settings put secure location_mode 0

# 4. Optional: remove the new launcher too. Uncomment to do it.
# $TV pm uninstall com.gathin.tvhome

$TV 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
echo "Done. Switch USB debugging off again afterwards."
