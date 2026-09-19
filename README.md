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

## 2. How System-Wide VPN Routing Actually Works on Arch Linux

In the codebase:
- **`server/torManager.ts`**: Controls the local Tor process (`/usr/bin/tor`), manages ControlPort `9051`, generates the hardened `torrc` (enabling `TransPort 9040` and `DNSPort 5353`), monitors 100% bootstrap progress, and invokes the routing manager.
- **`linux/centium-routing.sh`**: The privileged firewall and routing engine. When you click **Connect**, it executes:
  1. Sets `/etc/resolv.conf` to `nameserver 127.0.0.1` so system DNS queries route to Tor DNSPort (`5353`).
  2. Creates the `CENTIUM_NAT` iptables chain: redirects outbound TCP SYN packets to Tor TransPort (`9040`).
  3. Bypasses the local `tor` user process (auto-detected as user `tor` on Arch Linux and `debian-tor` on Debian/Ubuntu) so Tor itself can communicate with outside relays.
  4. Enforces the **Kill Switch** (`CENTIUM_FILTER` chain): drops any outbound packets that attempt to bypass Tor.
  5. Drops IPv6 traffic to prevent dual-stack IPv6 leaks.

When you click **Disconnect**:
- Restores original `/etc/resolv.conf`.
- Flushes `CENTIUM_NAT` and `CENTIUM_FILTER` iptables chains.
- Restores default network routing.

---

## 3. Installation on Arch Linux

```bash
cd centium

# 1. Run the native Linux installer (installs dependencies, routing script, sudoers, and desktop launcher)
sudo ./setup-linux.sh

# 2. Launch Centium Desktop
centium
```

---

## 4. Manual Verification in Terminal

While Centium is **Connected**, verify in another terminal:

```bash
# Verify your IP is an encrypted Tor Exit Relay
curl https://check.torproject.org/api/ip

# Verify active iptables transparent routing rules
sudo centium-routing status
```

To manually reset your network at any time:
```bash
sudo centium-routing disable
```
