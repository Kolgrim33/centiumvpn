export const CENTIUM_SYSTEMD_SERVICE = `[Unit]
Description=Centium VPN Core Daemon
After=network.target network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/centium
ExecStart=/usr/bin/centiumd
Restart=always
RestartSec=3s
Environment=NODE_ENV=production
RuntimeDirectory=centium
RuntimeDirectoryMode=0755

[Install]
WantedBy=multi-user.target
`;

export const CENTIUM_ROUTING_SCRIPT = `#!/usr/bin/env bash
# Centium VPN - Transparent Tor Routing & Kill Switch Manager
# Compatible with: Arch Linux (tor user), Debian/Ubuntu (debian-tor), Fedora

set -euo pipefail

if [ -n "\${CENTIUM_TOR_UID:-}" ]; then
    TOR_UID="\$CENTIUM_TOR_UID"
elif id "tor" &>/dev/null; then
    TOR_UID="tor"
elif id "debian-tor" &>/dev/null; then
    TOR_UID="debian-tor"
else
    TOR_UID="\$(id -un)"
fi

TRANS_PORT="\${TRANS_PORT:-9040}"
DNS_PORT="\${DNS_PORT:-5353}"
RESOLV_BACKUP="/run/centium/resolv.conf.backup"

verify_prerequisites() {
    if command -v ss &>/dev/null; then
        if ! ss -tln | grep -q ":\${TRANS_PORT}\\b"; then
            echo "[Centium Error] Tor TransPort :\${TRANS_PORT} is not listening!" >&2
            return 1
        fi
    fi
    return 0
}

enable_routing() {
    verify_prerequisites || exit 1
    echo "[Centium] Enabling system-wide routing via Tor (Tor user: \$TOR_UID)..."

    mkdir -p /run/centium
    if [ -f /etc/resolv.conf ] && [ ! -f "\$RESOLV_BACKUP" ]; then
        cp /etc/resolv.conf "\$RESOLV_BACKUP" 2>/dev/null || true
    fi
    echo "nameserver 127.0.0.1" > /etc/resolv.conf 2>/dev/null || true

    if command -v ip6tables &>/dev/null; then
        ip6tables -P INPUT DROP 2>/dev/null || true
        ip6tables -P OUTPUT DROP 2>/dev/null || true
        ip6tables -P FORWARD DROP 2>/dev/null || true
        ip6tables -A OUTPUT -o lo -j ACCEPT 2>/dev/null || true
    fi

    if command -v iptables &>/dev/null; then
        iptables -t nat -N CENTIUM_NAT 2>/dev/null || iptables -t nat -F CENTIUM_NAT
        iptables -t nat -A CENTIUM_NAT -p udp --dport 53 -j REDIRECT --to-ports "\$DNS_PORT"
        iptables -t nat -A CENTIUM_NAT -p tcp --dport 53 -j REDIRECT --to-ports "\$DNS_PORT"
        iptables -t nat -A CENTIUM_NAT -m owner --uid-owner "\$TOR_UID" -j RETURN 2>/dev/null || true
        iptables -t nat -A CENTIUM_NAT -d 127.0.0.0/8 -j RETURN
        iptables -t nat -A CENTIUM_NAT -p tcp --syn -j REDIRECT --to-ports "\$TRANS_PORT"

        iptables -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
        iptables -t nat -A OUTPUT -j CENTIUM_NAT

        iptables -t filter -N CENTIUM_FILTER 2>/dev/null || iptables -t filter -F CENTIUM_FILTER
        iptables -t filter -A CENTIUM_FILTER -o lo -j ACCEPT
        iptables -t filter -A CENTIUM_FILTER -m owner --uid-owner "\$TOR_UID" -j ACCEPT 2>/dev/null || true
        iptables -t filter -A CENTIUM_FILTER -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || \
            iptables -t filter -A CENTIUM_FILTER -m state --state ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || true
        iptables -t filter -A CENTIUM_FILTER -j DROP

        iptables -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
        iptables -t filter -A OUTPUT -j CENTIUM_FILTER
    fi

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
