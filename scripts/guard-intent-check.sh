#!/usr/bin/env bash
# Proves ModuleGuard keeps deliberate module state across guard ticks and teardown.
#
# Usage: scripts/guard-intent-check.sh [port] [token-file]
#   port       worker harness port, default 8898
#   token-file harness config holding the token, default the local bot instance
#
# Sequence (the order matters, and it is the order that used to fail):
#   start BotBrain -> toggle a guarded module -> wait -> it must stay toggled
#   -> stop BotBrain -> wait -> it must still stay toggled
# Then the explicit force API: force on/off -> start/stop guard -> state holds.
set -euo pipefail

PORT="${1:-8898}"
TOKEN_FILE="${2:-C:/Users/rdh/Desktop/PineconeMC-Windows-MSVC-Portable-11.1.2/instances/bot/.minecraft/config/enhanced-swarm-harness.json}"
TOKEN="$(python -c "import json,sys;print(json.load(open(sys.argv[1]))['token'])" "$TOKEN_FILE")"
BASE="http://127.0.0.1:${PORT}/harness"

get()  {
    case "$1" in
        *\?*) curl -s "$BASE/$1&token=$TOKEN" ;;
        *)    curl -s "$BASE/$1?token=$TOKEN" ;;
    esac
}
post() { curl -s -X POST "$BASE/$1?token=$TOKEN" -H 'Content-Type: application/json' -d "$2"; }
active() { get "module?name=$1" | python -c 'import json,sys;d=json.load(sys.stdin);print(str(d.get("active")).lower())'; }
wait_s() { sleep "$1"; }

fails=0
check() { # label expected actual
    if [ "$2" = "$3" ]; then
        echo "PASS  $1 (expected $2, got $3)"
    else
        echo "FAIL  $1 (expected $2, got $3)"
        fails=$((fails + 1))
    fi
}

TARGET="swarm-shield"
before="$(active "$TARGET")"
echo "initial $TARGET = $before"

echo "--- 1. start BotBrain (guard active) ---"
if [ "$(active bot-brain)" != "true" ]; then
    post command '{"cmd":"toggle bot-brain"}' >/dev/null
fi
wait_s 3
if [ "$(active bot-brain)" != "true" ]; then
    echo "FAIL  could not start BotBrain"; exit 1
fi

echo "--- 2. operator toggles $TARGET while the guard runs ---"
# Read live state first: the guard may have enabled it at activation, so the
# pre-guard snapshot is not a safe basis for predicting a toggle.
pre="$(active "$TARGET")"
post command "{\"cmd\":\"toggle $TARGET\"}" >/dev/null
toggled=$([ "$pre" = "true" ] && echo false || echo true)
wait_s 3
check "$TARGET survives guard tick" "$toggled" "$(active "$TARGET")"

echo "--- 3. stop BotBrain (guard teardown) ---"
if [ "$(active bot-brain)" = "true" ]; then
    post command '{"cmd":"toggle bot-brain"}' >/dev/null
fi
wait_s 3
check "$TARGET survives guard teardown" "$toggled" "$(active "$TARGET")"

echo "--- 4. force API on, with the guard running ---"
post force "{\"name\":\"$TARGET\",\"enabled\":true}" >/dev/null
wait_s 1
if [ "$(active bot-brain)" != "true" ]; then
    post command '{"cmd":"toggle bot-brain"}' >/dev/null
fi
wait_s 3
check "forced-on $TARGET holds during guard" "true" "$(active "$TARGET")"

echo "--- 5. force API off, with the guard running ---"
post force "{\"name\":\"$TARGET\",\"enabled\":false}" >/dev/null
wait_s 3
check "forced-off $TARGET holds during guard" "false" "$(active "$TARGET")"

echo "--- 6. forced-off survives guard teardown ---"
if [ "$(active bot-brain)" = "true" ]; then
    post command '{"cmd":"toggle bot-brain"}' >/dev/null
fi
wait_s 3
check "forced-off $TARGET survives teardown" "false" "$(active "$TARGET")"

echo "--- 7. forced-on outranks a later manual toggle ---"
post force "{\"name\":\"$TARGET\",\"enabled\":true}" >/dev/null
wait_s 1
if [ "$(active bot-brain)" != "true" ]; then
    post command '{"cmd":"toggle bot-brain"}' >/dev/null
fi
wait_s 3
post command "{\"cmd\":\"toggle $TARGET\"}" >/dev/null
wait_s 3
check "forced-on $TARGET survives a manual toggle" "true" "$(active "$TARGET")"

# Leave no sticky intent behind: clear it and stop the brain.
post force "{\"name\":\"$TARGET\",\"clear\":true}" >/dev/null
wait_s 1
if [ "$(active bot-brain)" = "true" ]; then
    post command '{"cmd":"toggle bot-brain"}' >/dev/null
fi
wait_s 2

echo
if [ "$fails" -eq 0 ]; then
    echo "RESULT: PASS (all intent checks held)"
    exit 0
fi
echo "RESULT: FAIL ($fails checks failed)"
exit 1
