#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ "${PANDORA_ENV_READY:-}" != 1 ]]; then
    exec "$ROOT/scripts/with-env.sh" env PANDORA_ENV_READY=1 "$0" "$@"
fi

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    if [[ "$(adb -s "$ANDROID_SERIAL" get-state 2>/dev/null || true)" != device ]]; then
        echo "Device $ANDROID_SERIAL is unavailable. Connect it and authorize USB debugging." >&2
        exit 1
    fi
    printf '%s\n' "$ANDROID_SERIAL"
    exit 0
fi

mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
case ${#devices[@]} in
    0) echo 'No authorized Android device. Start make emulator, or connect and authorize your phone.' >&2; exit 1 ;;
    1) printf '%s\n' "${devices[0]}" ;;
    *) echo 'Multiple devices. Select one with: env ANDROID_SERIAL=<serial> make android' >&2
       adb devices -l >&2; exit 1 ;;
esac
