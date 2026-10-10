# Centium VPN — Android Implementation & Build Guide

Centium VPN for Android is a **native Android VPN application** that transparently routes device network traffic through the **Tor network** using Android's official `VpnService` framework, native `hev-socks5-tunnel` packet bridging, and local Tor DNS interception without requiring device root, paid proxies, or third-party relay infrastructure.

---

## 1. Architectural Model

```text
Android Applications
        │
        │ (TCP & Port 53 DNS)
        ▼
Android VpnService TUN Interface (198.18.0.1/15)
        │
        │ (ParcelFileDescriptor fd)
        ▼
Native lwIP TUN-to-SOCKS5 Bridge (hev-socks5-tunnel / TProxyService)
        │
        │ SOCKS5 Connect (127.0.0.1:9050)
        ▼
Local Tor Runtime (Application Sandbox)
        ├── SOCKS5 Proxy  (:9050)
        ├── ControlPort   (:9051)
        └── Tor DNSPort   (:9053)
        │
        │ VpnService.protect() [Bypasses TUN to prevent routing loops]
        ▼
Encrypted Tor 3-Hop Circuits (Guard ➔ Middle ➔ Exit)
        │
        ▼
Internet / Destination Servers
```

---

## 2. Directory Structure

```text
android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/
        │   ├── hev/htproxy/
        │   │   └── TProxyService.kt          # Native JNI binding for hev-socks5-tunnel
        │   └── org/centium/vpn/
        │       ├── CentiumApplication.kt     # App initialization & preferences
        │       ├── data/                     # Domain & telemetry data models
        │       │   ├── CentiumConfig.kt      # Bridges, killswitch, timeout, exit location
        │       │   ├── ConnectionState.kt    # 8-phase connection state machine
        │       │   ├── CircuitNode.kt        # Guard, Middle, Exit circuit hops
        │       │   ├── DiagnosticResult.kt   # 10-layer diagnostic report model
        │       │   └── PreferencesRepository.kt
        │       ├── tor/                      # Tor process & control protocol
        │       │   ├── TorManager.kt         # Sandbox process execution & port checks
        │       │   ├── TorrcGenerator.kt     # Dynamic torrc with DNSPort & bridges
        │       │   ├── TorController.kt      # ControlPort (SIGNAL NEWNYM, circuits)
        │       │   └── TorBootstrapMonitor.kt # Consensus progress regex parser
        │       ├── bridge/                   # Native TUN bridge integration
        │       │   ├── HevTunnelBridge.kt    # Manages TProxyService & traffic stats
        │       │   └── NativeBridgeLoader.kt # Validates libhev-socks5-tunnel.so
        │       ├── dns/                      # Zero-leak DNS subsystem
        │       │   ├── TorDnsResolver.kt     # UDP forwarder to Tor DNSPort (:9053)
        │       │   └── DnsPacketHandler.kt   # Transaction ID & query validator
        │       ├── security/                 # Security hardening
        │       │   ├── FailClosedGuard.kt    # In-app fail-closed drop engine
        │       │   └── LockdownDetector.kt   # Android Always-on VPN detector
        │       ├── vpn/                      # Native Android VpnService
        │       │   ├── CentiumVpnService.kt  # Core Foreground VPN Service
        │       │   ├── TunConfiguration.kt  # IPv4/IPv6 virtual routing builder
        │       │   └── VpnLifecycleManager.kt # VpnService.prepare() coordinator
        │       ├── diagnostics/              # Verification matrix
        │       │   ├── DiagnosticSuite.kt    # 10-layer test runner
        │       │   └── VerificationMatrix.kt # Direct unproxied exit-IP check
        │       ├── ui/                       # Clean Native Material3 UI
        │       │   ├── MainActivity.kt       # Primary activity & permission flow
        │       │   ├── ConnectionFragment.kt # Power button, gauge & circuit view
        │       │   ├── SettingsFragment.kt   # Bridges, killswitch & exit node
        │       │   ├── DiagnosticsFragment.kt # Live 10-layer audit runner UI
        │       │   └── LogsFragment.kt       # Diagnostic log terminal
        │       └── notifications/
        │           ├── VpnNotificationManager.kt # Persistent status notification
        │           └── CentiumTileService.kt # Android Quick Settings toggle tile
        └── jni/
            ├── Android.mk
            ├── Application.mk
            └── hev-socks5-tunnel-jni.c       # JNI C glue for hev-socks5-tunnel
```

