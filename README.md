# Pandora

A native Android app that brings your productivity services into one place.
Pandora has no backend of its own: each module talks directly to the service it
connects to (Google Calendar through Android, GitHub over HTTPS). A floating
dock switches between Home and the modules turned on in Settings.

## What has been implemented

- A Kotlin + Jetpack Compose Android app named **Pandora**, application ID
  `dev.rafaelm.pandora`, supporting Android 8.0/API 26 and newer, built with
  the committed Gradle wrapper and coordinated by root Make commands.
- Typed Android routes, a floating dock, saved navigation state, and Android
  Back handling.
- User-local Java, Android SDK and emulator setup, with USB install for running
  the same debug app on a phone.
- Android automated checks, emulator UI tests, and a GitHub Actions workflow
  for builds and checks.

| App page | Current behavior |
| --- | --- |
| Home | Shows the Pandora welcome card; points to Settings when every module is off. |
| Tasks | Creates tasks (title, date, time and duration or all day, optional description, optional repeat) as tagged Google Calendar events, so they live in the calendar and survive reinstalls. Edits to repeating tasks apply from the next occurrence on. Also lists the next 14+ days of events from your Google calendars, read-only, with a Tasks/Everything filter. |
| Development | Connects your GitHub account, groups open issues by selected personal repository, and creates issues with labels and assignees. |
| Settings | Opens Appearance to choose System, Light, or Dark; chooses the default calendar for tasks and which calendars appear in Tasks; shows the app name/version. |

## Run on this machine

The toolchains and `Pandora_API_36` virtual device have already been installed
on this workspace's Linux machine. From any terminal:

```sh
cd /home/rafaelm/Documents/code/pandora
make dev
```

This starts an emulator if no authorized device is available, waits for
Android to boot, builds and installs the APK, and opens Pandora.

Leave this terminal running while using the app. Ctrl-C stops an emulator
started by this command; a previously running emulator stays open. After
changing code, run `make android` to reinstall. Live reload is not configured.

If both an emulator and phone are connected, run `make devices` and select one:

```sh
env ANDROID_SERIAL=YOUR_DEVICE_SERIAL make dev
```

The debug APK is built at
`apps/android/app/build/outputs/apk/debug/app-debug.apk`. The log for an
emulator started by `make dev` is `.local/emulator.log`.

## Structure

```text
apps/
  android/             Kotlin + Compose app, Gradle wrapper and version catalog
scripts/               Toolchain setup and local development commands
Makefile               Root workflow
```

Android's UI/data separation is inside its app module. Reusable Android modules
can be extracted when needed.

Android routes are typed serializable objects in `ui/navigation/PandoraRoute.kt`.
`ui/PandoraApp.kt` owns the navigation graph, dock, and screen insets;
screens receive action callbacks. Tab state is saved/restored, repeated taps do not duplicate destinations,
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
The bootstrap expects Bash, Make, curl, tar and unzip. A usable `/dev/kvm` is needed for an accelerated emulator, along
with the host's graphics/display libraries for its window.

This bootstrap targets Linux x86_64. It installs Temurin JDK 21 to `~/.local/share/pandora/jdk-21`, and the Android
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

To manage the emulator separately, open it in one terminal:

```sh
make emulator
```

After it boots, build, install and open the app from another terminal:

```sh
make android
```

The emulator is `Pandora_API_36`, based on a Pixel 5 with Android 16/API 36,
2 GB RAM, and two CPU cores. `make doctor` checks KVM hardware acceleration.
If host GPU rendering has trouble, run `./scripts/emulator.sh -gpu software`.

