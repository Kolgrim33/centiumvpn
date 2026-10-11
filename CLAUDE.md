# Centium VPN

Routes all device traffic through Tor. Two independent apps live here:

- **Android app (`android/`)**: native Kotlin, the main focus. A `VpnService` TUN is bridged to Tor's SOCKS port.
- **Linux desktop (repo root)**: a React/Vite UI plus a Node daemon (`server.ts`, `server/`) driving system Tor, nftables and `linux/centium-network.sh`. `src-tauri/` is an unused stub.

## Android architecture

Connect flow (`vpn/CentiumVpnService.kt`):
1. `tor/TorManager` binds tor-android's in-process `TorService`.
   - Tor picks its SOCKS port (`SocksPort auto`).
   - Control goes over TorService's Unix socket via jtorctl; there are no fixed ports.
2. The service waits for bootstrap to reach 100%.
3. `vpn/TunConfiguration` builds the TUN.
   - It captures all IPv4 and IPv6.
   - The app's own package is excluded, so Tor's relay traffic doesn't loop back into the tunnel.
4. `bridge/HevTunnelBridge` starts hev-socks5-tunnel on the TUN fd, pointed at Tor's SOCKS port.
   - DNS goes to hev's mapdns (198.18.0.2), which returns fake 100.64/10 IPs, so hostnames resolve at the Tor exit.
   - There is no Tor DNSPort.
5. `diagnostics/VerificationMatrix` checks the exit through Tor's SOCKS port (the app itself bypasses the VPN).
6. A watchdog polls traffic stats, refreshes the circuit and enforces the kill switch.

Other notes:
- **UI state:** state flows live in the service's companion object, so the UI can observe them whether or not the service is running.
- **Native bridge:** hev-socks5-tunnel is a git submodule pinned to 2.18.0. Its JNI registers natives on `hev.htproxy.TProxyService` (`java/hev/htproxy/TProxyService.kt`), so don't rename or move that class. After cloning, run `git submodule update --init --recursive`.
- **Bridges:** pluggable transports (obfs4, Snowflake) are not bundled. `TorrcGenerator` rejects them, and only plain `IP:port` bridges work.

## Building (Android)

- **Toolchain:** AGP 9.4.1, Gradle 9.7.1 and compileSdk 37.1. The build needs JDK 21; the system Java 27 is too new.
- **Why so new:** tor-android 0.4.9.x requires compileSdk 37.
- **Command:**
  ```
  cd android && JAVA_HOME=~/Android/jdk-21 ./gradlew assembleDebug --max-workers=2
  ```
  - Keep `--max-workers=2`. A full parallel build, which includes NDK builds for 4 ABIs, has OOM-killed this machine.
  - The SDK is at `~/Android/Sdk`, set in `android/local.properties` (gitignored).
  - If ndk-build reports a stale path, delete `android/app/.cxx` and `android/app/build/.cxx` and `android/app/build/intermediates/cxx`.
- **Flavors:** `github` (self-updater on) and `fdroid` (updater off, and `src/fdroid/AndroidManifest.xml` strips `REQUEST_INSTALL_PACKAGES` and the update receivers). Build one with e.g. `assembleGithubDebug` or `assembleFdroidRelease`. A bare `assembleDebug` builds both.
- **Output:** `android/app/build/outputs/apk/<flavor>/<type>/`. Debug builds use the package `org.centium.vpn.debug` and release builds `org.centium.vpn`.

## Releasing and in-app updates

- **Ship a release:**
  1. Bump `centium.versionName` in `android/gradle.properties`.
  2. Add a `## x.y.z` section to `CHANGELOG.md`, written for users.
  3. Commit and push `main`, then `git tag vx.y.z && git push origin vx.y.z`.
- **What CI does:** `.github/workflows/android-release.yml`
  1. checks the tag matches the version;
  2. builds and signs with the key in repo secrets (`CENTIUM_KEYSTORE_B64`, `CENTIUM_KEYSTORE_PASSWORD`, `CENTIUM_KEY_ALIAS`, `CENTIUM_KEY_PASSWORD`);
  3. checks the signer against the `CENTIUM_SIGNER_SHA256` repo variable;
  4. publishes `centium-x.y.z.apk` and a `.sha256`, using the changelog section as the notes.