---

## 3. Prerequisites & Environment Setup

- **Java Development Kit (JDK)**: OpenJDK 17 or higher
- **Android SDK**: API Level 34 (Android 14)
- **Minimum Supported Android OS**: Android 8.0 (API Level 26)
- **Android NDK**: Version r25b or higher
- **Supported ABIs**:
  - `arm64-v8a` (Modern Android devices)
  - `armeabi-v7a` (32-bit ARM)
  - `x86_64` (Android Emulators / x86 tablets)
  - `x86` (32-bit Emulators)

---

## 4. Reproducible Build Instructions

### Step 1: Clone and navigate to Android directory
```bash
cd android
```

### Step 2: Compile Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be generated at:
```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Run Unit & Integration Tests
```bash
./gradlew test
```

### Step 4: Install to physical Android phone or emulator via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Physical Device Testing Guide

1. **Permission Request**:
   - Open Centium VPN on the phone.
   - Tap **Connect**.
   - Android will display the system VPN authorization dialog (*"Centium VPN wants to set up a VPN connection that allows it to monitor network traffic"*).
   - Tap **OK** / **Allow**.

2. **Tor Bootstrap Verification**:
   - Watch the circular gauge on the home screen.
   - Observe progression through the 8 phases:
     `STARTING_TOR` ➔ `WAITING_FOR_BOOTSTRAP (0-100%)` ➔ `STARTING_BRIDGE` ➔ `ESTABLISHING_VPN` ➔ `CONFIGURING_DNS` ➔ `VERIFYING_PROTECTION` ➔ `CONNECTED`.

3. **External Exit-IP Verification**:
   - Open Chrome or Firefox on the phone.
   - Navigate to `https://check.torproject.org`.
   - Verify green banner: *"Congratulations. This browser is configured to use Tor."*

4. **DNS Leak Resistance Audit**:
   - Open `https://dnsleaktest.com` or `https://ipleak.net`.
   - Run Standard/Extended Test.
   - Confirm all detected DNS servers are Tor exit relays in Germany, Switzerland, Netherlands, Iceland, etc. No ISP servers appear.

5. **Kill Switch & Fail-Closed Testing**:
   - Navigate to Android System Settings ➔ **Network & internet** ➔ **VPN**.
   - Tap the gear icon next to **Centium VPN**.
   - Toggle **Always-on VPN** and **Block connections without VPN**.
   - While connected, toggle Airplane mode on and off.
   - Confirm that Android strictly blocks all network egress until Centium re-verifies the Tor circuit.

---

## 6. Features & Security Limitations

### Implemented Features
- **Zero-Root Operation**: Fully compliant with standard Android unprivileged application sandbox.
- **8-Phase Connection State Machine**: Mirrors desktop Centium connection workflow with zero premature state claims.
- **Fail-Closed Tunnel Protection**: TUN interface is kept alive; unproxied network fallback is strictly blocked.
- **Transparent Tor DNS**: Intercepts UDP/TCP port 53 and resolves via Tor `DNSPort 9053`.
- **Anti-Loop Protection**: Native `VpnService.protect()` ensures Tor's upstream relay traffic bypasses the VPN tunnel.
- **UDP / QUIC Leak Mitigation**: Drops unsupported non-DNS UDP packets so browsers gracefully fall back to TCP over Tor.
- **10-Layer On-Device Diagnostics**: Real-time validation across all architecture layers.
- **Quick Settings Tile**: Single-tap toggle from Android notification shade.

### Known Technical Limitations
- **Arbitrary UDP Traffic**: Tor SOCKS does not support general-purpose UDP. Non-DNS UDP applications (like voice calls or BitTorrent over UDP) are dropped to prevent leakage.
- **Encrypted DNS (DoH/DoT)**: If an application specifically hardcodes an HTTPS-based DoH resolver (e.g., custom DoH server), the traffic is routed over Tor as normal HTTPS traffic rather than port 53.
- **Exit Node Egress**: Tor encrypts traffic between the phone and the Tor Exit node. Cleartext HTTP traffic exiting the Tor network is visible to the exit relay operator. HTTPS (TLS) should always be used for end-to-end encryption.
