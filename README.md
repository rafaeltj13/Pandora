# Pandora

A minimal native Android app and Rust backend, organized like the Better-T-Stack
projects in this workspace. The first screen loads a welcome message from the
server and handles loading, connection failures, and Retry. A bottom navigation
bar switches between Home, Explore, Development, and Settings.

## What has been implemented

- An `apps/` and `packages/` monorepo with native Gradle and Cargo builds,
  coordinated by root Make commands.
- A Kotlin + Jetpack Compose Android app named **Pandora**, application ID
  `dev.rafaelm.pandora`, supporting Android 8.0/API 26 and newer.
- A Rust/Axum server with logging, configurable binding, graceful shutdown,
  health checks, and a versioned welcome endpoint.
- Rust-generated OpenAPI, plus a test that checks Android's response model
  against that contract. This is the shared boundary for a future web client.
- Typed Android routes, a bottom navigation bar, saved navigation state,
  Android Back handling, and a Home ViewModel retained across tab switches.
- User-local Rust, Java, Android SDK and emulator setup, with USB port
  forwarding for running the same debug app on a phone.
- Rust and Android automated checks, emulator UI tests, and a GitHub Actions
  workflow for builds and checks.

| App page | Current behavior |
| --- | --- |
| Home | Loads the Rust welcome message; shows loading, errors, Retry and Refresh. |
| Explore | Empty starter page for future features. |
| Development | Connects your GitHub account, groups open issues by selected personal repository, and creates issues with labels and assignees. |
| Settings | Opens Appearance to choose System, Light, or Dark; also shows the app name/version. |

## Run on this machine

The toolchains and `Pandora_API_36` virtual device have already been installed
on this workspace's Linux machine. From any terminal:

```sh
cd /home/rafaelm/Documents/code/pandora
make dev
```

This starts or reuses the local server, starts an emulator if no authorized
device is available, waits for Android to boot, builds and installs the APK,
sets up port forwarding, and opens Pandora. Use the bottom bar to switch pages.

Leave this terminal running while using the app. Ctrl-C stops only processes
started by this command; a previously running server or emulator stays open.
After changing code, stop and rerun `make dev`, or use `make android` if the
server and emulator are already running. Live reload is not configured.

If both an emulator and phone are connected, run `make devices` and select one:

```sh
env ANDROID_SERIAL=YOUR_DEVICE_SERIAL make dev
```

The debug APK is built at
`apps/android/app/build/outputs/apk/debug/app-debug.apk`. Logs for the server
and emulator started by `make dev` are in `.local/server.log` and
`.local/emulator.log`.

## Structure

```text
apps/
  android/             Kotlin + Compose app, Gradle wrapper and version catalog
  server/              Rust/Axum process, environment and shutdown
packages/
  api/                 Rust routes, response types and API tests
  contracts/           Generated OpenAPI contract for current/future clients
scripts/               Toolchain setup and local development commands
Cargo.toml             Rust workspace
Makefile               Root workflow
```

Android's UI/data separation is inside its app module. Reusable Android modules
can be extracted when needed. A future web frontend can live in `apps/web` and
consume `packages/contracts/openapi.json`; it need not share a runtime or UI
framework with Android.

Android routes are typed serializable objects in `ui/navigation/PandoraRoute.kt`.
`ui/PandoraApp.kt` owns the navigation graph, bottom bar, and screen insets;
screens receive action callbacks. Home retains its ViewModel while switching
tabs. Tab state is saved/restored, repeated taps do not duplicate destinations,
and Android Back returns from another tab to Home. Add new routes to the route
definitions and navigation graph as real features grow.

## Android design system

Pandora uses native Material 3 components. The light palette draws from a lynx
point cat: cream and ivory surfaces, taupe markings, brown-gray text, and blue
actions inspired by its eyes. Its dark palette adapts those color families to
warm charcoal surfaces and lighter foregrounds. Otto is a black-and-white cat;
his light and dark palettes use neutral white, black, and gray tones.

