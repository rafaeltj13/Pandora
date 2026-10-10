#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
exec "$ROOT/scripts/with-env.sh" "$ROOT/apps/android/gradlew" \
    -p "$ROOT/apps/android" --console=plain "$@"
