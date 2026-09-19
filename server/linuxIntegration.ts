export const CENTIUM_SYSTEMD_SERVICE = `[Unit]
Description=Centium VPN Privileged Daemon
After=network.target network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
ExecStart=/usr/bin/centiumd --socket /run/centium/centium.sock
Restart=on-failure
RestartSec=3s
RuntimeDirectory=centium
RuntimeDirectoryMode=0755

# Security hardening
ProtectSystem=strict
ProtectHome=read-only
PrivateTmp=true
ProtectControlGroups=true
ReadWritePaths=/run/centium /var/lib/centium /etc/resolv.conf

[Install]
WantedBy=multi-user.target
`;

export const CENTIUM_ROUTING_SCRIPT = `#!/usr/bin/env bash
# Centium VPN - Transparent Tor Routing & Kill Switch Manager
# Supports both iptables and nftables

set -euo pipefail

TOR_UID="\${TOR_UID:-debian-tor}"
TRANS_PORT="\${TRANS_PORT:-9040}"
DNS_PORT="\${DNS_PORT:-5353}"
TUN_DEV="centium0"
RESOLV_BACKUP="/run/centium/resolv.conf.backup"

enable_routing() {
    echo "[Centium] Enabling system-wide routing via Tor..."

    # 1. Back up system DNS
    if [ -f /etc/resolv.conf ] && [ ! -f "$RESOLV_BACKUP" ]; then
        mkdir -p /run/centium
        cp /etc/resolv.conf "$RESOLV_BACKUP"
    fi

    # 2. Set DNS to local Tor DNSPort (127.0.0.1:5353)
    echo "nameserver 127.0.0.1" > /etc/resolv.conf

    # 3. Block all IPv6 traffic to prevent IPv6 bypass leaks
    ip6tables -P INPUT DROP || true
    ip6tables -P OUTPUT DROP || true
    ip6tables -P FORWARD DROP || true
    ip6tables -A OUTPUT -o lo -j ACCEPT || true

    # 4. Flush existing Centium chains
    iptables -t nat -N CENTIUM_NAT 2>/dev/null || iptables -t nat -F CENTIUM_NAT
    iptables -t filter -N CENTIUM_FILTER 2>/dev/null || iptables -t filter -F CENTIUM_FILTER

    # 5. NAT redirection (Transparent TCP Proxy & DNS)
    # Redirect DNS traffic (UDP port 53) to Tor DNSPort
    iptables -t nat -A CENTIUM_NAT -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"
    # Exclude Tor daemon process user from loop redirection
    iptables -t nat -A CENTIUM_NAT -m owner --uid-owner "$TOR_UID" -j RETURN
    # Exclude local loopback traffic
    iptables -t nat -A CENTIUM_NAT -d 127.0.0.0/8 -j RETURN
    # Redirect all remaining TCP traffic to Tor TransPort
    iptables -t nat -A CENTIUM_NAT -p tcp --syn -j REDIRECT --to-ports "$TRANS_PORT"

    # Link into OUTPUT chain
    iptables -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
    iptables -t nat -A OUTPUT -j CENTIUM_NAT

    # 6. Kill Switch (Fail-closed filter rules)
    # Allow local loopback
    iptables -t filter -A CENTIUM_FILTER -o lo -j ACCEPT
    # Allow Tor daemon traffic to establish connections to relays
    iptables -t filter -A CENTIUM_FILTER -m owner --uid-owner "$TOR_UID" -j ACCEPT
    # Allow established and related connections
    iptables -t filter -A CENTIUM_FILTER -m state --state ESTABLISHED,RELATED -j ACCEPT
    # Drop all non-Tor egress (Kill Switch)
    iptables -t filter -A CENTIUM_FILTER -j DROP

    iptables -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
    iptables -t filter -A OUTPUT -j CENTIUM_FILTER

    echo "[Centium] System routing active. Traffic safely contained in Tor network."
}

disable_routing() {
    echo "[Centium] Disabling Tor routing and restoring network defaults..."

    # 1. Detach and flush Centium iptables chains
    iptables -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
    iptables -t nat -F CENTIUM_NAT 2>/dev/null || true
    iptables -t nat -X CENTIUM_NAT 2>/dev/null || true

    iptables -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
    iptables -t filter -F CENTIUM_FILTER 2>/dev/null || true
    iptables -t filter -X CENTIUM_FILTER 2>/dev/null || true

    # 2. Restore IPv6 default policy
    ip6tables -P INPUT ACCEPT 2>/dev/null || true
    ip6tables -P OUTPUT ACCEPT 2>/dev/null || true
    ip6tables -P FORWARD ACCEPT 2>/dev/null || true
    ip6tables -F 2>/dev/null || true

    # 3. Restore DNS resolver
    if [ -f "$RESOLV_BACKUP" ]; then
        cp "$RESOLV_BACKUP" /etc/resolv.conf
        rm -f "$RESOLV_BACKUP"
    fi

    echo "[Centium] Normal network routing restored."
}

case "\${1:-}" in
    enable)
        enable_routing
        ;;
    disable)
        disable_routing
        ;;
    status)
        iptables -t nat -L CENTIUM_NAT -n -v 2>/dev/null || echo "Centium NAT not active"
        iptables -t filter -L CENTIUM_FILTER -n -v 2>/dev/null || echo "Kill switch not active"
        ;;
    *)
        echo "Usage: $0 {enable|disable|status}"
        exit 1
        ;;
esac
`;

