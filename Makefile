.DEFAULT_GOAL := help
.PHONY: help setup doctor dev avd emulator devices android build check test-device

help:
	@printf '%s\n' 'Pandora' '' \
	  '  make setup          Install user-local Java and Android SDK tools' \
	  '  make doctor         Check toolchains, KVM, and connected devices' \
	  '  make dev            Start/use emulator, install and launch app' \
	  '  make avd            Create the Android virtual device' \
	  '  make emulator       Open the graphical Android emulator' \
	  '  make devices        List emulator/phone serials' \
	  '  make android        Build, install and launch on a device' \
	  '  make build          Build the debug APK' \
	  '  make check          Android unit tests, lint, and build' \
	  '  make test-device    Run Compose UI tests on the selected device'

setup:
	./scripts/setup-tools.sh
doctor:
	./scripts/doctor.sh
dev:
	./scripts/dev.sh
avd:
	./scripts/create-avd.sh
emulator:
	./scripts/emulator.sh
devices:
	./scripts/with-env.sh adb devices -l
android:
	./scripts/run-android.sh
build:
	./scripts/android.sh :app:assembleDebug
check:
	./scripts/android.sh :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
test-device:
	@SERIAL=$$(./scripts/device.sh) && ANDROID_SERIAL="$$SERIAL" ./scripts/android.sh :app:connectedDebugAndroidTest
