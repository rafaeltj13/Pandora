#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ "${PANDORA_ENV_READY:-}" != 1 ]]; then
    exec "$ROOT/scripts/with-env.sh" env PANDORA_ENV_READY=1 "$0"
fi
cd "$ROOT"
mkdir -p .local
PORT="${PANDORA_PORT:-8080}"
SERVER_PID=''
EMULATOR_PID=''

cleanup() {
    [[ -z "$SERVER_PID" ]] || kill "$SERVER_PID" 2>/dev/null || true
    [[ -z "$EMULATOR_PID" ]] || kill "$EMULATOR_PID" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM

if curl --fail --silent --max-time 2 "http://127.0.0.1:$PORT/api/v1/status" | \
    python3 -c 'import json,sys; sys.exit(json.load(sys.stdin).get("app") != "Pandora")' 2>/dev/null; then
    echo "Using the Pandora server already running on port $PORT."
else
    cargo build --locked --quiet -p pandora-server --bin pandora-server
    PANDORA_BIND="127.0.0.1:$PORT" target/debug/pandora-server > .local/server.log 2>&1 &
    SERVER_PID=$!
    ready=0
    for _ in $(seq 1 30); do
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            cat .local/server.log >&2
            exit 1
        fi
        if curl --fail --silent --max-time 1 "http://127.0.0.1:$PORT/health" >/dev/null; then
            ready=1
            break
        fi
        sleep 1
    done
    if (( ! ready )); then
        echo 'Server startup timed out. See .local/server.log.' >&2
        exit 1
    fi
fi

if [[ -z "${ANDROID_SERIAL:-}" ]] && ! adb devices | awk 'NR > 1 && $2 == "device" {found=1} END {exit !found}'; then
    "$ROOT/scripts/emulator.sh" > .local/emulator.log 2>&1 &
    EMULATOR_PID=$!
    echo 'Starting the Pandora emulator...'
    ready=0
    for _ in $(seq 1 180); do
        if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
            tail -n 30 .local/emulator.log >&2
            exit 1
        fi
        if adb devices | awk 'NR > 1 && $2 == "device" {found=1} END {exit !found}'; then
            ready=1
            break
        fi
        sleep 1
    done
    if (( ! ready )); then
        echo 'Emulator startup timed out. See .local/emulator.log.' >&2
        exit 1
    fi
fi

SERIAL=$("$ROOT/scripts/device.sh")
ready=0
for _ in $(seq 1 180); do
    if [[ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
        ready=1
        break
    fi
    sleep 1
done
if (( ! ready )); then
    echo "Device $SERIAL did not finish booting." >&2
    exit 1
fi
ANDROID_SERIAL="$SERIAL" "$ROOT/scripts/run-android.sh"
echo 'Pandora is ready. Ctrl-C stops processes started by this command. Logs: .local/.'
if [[ -n "$SERVER_PID" ]]; then
    wait "$SERVER_PID"
else
    # Keep a newly started emulator alive when reusing an existing server.
    while true; do sleep 5; done
fi
