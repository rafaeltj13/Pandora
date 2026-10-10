#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
"$ROOT/scripts/create-avd.sh"
exec "$ROOT/scripts/with-env.sh" emulator -avd "${PANDORA_AVD:-Pandora_API_36}" \
    -memory 2048 -cores 2 -no-boot-anim "$@"
