#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ "${PANDORA_ENV_READY:-}" != 1 ]]; then
    exec "$ROOT/scripts/with-env.sh" env PANDORA_ENV_READY=1 "$0"
fi
cd "$ROOT"
mkdir -p .local
EMULATOR_PID=''

cleanup() {
    [[ -z "$EMULATOR_PID" ]] || kill "$EMULATOR_PID" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM

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
echo 'Pandora is ready. Ctrl-C stops an emulator started by this command. Logs: .local/.'
if [[ -n "$EMULATOR_PID" ]]; then
    wait "$EMULATOR_PID"
fi
