#!/usr/bin/env bash
# Set the text that scrolls on the Home screen, from this Mac. Each line becomes its own item.
# Needs USB debugging on. Home picks the new text up at the end of the current pass (no restart).
#
# Usage: scripts/marquee.sh "Dinner at 8" "Match on HDMI 3"    one item per argument
#        scripts/marquee.sh --show                             print the current text
#        scripts/marquee.sh --reset                            back to marquee_text from config.xml
#        TV_HOST=<tv ip> scripts/marquee.sh ...
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export TV_HOST
TV="tools/venv/bin/python -I tools/tv.py"
URI=content://com.gathin.tvhome.theme/marquee.txt
[ $# -gt 0 ] || { sed -n '2,8p' "$0"; exit 1; }
case "$1" in
  --show) $TV "content read --uri $URI 2>/dev/null || echo '(no marquee.txt: config.xml marquee_text shows)'"; exit ;;
  --reset) $TV "content delete --uri $URI"; echo "reset"; exit ;;
esac
# base64 keeps quotes, emoji and other characters away from the TV's shell.
B64=$(printf '%s\n' "$@" | base64 | tr -d '\n')
$TV "echo $B64 | base64 -d > /data/local/tmp/marquee.txt && content write --uri $URI < /data/local/tmp/marquee.txt; rm -f /data/local/tmp/marquee.txt; content read --uri $URI"
