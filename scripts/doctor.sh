#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ "${PANDORA_ENV_READY:-}" != 1 ]]; then
    exec "$ROOT/scripts/with-env.sh" env PANDORA_ENV_READY=1 "$0"
fi

missing=0
for command in java cargo rustc sdkmanager adb emulator make cc; do
    if command -v "$command" >/dev/null 2>&1; then
        printf '%-12s %s\n' "$command" "$(command -v "$command")"
    else
        printf 'MISSING: %s\n' "$command"
        missing=1
    fi
done
if (( missing )); then
    echo 'Run make setup to install the missing toolchains.'
    exit 1
fi
java -version
rustc --version
emulator -accel-check
adb devices -l
