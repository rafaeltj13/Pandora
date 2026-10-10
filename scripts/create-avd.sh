#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
AVD_NAME="${PANDORA_AVD:-Pandora_API_36}"

if "$ROOT/scripts/with-env.sh" emulator -list-avds | grep -Fxq "$AVD_NAME"; then
    echo "AVD $AVD_NAME already exists."
    exit 0
fi

printf 'no\n' | "$ROOT/scripts/with-env.sh" avdmanager create avd \
    --name "$AVD_NAME" --package 'system-images;android-36;google_apis;x86_64' \
    --device 'pixel_5'
echo "Created $AVD_NAME. Run make emulator to open it."
