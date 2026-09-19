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

function loadCentiumRoutingScript(): string {
  const candidatePaths = [
    path.resolve(process.cwd(), 'linux/centium-routing.sh'),
    '/usr/local/bin/centium-routing',
    '/usr/bin/centium-routing',
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

export const CENTIUM_ROUTING_SCRIPT = loadCentiumRoutingScript();

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
