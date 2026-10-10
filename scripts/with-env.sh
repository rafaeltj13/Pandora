#!/usr/bin/env bash
set -euo pipefail

# Keep setup local to this invocation; do not rewrite the user's shell config.
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/pandora/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export PATH="$HOME/.cargo/bin:$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
exec "$@"