`ui/PandoraTheme.kt` owns `PandoraLightColors`, `PandoraDarkColors`,
`OttoLightColors`, and `OttoDarkColors`, along with the system-font typography
scale, Material shapes, and `PandoraSpacing`. Edit each palette independently. Screens use semantic roles
such as `MaterialTheme.colorScheme.primary`, `surface`, `onSurface`, and
`onSurfaceVariant`; prefer these over literal colors. Use `MaterialTheme`
typography/shapes and `PandoraSpacing` for consistent components and layouts.
`PandoraPage` provides the shared scrollable layout for information pages.
Window startup backgrounds live in `res/values/themes.xml` and
`res/values-night/themes.xml`; keep these aligned with the base surfaces.

**Settings → Appearance** offers System, Light, and Dark. The default is System;
explicit selections override the device setting. Visual phone cards show the
color schemes, and paired palette previews let you select a different cat theme
for light and dark mode. Tap a theme card to apply it to both. Appearance opens
with a native back toolbar and hides bottom navigation, following the T3 Code
reference. A text sample follows Android’s font-size setting; larger text and
narrow windows switch the theme controls to a vertical layout.

`AppearanceRepository` stores mode and both theme choices locally, and
`AppearanceViewModel` exposes them to the UI. System
changes update the resolved theme, and system-bar icons follow that theme.
Wallpaper-derived colors are disabled so the app keeps its chosen palette.
App/version information remains in Settings under About Pandora.

Welcome and Appearance include Compose previews. Palette tests check text and
control contrast for all four palettes, while device tests cover independent
theme selection, persistence, system-mode changes, larger text, and navigation
restoration.

## Local setup

For a fresh Linux x86_64 machine, run the following from the repository root.
The bootstrap expects Bash, Make, a C linker/compiler, curl, tar, unzip and
Python 3. A usable `/dev/kvm` is needed for an accelerated emulator, along
with the host's graphics/display libraries for its window.

This bootstrap targets Linux x86_64. It installs Rust to `~/.cargo` and
`~/.rustup`, Temurin JDK 21 to `~/.local/share/pandora/jdk-21`, and the Android
SDK to `~/Android/Sdk`. It needs no sudo and does not change shell startup
files. Downloaded JDK/command-line tools are checksum verified. SDK licenses
are shown for acceptance during installation.

```sh
make setup
make doctor
make avd
make dev
```

Toolchain downloads, SDKs, AVDs, local config, build outputs and logs are kept
outside version control. Android Studio is optional; this setup uses the
standalone SDK and a graphical emulator.

To manage the processes separately, run the backend in one terminal:

```sh
make server
```

Open the emulator in a second terminal:

```sh
make emulator
```

After it boots, build, install and open the app from a third terminal:

```sh
make android
```

The emulator is `Pandora_API_36`, based on a Pixel 5 with Android 16/API 36,
2 GB RAM, and two CPU cores. `make doctor` checks KVM hardware acceleration.
If host GPU rendering has trouble, run `./scripts/emulator.sh -gpu software`.

The scripts respect existing `JAVA_HOME` and `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
For tools outside Make, use `./scripts/with-env.sh <command>`; for example,
`./scripts/with-env.sh cargo test --workspace`.

## Your phone

1. Enable Developer options and USB debugging on your Android phone.
2. Connect it with a data-capable USB cable and accept the debugging prompt.
3. Run `make devices`. The device must show `device`, rather than `unauthorized`.
4. Keep `make server` running and run `make android`.

When the emulator and phone are both connected, select a serial explicitly:

```sh
env ANDROID_SERIAL=YOUR_PHONE_SERIAL make android
```

`make android` applies `adb reverse tcp:8080 tcp:8080` to the selected device,
then installs and opens the debug APK. Both phone and emulator therefore use
the same `http://localhost:8080` API URL. Repeat this command after reconnecting
the phone. The phone supports Android 8.0/API 26 or newer. You can also use
`env ANDROID_SERIAL=YOUR_PHONE_SERIAL make dev` to manage the backend and app
from one terminal.

