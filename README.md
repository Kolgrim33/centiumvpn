# Centium VPN

Centium VPN is a privacy-focused desktop VPN application that routes system Internet traffic through the **Tor network**.

## How Centium Works

Unlike conventional VPN providers that operate centralized VPN servers (where the provider can view, decrypt, or log your browsing traffic), Centium uses a **zero-backend routing architecture**:

```
Your Applications (Browser, Shell, Clients)
       ↓
Centium Virtual Interface (centium0)
       ↓
Centium Network Daemon (TransPort :9040, DNSPort :5353)
       ↓
Local Tor Process
       ↓
Tor Guard Relay (Layer 1 encryption)
       ↓
Tor Middle Relay (Layer 2 encryption)
       ↓
Tor Exit Relay (Layer 3 decryption)
       ↓
Destination Internet
```

Centium backend servers are **never** in your traffic path.

---

## Live Web Preview vs. Native Desktop System VPN

1. **In the Web Sandbox Preview**:
   - The **real Tor binary (v0.4.9.11)** is compiled and running inside the Linux container.
   - SOCKS5 proxy on `127.0.0.1:9050`, DNSPort on `127.0.0.1:5353`, and TransPort on `127.0.0.1:9040` are active.
   - Live onion circuits are formed and verified against `check.torproject.org`.
   - However, because the preview runs inside a remote Cloud Run sandboxed container (gVisor), the remote container cannot modify the network interface of your physical local computer.

2. **On Your Physical Linux Machine (Arch, Ubuntu, Debian, Fedora)**:
   - Centium runs locally as a native application.
   - The privileged daemon (`centiumd`) creates the virtual interface (`centium0`), configures `iptables`/`nftables` to transparently route all system TCP traffic through Tor, sets DNS to Tor DNSPort (`5353`), and arms the fail-closed kill switch.

---

## Quick Start on Your Linux Desktop

### 1. Clone or Download the Repository
```bash
git clone <repository-url> centium
cd centium
```

### 2. Run the Automated Installer
```bash
sudo ./setup-linux.sh
```

### 3. Launch Centium
```bash
# Start the Centium service
sudo systemctl start centiumd

# Start the application
npm start
```

Or run directly in development mode:
```bash
sudo npm run dev
```

---

## Manual Installation by Distribution

### Arch Linux
```bash
cd linux/arch
makepkg -si
sudo systemctl enable --now centiumd
```

### Ubuntu / Debian
```bash
# 1. Install dependencies
sudo apt-get update
sudo apt-get install -y tor iptables iproute2 nodejs npm

# 2. Setup routing script and service
sudo cp linux/centium-routing.sh /usr/local/bin/centium-routing
sudo chmod +x /usr/local/bin/centium-routing
sudo cp linux/centiumd.service /etc/systemd/system/centiumd.service
sudo systemctl daemon-reload
sudo systemctl enable --now centiumd

# 3. Build and launch UI
npm install
npm run build
npm start
```

---

## Transparent Routing & Kill Switch Commands

You can also test the transparent routing rules independently:

- **Enable Tor system-wide routing + Kill Switch**:
  ```bash
  sudo /usr/local/bin/centium-routing enable
  ```
- **Check routing & filter status**:
  ```bash
  sudo /usr/local/bin/centium-routing status
  ```
- **Disable routing and restore normal network defaults**:
  ```bash
  sudo /usr/local/bin/centium-routing disable
  ```

---

## Core Privacy Features
- **Fail-Closed Kill Switch**: If the Tor process crashes or circuit drops, `iptables` blocks all non-Tor outbound traffic to prevent IP leaks.
- **DNS Leak Protection**: System DNS is locked to `127.0.0.1:5353` (Tor DNSPort) so lookups never leak to your ISP.
- **IPv6 Shield**: Blocks un-tunneled IPv6 packets to prevent dual-stack IP bypass.
- **Pluggable Bridges**: Supports `obfs4` and `snowflake` for users in censored networks.
- **Exit Jurisdiction**: Set preferred exit relay countries via Tor's `ExitNodes` directive.
