import fs from 'fs';
import path from 'path';

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
TimeoutStopSec=15s
KillMode=control-group
Environment=NODE_ENV=production
RuntimeDirectory=centium
RuntimeDirectoryMode=0775

[Install]
WantedBy=multi-user.target
`;

export const CENTIUM_HEV_SERVICE = `[Unit]
Description=Centium TUN to SOCKS5 Bridge (hev-socks5-tunnel)
Documentation=https://github.com/heiher/hev-socks5-tunnel
After=network.target network-online.target centiumd.service
PartOf=centiumd.service

[Service]
Type=simple
ExecStart=/usr/local/bin/hev-socks5-tunnel /run/centium/hev-socks5-tunnel.yml
Restart=on-failure
RestartSec=2s
KillMode=control-group
AmbientCapabilities=CAP_NET_ADMIN
CapabilityBoundingSet=CAP_NET_ADMIN
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/run/centium

[Install]
WantedBy=multi-user.target
`;

function loadCentiumNetworkScript(): string {
  const candidatePaths = [
    path.resolve(process.cwd(), 'linux/centium-network.sh'),
    '/usr/local/bin/centium-network',
    '/usr/bin/centium-network',
  ];
  for (const p of candidatePaths) {
    if (fs.existsSync(p)) {
      try {
        return fs.readFileSync(p, 'utf-8');
      } catch {}
    }
  }
  return '';
}

export const CENTIUM_NETWORK_SCRIPT = loadCentiumNetworkScript();
export const CENTIUM_ROUTING_SCRIPT = CENTIUM_NETWORK_SCRIPT;

export const ARCH_PKGBUILD = `# Maintainer: Centium Core Team <packages@centiumvpn.org>
pkgname=centium
pkgver=1.1.0
pkgrel=1
pkgdesc="Privacy-focused desktop VPN powered by Tor and TUN-to-SOCKS5 architecture"
arch=('x86_64')
url="https://centiumvpn.org"
license=('GPL-3.0-or-later')
depends=('tor' 'nftables' 'iproute2' 'curl' 'webkit2gtk' 'gtk3')
makedepends=('git' 'make' 'gcc' 'nodejs' 'npm')
backup=('etc/centium/config.json')
source=(
    "$pkgname-$pkgver.tar.gz"
    "git+https://github.com/heiher/hev-socks5-tunnel.git#commit=9a06bc6e8b4e78347f3b890fa25e6e1ad1bf5d8f"
)
sha256sums=('SKIP' 'SKIP')

build() {
    # 1. Build pinned hev-socks5-tunnel v2.17.1
    cd "$srcdir/hev-socks5-tunnel"
    git submodule update --init --recursive
    make

    # 2. Build Centium application & daemon
    cd "$srcdir/$pkgname-$pkgver"
    npm ci
    npm run build
}

package() {
    cd "$srcdir/$pkgname-$pkgver"
    # Install binaries
    install -Dm755 "$srcdir/hev-socks5-tunnel/bin/hev-socks5-tunnel" "$pkgdir/usr/local/bin/hev-socks5-tunnel"
    install -Dm755 linux/centium-network.sh "$pkgdir/usr/local/bin/centium-network"
    install -Dm755 linux/centium-diagnose.sh "$pkgdir/usr/local/bin/centium-diagnose"
    install -Dm755 linux/centiumd "$pkgdir/usr/bin/centiumd"

    # Install services
    install -Dm644 linux/centiumd.service "$pkgdir/usr/lib/systemd/system/centiumd.service"
    install -Dm644 linux/centium-hev-socks5.service "$pkgdir/usr/lib/systemd/system/centium-hev-socks5.service"
    install -Dm644 linux/centium.desktop "$pkgdir/usr/share/applications/centium.desktop"
}
`;

export const DEBIAN_CONTROL = `Source: centium
Section: net
Priority: optional
Maintainer: Centium Core Team <packages@centiumvpn.org>
Build-Depends: debhelper-compat (= 13), make, gcc, git, libssl-dev, nodejs, npm
Standards-Version: 4.6.2
Homepage: https://centiumvpn.org

Package: centium
Architecture: any
Depends: \${shlibs:Depends}, \${misc:Depends}, tor (>= 0.4.7), nftables, iproute2, curl
Description: Privacy-focused desktop VPN application powered by the Tor network
 Centium routes system network traffic through an isolated TUN device (centium0)
 and hev-socks5-tunnel directly into Tor SOCKS5 (127.0.0.1:9050). Includes
 fail-closed nftables kill switch (table inet centium), policy routing (table 8420),
 and mapped-DNS interception without intermediate proxy logging.
`;

export const RUST_DAEMON_SOURCE = `// Centium Core Daemon (centiumd)
// Privileged background service running with root privileges on Linux
// Manages Tor lifecycle, TUN centium0 device, policy routing, and nftables kill switch.

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
    println!("[centiumd] Starting Centium Privileged Daemon (TUN + nftables engine)...");

    if let Some(parent) = Path::new(SOCKET_PATH).parent() {
        std::fs::create_dir_all(parent)?;
    }
    let _ = std::fs::remove_file(SOCKET_PATH);

    let listener = UnixListener::bind(SOCKET_PATH)?;
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
            Err(e) => eprintln!("[centiumd] Connection failed: {}", e),
        }
    }
}

async fn handle_ipc_message(payload: &[u8]) -> String {
    let msg: Result<DaemonCommand, _> = serde_json::from_slice(payload);
    match msg {
        Ok(DaemonCommand::Connect { .. }) => {
            let status = Command::new("/usr/local/bin/centium-network")
                .arg("enable")
                .status();
            match status {
                Ok(s) if s.success() => r#"{"success":true,"state":"CONNECTED"}"#.to_string(),
                _ => r#"{"success":false,"error":"Failed to configure TUN network"}"#.to_string(),
            }
        }
        Ok(DaemonCommand::Disconnect) => {
            let _ = Command::new("/usr/local/bin/centium-network")
                .arg("disable")
                .status();
            r#"{"success":true,"state":"DISCONNECTED"}"#.to_string()
        }
        Ok(DaemonCommand::GetStatus) => r#"{"state":"OK","engine":"TUN+hev-socks5"}"#.to_string(),
        Ok(DaemonCommand::RunDiagnostics) => {
            let output = Command::new("/usr/local/bin/centium-diagnose").output();
            match output {
                Ok(o) => String::from_utf8_lossy(&o.stdout).to_string(),
                Err(e) => format!("Diagnostics error: {}", e),
            }
        }
        Err(e) => format!(r#"{{"error":"Invalid payload: {}"}}"#, e),
    }
}
`;
