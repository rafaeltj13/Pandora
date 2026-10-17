#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
SERIAL=$("$ROOT/scripts/device.sh")
"$ROOT/scripts/android.sh" :app:assembleDebug "$@"
"$ROOT/scripts/with-env.sh" adb -s "$SERIAL" install -r "$ROOT/apps/android/app/build/outputs/apk/debug/app-debug.apk"
"$ROOT/scripts/with-env.sh" adb -s "$SERIAL" shell am start -W -n dev.rafaelm.pandora/.MainActivity
echo "Pandora is running on $SERIAL."