export const ARCH_PKGBUILD = `# Maintainer: Centium Core Team <packages@centiumvpn.org>
pkgname=centium
pkgver=1.0.0
pkgrel=1
pkgdesc="Privacy-focused desktop VPN application powered by the Tor network"
arch=('x86_64')
url="https://centiumvpn.org"
license=('GPL-3.0-or-later')
depends=('tor' 'iptables' 'iproute2' 'webkit2gtk' 'gtk3')
makedepends=('rust' 'cargo' 'nodejs' 'npm')
backup=('etc/centium/config.json')
source=("$pkgname-$pkgver.tar.gz")
sha256sums=('SKIP')

build() {
    cd "$pkgname-$pkgver"
    npm ci
    npm run build
    cargo build --release --locked
}

package() {
    cd "$pkgname-$pkgver"
    install -Dm755 target/release/centium "$pkgdir/usr/bin/centium"
    install -Dm755 target/release/centiumd "$pkgdir/usr/bin/centiumd"
    install -Dm644 linux/centiumd.service "$pkgdir/usr/lib/systemd/system/centiumd.service"
    install -Dm644 linux/centium.desktop "$pkgdir/usr/share/applications/centium.desktop"
    install -Dm644 linux/centium.svg "$pkgdir/usr/share/icons/hicolor/scalable/apps/centium.svg"
}
`;

export const DEBIAN_CONTROL = `Source: centium
Section: net
Priority: optional
Maintainer: Centium Core Team <packages@centiumvpn.org>
Build-Depends: debhelper-compat (= 13), cargo, rustc, libwebkit2gtk-4.1-dev, libssl-dev, nodejs, npm
Standards-Version: 4.6.2
Homepage: https://centiumvpn.org

Package: centium
Architecture: any
Depends: \${shlibs:Depends}, \${misc:Depends}, tor (>= 0.4.7), iptables, iproute2
Description: Privacy-focused desktop VPN application powered by the Tor network
 Centium routes system network traffic through onion circuits on the Tor network
 without intermediate VPN servers or proxy logging. Includes fail-closed kill switch,
 DNS leak prevention, and bridge configuration.
`;

export const RUST_DAEMON_SOURCE = `// Centium Core Daemon (centiumd)
// Privileged background service running with root privileges on Linux
// Manages Tor lifecycle, TUN centium0 device, and firewall/routing tables.

use std::error::Error;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;
use tokio::net::UnixListener;
use tokio::io::{AsyncReadExt, AsyncWriteExt};

const SOCKET_PATH: &str = "/run/centium/centium.sock";

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub enum DaemonCommand {
    Connect { exit_node: Option<String>, bridge: Option<String> },
    Disconnect,
    GetStatus,
    RunDiagnostics,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    println!("[centiumd] Starting Centium Privileged Daemon...");

    // Ensure socket directory exists
    if let Some(parent) = Path::new(SOCKET_PATH).parent() {
        std::fs::create_dir_all(parent)?;
    }
    let _ = std::fs::remove_file(SOCKET_PATH);

    let listener = UnixListener::bind(SOCKET_PATH)?;
    // Set socket permissions for non-root UI client group
    std::fs::set_permissions(SOCKET_PATH, std::fs::Permissions::from_mode(0o660))?;

    println!("[centiumd] Listening on {}", SOCKET_PATH);

    loop {
        match listener.accept().await {
            Ok((mut stream, _)) => {
                tokio::spawn(async move {
                    let mut buf = [0u8; 4096];
                    if let Ok(n) = stream.read(&mut buf).await {
                        if n > 0 {
                            let response = handle_ipc_message(&buf[..n]).await;
                            let _ = stream.write_all(response.as_bytes()).await;
                        }
                    }
                });
            }
            Err(e) => eprintln!("[centiumd] Socket error: {}", e),
        }
    }
}

async fn handle_ipc_message(payload: &[u8]) -> String {
    // Deserialize command and execute network routing
    "{\\"status\\":\\"ok\\"}".to_string()
}
`;
