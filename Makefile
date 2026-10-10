.DEFAULT_GOAL := help
.PHONY: help setup doctor dev server avd emulator devices android build check check-rust check-android test-device contracts contracts-check fmt

help:
	@printf '%s\n' 'Pandora' '' \
	  '  make setup          Install user-local Rust, Java, and Android SDK tools' \
	  '  make doctor         Check toolchains, KVM, and connected devices' \
	  '  make dev            Start server, start/use emulator, install and launch app' \
	  '  make server         Run the Rust API at http://127.0.0.1:8080' \
	  '  make avd            Create the Android virtual device' \
	  '  make emulator       Open the graphical Android emulator' \
	  '  make devices        List emulator/phone serials' \
	  '  make android        Build, forward port, install and launch on a device' \
	  '  make build          Build server and debug APK' \
	  '  make check          Rust checks, contract consistency, Android tests/lint/build' \
	  '  make test-device    Run Compose UI tests on the selected device' \
	  '  make contracts      Regenerate the shared OpenAPI document' \
	  '  make fmt            Format Rust source'

setup:
	./scripts/setup-tools.sh
doctor:
	./scripts/doctor.sh
dev:
	./scripts/dev.sh
server:
	./scripts/with-env.sh cargo run --locked -p pandora-server
avd:
	./scripts/create-avd.sh
emulator:
	./scripts/emulator.sh
devices:
	./scripts/with-env.sh adb devices -l
android:
	./scripts/run-android.sh
build:
	./scripts/with-env.sh cargo build --locked --workspace
	./scripts/android.sh :app:assembleDebug
check: check-rust contracts-check check-android
check-rust:
	./scripts/with-env.sh cargo fmt --all --check
	./scripts/with-env.sh cargo clippy --locked --workspace --all-targets -- -D warnings
	./scripts/with-env.sh cargo test --locked --workspace
check-android:
	./scripts/android.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
test-device:
	@SERIAL=$$(./scripts/device.sh) && ANDROID_SERIAL="$$SERIAL" ./scripts/android.sh :app:connectedDebugAndroidTest
contracts:
	./scripts/contracts.sh
contracts-check:
	./scripts/contracts.sh --check
fmt:
	./scripts/with-env.sh cargo fmt --all
