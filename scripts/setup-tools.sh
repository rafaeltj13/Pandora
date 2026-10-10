#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    echo 'This bootstrap supports Linux x86_64. Install native toolchains for your host manually.' >&2
    exit 1
fi
TOOL_DIR="$HOME/.local/share/pandora"
DOWNLOAD_DIR="$HOME/.cache/pandora/downloads"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
mkdir -p "$TOOL_DIR" "$DOWNLOAD_DIR" "$SDK_DIR"

download() {
    local url=$1 file=$2 checksum=$3
    if [[ ! -f "$file" ]] || ! printf '%s  %s\n' "$checksum" "$file" | sha256sum --check --status; then
        curl --fail --location --retry 3 --silent --show-error "$url" -o "$file.part"
        printf '%s  %s\n' "$checksum" "$file.part" | sha256sum --check --status
        mv "$file.part" "$file"
    fi
}

if [[ ! -x "$TOOL_DIR/jdk-21/bin/java" ]] && [[ -z "${JAVA_HOME:-}" ]]; then
    echo 'Installing Temurin JDK 21 in your user directory...'
    download 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz' \
        "$DOWNLOAD_DIR/jdk-21.tar.gz" 'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94'
    mkdir -p "$TOOL_DIR/jdk-21"
    tar -xzf "$DOWNLOAD_DIR/jdk-21.tar.gz" -C "$TOOL_DIR/jdk-21" --strip-components=1
fi

if [[ ! -x "$HOME/.cargo/bin/rustup" ]]; then
    echo 'Installing Rust in your user directory...'
    curl --fail --location --silent --show-error 'https://static.rust-lang.org/rustup/dist/x86_64-unknown-linux-gnu/rustup-init.sha256' -o "$DOWNLOAD_DIR/rustup-init.sha256"
    RUSTUP_SHA=$(cut -d ' ' -f 1 "$DOWNLOAD_DIR/rustup-init.sha256")
    download 'https://static.rust-lang.org/rustup/dist/x86_64-unknown-linux-gnu/rustup-init' "$DOWNLOAD_DIR/rustup-init" "$RUSTUP_SHA"
    chmod +x "$DOWNLOAD_DIR/rustup-init"
    "$DOWNLOAD_DIR/rustup-init" -y --profile minimal --default-toolchain stable --no-modify-path
fi
"$HOME/.cargo/bin/rustup" toolchain install stable --profile minimal --component rustfmt --component clippy

if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
    echo 'Installing Android SDK command-line tools...'
    download 'https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip' \
        "$DOWNLOAD_DIR/android-commandline-tools.zip" '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
    TEMP_DIR=$(mktemp -d)
    trap 'rm -rf "$TEMP_DIR"' EXIT
    unzip -q "$DOWNLOAD_DIR/android-commandline-tools.zip" -d "$TEMP_DIR"
    mkdir -p "$SDK_DIR/cmdline-tools"
    mv "$TEMP_DIR/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi

echo 'Review the licenses for the requested Android SDK packages when prompted.'
"$ROOT/scripts/with-env.sh" sdkmanager --sdk_root="$SDK_DIR" --channel=0 \
    'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' \
    'emulator' 'system-images;android-36;google_apis;x86_64'

echo 'Toolchains installed. Run make doctor, then make avd.'
