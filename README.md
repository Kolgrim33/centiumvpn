# Centium VPN

Centium VPN is a privacy-focused desktop VPN application that routes system Internet traffic through the **Tor network**.

## 1. Running Centium as a Native Desktop App (Not Just in a Browser)

Centium is designed to run as a **native desktop application window**. 

Once installed, you do not need to open a browser tab. You can launch Centium directly:

```bash
# Launch as a standalone desktop application window
centium
# or
./centium-desktop.sh
# or
npm run desktop
```

This opens Centium in a dedicated, distraction-free desktop window with the Centium application icon, system tray menu, and native window controls.

---

## 2. How System-Wide VPN Routing Actually Works on Linux

In the codebase:
- **`server/torManager.ts`**: Controls the local Tor process (`/usr/bin/tor`), manages ControlPort `9051`, generates the hardened `torrc`, monitors 100% bootstrap progress, and invokes the network engine.
- **`linux/centium-network.sh`**: The privileged firewall, TUN, and routing engine (`centium-network`). When you click **Connect**, it executes:
  1. Arms the **Fail-Closed Kill Switch**: creates the `table inet centium` in nftables with default-drop outbound policy.
  2. Bypasses the local `tor` user process (auto-detected as `debian-tor` on Debian/Ubuntu and `tor` on Arch Linux) so Tor itself can communicate with outside relays.
  3. Creates and brings up the virtual TUN interface (`centium0`, `198.18.0.1/15`).
  4. Starts `hev-socks5-tunnel` to bridge packets between `centium0` and Tor's local SOCKS5 proxy (`127.0.0.1:9050`).
  5. Installs isolated policy routing (dedicated routing table `8420` with default route through `centium0`), leaving the main system routing table untouched.
  6. Configures mapped-DNS (`198.18.0.2`), intercepting DNS queries directly through the tunnel without plaintext leaks.
  7. Enforces dual-stack IPv6 drop to prevent IPv6 leaks.

When you click **Disconnect**:
- Restores original `/etc/resolv.conf`.
- Flushes policy routing table `8420` and ip rule lookups.
- Stops `hev-socks5-tunnel` and removes `centium0`.
- Deletes the `inet centium` nftables table.
- Restores default network routing.

---

## 3. Installation on Linux (Arch, Debian, Ubuntu, Fedora)

```bash
cd centium

# 1. Run the native Linux installer (installs dependencies, hev-socks5-tunnel, centiumd systemd service, sudoers, and desktop launcher)
sudo ./setup-linux.sh

# 2. Launch Centium Desktop
centium
```

---

## 4. Manual Verification & Diagnostics in Terminal

While Centium is **Connected**, verify in another terminal:

```bash
# Verify your IP is an encrypted Tor Exit Relay
curl https://check.torproject.org/api/ip

# Verify active TUN interface, nftables kill switch, and routing status
sudo centium-network status

# Run the automated diagnostic test suite
centium-diagnose
```

To manually reset your network at any time:
```bash
sudo centium-network disable
```
