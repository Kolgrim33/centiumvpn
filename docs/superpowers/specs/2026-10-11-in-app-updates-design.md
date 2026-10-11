# In-app updates for Centium Android

## Goal
Users stop sideloading new APKs by hand. Centium finds updates, downloads and verifies them in the background, and installs with one tap. After the restart it shows "What's new".

## Decisions (agreed with owner, 2026-10-11)
- **Audience:** the app will be shared with other people, so updates must be tamper-proof and private.
- **Builds:** GitHub Actions builds and signs on a `v*` tag push. The key lives in repo secrets, with an offline backup kept by the owner.
- **Flow:** the app checks about once a day and downloads automatically. The user taps once to install, and "What's new" shows after the update.
- **Feed:** GitHub Releases on `Kolgrim33/centiumvpn`. There's no separately signed manifest; the APK signature check covers that threat.

## Release pipeline
- **Version source:** the version is set in one place, `centium.versionName` in `android/gradle.properties`. The versionCode is derived as `major*10000 + minor*100 + patch`.
- **Changelog:** `CHANGELOG.md` has one `## x.y.z` section per release, and that section becomes the release notes.
- **Workflow:** `.github/workflows/release.yml` runs on a tag push and:
  1. checks that the tag matches `centium.versionName`;
  2. installs SDK platform 37.1 and NDK 26.3;
  3. decodes the keystore from secrets and runs `assembleRelease`;
  4. publishes `centium-x.y.z.apk` and `centium-x.y.z.apk.sha256`, with the changelog section as the notes.
- **Signing:** `signingConfigs.release` reads `CENTIUM_KEYSTORE`, `CENTIUM_KEYSTORE_PASSWORD`, `CENTIUM_KEY_ALIAS` and `CENTIUM_KEY_PASSWORD`. They come from environment variables in CI, or locally from the properties file named by `centium.signing` in `local.properties`. Without them, release builds are unsigned.

## App components (`org.centium.vpn.update`)
- **`Version`:** parses and compares `x.y.z` and maps it to a versionCode. Pure logic, unit-tested.
- **`ReleaseFeed`:**
  - Fetches `releases/latest` from the GitHub API and parses the tag, notes and asset URLs. The parser is pure and unit-tested.
  - Requests go through Tor's SOCKS port when the VPN is connected.
  - Automatic checks run only over Tor, so they never expose a user's IP. A manual "Check now" while disconnected goes direct, and the screen says so.
- **`UpdateVerifier`:** an APK is accepted only if all of these hold, and is otherwise deleted with the reason logged:
  - its SHA-256 matches the published `.sha256`;
  - its package name is the app's own;
  - its versionCode is higher than the installed one;
  - its signing certificate equals the running app's.
- **`UpdateInstaller`:**
  - Uses a PackageInstaller session; on API 31+ it requests `USER_ACTION_NOT_REQUIRED`. `REQUEST_INSTALL_PACKAGES` is added to the manifest.
  - A status receiver launches the system confirmation when Android requires it.
- **`UpdateManager`** (owned by `CentiumApplication`):
  - Exposes a `StateFlow`: Idle, Checking, Downloading, Ready(release), UpToDate, Failed(reason).
  - Runs an automatic check after the VPN reaches CONNECTED, if more than 20h have passed since the last check and auto-updates are on.
  - Posts a notification when an update is ready.
  - Before installing, it caches the notes and records whether the VPN was connected.
- **`PackageReplacedReceiver`** (`MY_PACKAGE_REPLACED`): if the VPN was connected before the update, it reconnects.

## UI (existing route design language)
- **Connect screen:** when an update is ready, a slim banner appears at the top: "Version x.y.z is ready" with an Install button.
- **Settings "Updates" group:** a "Check for updates automatically" switch, and a row showing the current version with a "Check now" button and its status.
- **"What's new" dialog:** shown once on the first launch where the versionCode is above the last one seen. It displays the cached notes in plain text. A fresh install records the version without showing the dialog.

## Error handling
- **Network failures:** they leave the state Idle or Failed with a plain message, and the next connection retries.
- **Verification failures:** the file is never installed, and the cause appears in Logs.
- **Install failures:** they surface as Failed with Android's status message.

## Testing
- **Unit tests:** `Version`, release parsing (assets, notes, prerelease skipped), checksum parsing.
- **Emulator run:** install a locally release-signed 1.0.9, publish v1.1.0, and confirm the app finds, verifies and installs it, shows "What's new", and reconnects.

## One-time migration
Installed copies signed with the debug key can't update to release-signed builds, so the owner's phone needs one manual reinstall of v1.1.0.
