#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"
TEMP_FILE=$(mktemp)
trap 'rm -f "$TEMP_FILE"' EXIT
"$ROOT/scripts/with-env.sh" cargo run --locked --quiet -p pandora-server --bin export-openapi > "$TEMP_FILE"
if [[ "${1:-}" == '--check' ]]; then
    if ! diff -u packages/contracts/openapi.json "$TEMP_FILE"; then
        echo 'API contract changed. Run make contracts and commit the generated contract.' >&2
        exit 1
    fi
else
    mkdir -p packages/contracts
    cp "$TEMP_FILE" packages/contracts/openapi.json
fi