If ADB reports insufficient USB permissions on Arch/CachyOS, the host needs
the distribution's Android udev rules (typically `android-udev`), followed by
reconnecting the phone. This is a system package; the bootstrap does not use
sudo or modify system permissions.

## API and checks

Run `make help` to see the available commands. The main development commands
are:

| Command | Purpose |
| --- | --- |
| `make setup` | Install user-local toolchains and Android SDK packages. |
| `make doctor` | Check tools, KVM acceleration and connected devices. |
| `make avd` | Create the virtual device if it does not already exist. |
| `make dev` | Start the local stack and open Pandora on a selected device. |
| `make server` | Run the Rust backend. |
| `make emulator` | Open the graphical emulator. |
| `make devices` | List device serials and authorization states. |
| `make android` | Rebuild, install and launch the app; requires a running backend. |
| `make build` | Build the Rust workspace and debug APK without launching either. |
| `make check` | Run all host-side checks and build the debug APK. |
| `make test-device` | Run UI/navigation tests on a connected, booted device. |
| `make contracts` | Regenerate the shared OpenAPI document after an API change. |
| `make contracts-check` | Verify the generated OpenAPI document is current. |
| `make fmt` | Format Rust source. |

```sh
make check
make test-device
make contracts
```

`make check` runs Rust formatting, Clippy, API tests, generated-contract
consistency, Android JVM tests, Android lint, and a debug APK build.
`make test-device` runs Compose screen tests on the selected connected device.
The GitHub Actions workflow runs `make check` on pushes and pull requests;
device tests remain a local check in this starter.

Local verification completed on **2026-10-09**:

- `make check` passed: Rust formatting/Clippy, three Rust tests, eight Android
  JVM tests, generated-contract checks, Android lint and the debug APK build.
- `make test-device` passed all five emulator UI tests, including switching
  pages, repeated tab taps, Back navigation and selected-page restoration.
- The live Android app loaded the Rust response, displayed an error when the
  connection was removed, and recovered through Retry after reconnection.
- Switching pages and returning Home preserved the loaded response.

**Physical-phone verification is still pending.** The USB workflow is prepared;
the initial verification used the emulator. CI is configured in
[`.github/workflows/check.yml`](.github/workflows/check.yml).

The default server address is `127.0.0.1:8080`. Routes:

| Route | Purpose |
| --- | --- |
| `GET /health` | Process health |
| `GET /api/v1/status` | Typed welcome response |
| `GET /openapi.json` | Generated client contract |

With the backend running, verify it from a terminal:

```sh
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/api/v1/status
```

The health response is `{"status":"ok"}`. The welcome response contains
`app`, `message` and `version`. Rust owns its schema; regenerate the contract
with `make contracts` after changing API routes or response types.

`PANDORA_BIND` changes the standalone server bind address; `RUST_LOG` controls
logging. To run the complete local flow on another port:

```sh
env PANDORA_PORT=8081 make dev
```

Android permits local cleartext HTTP only in debug builds. Release variants
are disabled until an explicit HTTPS endpoint is supplied:

```sh
./scripts/android.sh :app:assembleRelease -Ppandora.releaseApiUrl=https://YOUR_API_HOST
```

Release signing, hosting, and web UI are future work. The Android GitHub feature
connects directly to one configured personal account; there are no general Pandora user accounts.

## Development tab and GitHub setup

Development connects **directly from Android to GitHub over HTTPS**. It needs
no hosted Pandora API, client secret, callback endpoint, or server token store.
Home still uses the existing Pandora welcome API independently.

The dashboard shows your GitHub avatar, name, username, profile link and
Disconnect. Choose personal repositories from a searchable list; Pandora
remembers that selection per account. Private repositories are included when
accessible to the installed GitHub App. Organization repositories, archived
repositories and repositories with issues disabled are excluded.

Each repository shows open issues from all authors, newest first, ten at a
time with **Load more**. Pull requests are excluded. Tap an issue to open
GitHub, or **New issue** to enter a title, optional description, and optional
labels and assignees loaded from that repository.