The scripts respect existing `JAVA_HOME` and `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
For tools outside Make, use `./scripts/with-env.sh <command>`; for example,
`./scripts/with-env.sh adb devices`.

## Your phone

1. Enable Developer options and USB debugging on your Android phone.
2. Connect it with a data-capable USB cable and accept the debugging prompt.
3. Run `make devices`. The device must show `device`, rather than `unauthorized`.
4. Run `make android`.

When the emulator and phone are both connected, select a serial explicitly:

```sh
env ANDROID_SERIAL=YOUR_PHONE_SERIAL make android
```

`make android` installs and opens the debug APK on the selected device. The
phone supports Android 8.0/API 26 or newer.

If ADB reports insufficient USB permissions on Arch/CachyOS, the host needs
the distribution's Android udev rules (typically `android-udev`), followed by
reconnecting the phone. This is a system package; the bootstrap does not use
sudo or modify system permissions.

## Checks

Run `make help` to see the available commands. The main development commands
are:

| Command | Purpose |
| --- | --- |
| `make setup` | Install user-local toolchains and Android SDK packages. |
| `make doctor` | Check tools, KVM acceleration and connected devices. |
| `make avd` | Create the virtual device if it does not already exist. |
| `make dev` | Start/use an emulator and open Pandora on a selected device. |
| `make emulator` | Open the graphical emulator. |
| `make devices` | List device serials and authorization states. |
| `make android` | Rebuild, install and launch the app. |
| `make build` | Build the debug APK without launching it. |
| `make check` | Run Android JVM tests and lint, and build the debug APK. |
| `make test-device` | Run UI/navigation tests on a connected, booted device. |

`make test-device` runs Compose screen tests on the selected connected device.
The GitHub Actions workflow runs `make check` on pushes and pull requests;
device tests remain a local check.

**Physical-phone verification:** the Tasks tab was checked over USB on a
Motorola edge 60 pro (Android 16) with a real Google account: tasks and their
events appeared in Google Calendar. CI is configured in
[`.github/workflows/check.yml`](.github/workflows/check.yml).

Release signing and a web UI are future work. The Android GitHub feature
connects directly to one configured personal account; there are no Pandora
user accounts.

## Development tab and GitHub setup

Development connects **directly from Android to GitHub over HTTPS**. It needs
no hosted Pandora API, client secret, callback endpoint, or server token store.

The dashboard shows your GitHub avatar, name, username, profile link and
Disconnect. Choose personal repositories from a searchable list; Pandora
remembers that selection per account. Private repositories are included when
accessible to the installed GitHub App. Organization repositories, archived
repositories and repositories with issues disabled are excluded.

Each repository shows open issues from all authors, newest first, ten at a
time with **Load more**. Pull requests are excluded. Tap an issue to open
GitHub, or **New issue** to enter a title, optional description, and optional
labels and assignees loaded from that repository. **Add image** opens Android's
photo picker. PNG, JPEG, GIF and WebP images up to 10 MB upload directly to
GitHub and appear as thumbnails. Remove a thumbnail to leave it out of the
issue. Images upload when selected; removing them or discarding the form does
not delete the uploaded asset from GitHub. Submission waits for uploads, and
retrying issue creation reuses successful uploads. Thumbnails use the selected
local image while the form is open. The Create issue action stays below the
scrolling form and above the keyboard. Labels and assignees have separate
headlines and selection summaries.

The Development dashboard keeps account actions behind the account row and
repository selection beside the repository heading. Creation feedback can be
dismissed without leaving the dashboard.

Uploads use the same endpoint as [GitHub CLI attachments](https://docs.github.com/en/github-cli/github-cli/attaching-files-with-github-cli).
They require write access to the repository and a supported credential. GitHub
[restricts App-token uploads to privileged first-party Apps](https://github.com/cli/cli/issues/14495#issuecomment-6159411769).
Pandora uses a separate, optional fine-grained personal access token for uploads.
The GitHub App connection still handles sign-in, repositories and issue creation.
Rejected uploads keep the draft.

To enable gallery uploads:

1. Connect GitHub, then open **Settings → Development → Attachment token**, or
   tap **Set up image uploads** in the issue form.
2. Tap **Create token on GitHub**. Choose your account as resource owner and
   **Only select repositories**. Select the repositories where you need images.
3. Give **Issues: Read and write** access and set an expiration date. The setup
   link pre-fills Issues write access and a 30-day lifetime.
4. Generate the token, copy it, return to Pandora and paste it into the masked
   field. Tap **Save token**, then **Done**. Saving stores the token locally;
   GitHub checks its access on the next upload.

Once a token is saved, the dialog shows **Token saved**. Choose **Replace token**
to enter another one, or **Remove token** to delete it from the device.

The attachment token is encrypted with Android Keystore, bound to the configured
GitHub account and client ID, and sent only to GitHub's upload endpoint. It is
never used for other API calls. Token entry is excluded from saved instance state
and screenshots. **Remove token** removes it from the device without disconnecting
the GitHub App or revoking it on GitHub. Disconnecting GitHub removes both saved
credentials. Replace an expired token through the same setting.

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
Do not commit local configuration or tokens.

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
document the provider endpoints. GitHub's API is consumed by the Android
repository.

Live GitHub authorization and issue creation remain pending until the App is
registered and configured. Automated tests use simulated GitHub responses;
physical-phone verification is separate from emulator checks.

## Toolchain choices

AGP 9.4.0, Gradle 9.6.0, Kotlin/Compose compiler 2.4.10, and the June 2026
Compose BOM are pinned in the Android project. Compile/target SDK is 36;
newer Compose releases that require a preview SDK are intentionally avoided.
Navigation Compose 2.9.8 and Coil 3.3.0 are pinned for compatibility with this SDK.
Android dependency versions live in `apps/android/gradle/libs.versions.toml`.

Primary references: [Android architecture](https://developer.android.com/topic/architecture),
[AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom).