- **versionCode** is derived as `major*10000 + minor*100 + patch` and must only ever increase. Never reuse or lower a version.
- **Release key:** `~/Android/centium-release/` (keystore + `signing.properties`), referenced from `android/local.properties` as `centium.signing`. If it's lost, every user must uninstall and reinstall. Never commit it.
- **App side (`update/`):** `UpdateManager` checks GitHub `releases/latest` at most every 20h, after the VPN connects and only through Tor's SOCKS port. "Check now" in Settings goes direct when disconnected.
  - **Verification:** `UpdateVerifier` requires a matching SHA-256, the same package, a higher versionCode and the same signing cert before `UpdateInstaller` (PackageInstaller session) runs.
  - **After install:** `PackageReplacedReceiver` reconnects if the VPN was on, and `MainActivity` shows "What's new" once.
  - **Debug builds:** `BuildConfig.UPDATES_ENABLED` is false (different package and key).

## F-Droid

- **Licensing:** the app is GPL-3.0 (`LICENSE`).
- **Store listing:** `fastlane/metadata/android/en-US/`. For each release, add `changelogs/<versionCode>.txt` (at most 500 chars) with the same text as the CHANGELOG section.
- **Build recipe:** `docs/fdroid/org.centium.vpn.yml` is the fdroiddata recipe (fdroid flavor, `submodules: true`, ndk r26d). After the first acceptance, F-Droid picks up new `vX.Y.Z` tags automatically (`UpdateCheckMode: Tags`). Submission steps are in `docs/fdroid/SUBMITTING.md`.
- **Signing:** F-Droid signs its own builds, so GitHub and F-Droid installs can't update each other.
- **Policy:** don't add non-free dependencies or network services. The only services contacted are check.torproject.org and onionoo.torproject.org (both through Tor), plus the GitHub API in the github flavor.

## Testing on the emulator

- **Boot:** the AVD is `centium_test` (API 34). Start it with:
  ```
  ANDROID_AVD_HOME=~/.config/.android/avd ~/Android/Sdk/emulator/emulator -avd centium_test -no-window -gpu swiftshader_indirect
  ```
- **Skip the VPN consent dialog:** `adb shell appops set org.centium.vpn.debug ACTIVATE_VPN allow`.
- **Check the exit IP through the tunnel:** toybox `nc` quits as soon as its input ends, so keep stdin open:
  ```
  adb shell "(printf 'GET / HTTP/1.0\r\nHost: icanhazip.com\r\n\r\n'; sleep 25) | toybox nc -w 30 icanhazip.com 80"
  ```
  Then confirm the IP is a Tor exit at `onionoo.torproject.org/details?search=<ip>`.
- **ANR dialogs:** "System UI isn't responding" dialogs come from the slow emulator, not the app.
- **Broken network after boot:** the emulator sometimes boots with no IPv4 ("connect: Network is unreachable"), and then Tor times out at 0%. Cold boot it with `-no-snapshot-load`.
- **SELinux noise:** `avc: denied { ioctl }` lines from `tor` are harmless.

## UI design system (Android)

The concept is a transit map of the Tor route. On the Connect screen, `ui/RouteLineView` draws phone → guard → middle → exit → internet, and the line builds hop by hop during bootstrap. That's the app's single animated moment. Keep new UI consistent with it:

- **Mark:** `drawable/ic_centium_mark.xml` is three nested arcs forming a "C", with one dot per relay. Don't reintroduce a shield.
- **Colour:** tokens are in `values{,-night}/colors.xml` (mist, paper, rule, ink, slate, faint, route, danger, caution, ok). Use the tokens and never hardcode hex.
- **Type:** Overpass, bundled as static TTFs. Use the `TextAppearance.Centium.*` scale: Display 34, Heading 24, Title 18, Body 15, Small 13.
  - Never set `android:fontFamily` on the theme. A theme-level font overrides every `textAppearance`.
  - Point styles at a weight file (`@font/overpass_semibold`); `textFontWeight` matching doesn't work.
- **Lists:** use grouped rows (the `Centium.Group`, `Centium.Row` and `Centium.Divider` styles in `values/styles.xml`), not a separate card per item.
- **Copy:** sentence case, no ALL-CAPS labels, plain language from the user's point of view ("Block traffic if Tor drops", not "Fail-Closed Kill Switch"). User-facing strings go in `values/strings.xml`.

## Conventions

- **Git:** work on a branch, then merge to `main`. The submodule is a gitlink, so commit submodule bumps deliberately.
- **`package-lock.json`:** it often carries unrelated npm churn. Don't commit it with Android work.