Leaving the form immediately discards its contents. No drafts, issue caches,
offline queue, or automatic submission retries are saved. Failed submissions
keep the open form. Uncertain outcomes ask you to check GitHub before retrying.
Leaving a form after submission starts does not undo the request. Offline, the
dashboard displays an empty state with Retry and does not offer issue creation.
Successful submission stays in Pandora with the issue number and **Open on GitHub**.

### GitHub setup (after development)

The app builds and the other tabs work before GitHub is configured. Development
shows **GitHub setup is pending**, with Connect disabled until configuration is
supplied. No GitHub requests are made in that state.

1. Register a **GitHub App** under your personal account. Enable **device flow**
   in its settings and leave expiring user tokens enabled. Disable webhooks;
   this app does not use webhooks or an OAuth callback URL.
2. Request repository **Issues: Read and write** plus the automatically required
   **Metadata: Read-only** permission. No Contents/code permission is needed.
3. Limit installation to your account and install it on selected personal
   repositories. Note the **client ID**, app **slug**, and your GitHub **username**.
   A client secret or app private key is not required by Pandora.
4. Supply these public identifiers as Gradle properties and rebuild:

```sh
./scripts/android.sh :app:assembleDebug \
  -Ppandora.githubClientId=YOUR_CLIENT_ID \
  -Ppandora.githubAppSlug=YOUR_APP_SLUG \
  -Ppandora.githubLogin=YOUR_GITHUB_USERNAME
```

For `make dev` / `make android`, set those properties in your user-local
`~/.gradle/gradle.properties` so subsequent builds keep the same configuration.
Do not commit local configuration or tokens. Existing release requirements
(explicit HTTPS welcome API URL and separate signing) still apply; that API is
independent of the GitHub feature.

5. Open Development and tap **Connect GitHub**. Pandora displays a GitHub device
   code. Copy it or remember it, tap **Open GitHub**, enter the code and approve
   access in your browser, then return to Pandora. It finishes connecting
   automatically. You can reopen GitHub while the authorization is pending.
6. Select dashboard repositories. Use **Manage GitHub App access**, followed by
   Refresh, to change the repositories available to the app.

Pandora respects GitHub's polling interval, increases it on `slow_down`, and
handles declined or expired authorization. A temporarily interrupted login can
be resumed with **Retry connection** while the app remains running. Unfinished
authorization is not persisted across process death; start Connect again.

Access and refresh tokens are encrypted together with AES-GCM and an Android
Keystore key in app-private preferences; Android backups are disabled. Tokens
are refreshed directly on Android without a client secret. The configured
GitHub account is checked before credentials are saved. Changing the configured
client ID or username requires connecting again. Disconnect deletes local
credentials even when offline; it does not uninstall or revoke the GitHub App.
Use GitHub settings to revoke its authorization or installation.

[GitHub device authorization](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app#using-the-device-flow-to-generate-a-user-access-token)
and [token refresh](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/refreshing-user-access-tokens)
document the provider endpoints. The Rust OpenAPI describes only Pandora's own
API; GitHub's API is consumed by the Android repository.

Live GitHub authorization and issue creation remain pending until the App is
registered and configured. Automated tests use simulated GitHub responses;
physical-phone verification is separate from emulator checks.

## Toolchain choices

AGP 9.4.0, Gradle 9.6.0, Kotlin/Compose compiler 2.4.10, and the June 2026
Compose BOM are pinned in the Android project. Compile/target SDK is 36;
newer Compose releases that require a preview SDK are intentionally avoided.
Navigation Compose 2.9.8 and Coil 3.3.0 are pinned for compatibility with this SDK. Rust uses
the stable toolchain; `Cargo.lock` pins dependencies. Android dependency
versions live in `apps/android/gradle/libs.versions.toml`.

Primary references: [Android architecture](https://developer.android.com/topic/architecture),
[AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom),
[ADB forwarding](https://developer.android.com/develop/ui/views/layout/webapps/access-local-server),
[Axum](https://docs.rs/axum/latest/axum/).
