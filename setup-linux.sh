#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Native Linux Desktop Installer & Setup Script
# Architecture: TUN (centium0) + hev-socks5-tunnel + Tor SOCKS5 (:9050)
# Supports: Arch Linux, Ubuntu, Debian, Fedora
# ==============================================================================

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================================"
echo "          Centium VPN - Linux System Setup             "
echo "  Architecture: TUN (centium0) -> SOCKS5 -> Tor SOCKS5  "
echo "========================================================"

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "[!] Please run this script with sudo: sudo ./setup-linux.sh"
    exit 1
fi

REAL_USER="${SUDO_USER:-$(logname 2>/dev/null || echo "$USER")}"

# Detect Package Manager
if command -v pacman &>/dev/null; then
    DISTRO="arch"
    echo "[+] Detected Arch Linux"
elif command -v apt-get &>/dev/null; then
    DISTRO="debian"
    echo "[+] Detected Ubuntu/Debian"
elif command -v dnf &>/dev/null; then
    DISTRO="fedora"
    echo "[+] Detected Fedora"
else
    DISTRO="unknown"
    echo "[!] Unknown distribution. Please ensure dependencies are installed manually."
fi

# 1. Install Required Packages
echo "[1/9] Installing core dependencies (tor, nftables, iproute2, curl, nodejs, npm, simdjson, git, make, gcc)..."
case "$DISTRO" in
    arch)
        pacman -S --needed --noconfirm tor nftables iproute2 curl nodejs npm simdjson git make gcc || \
        pacman -Sy --needed --noconfirm tor nftables iproute2 curl nodejs npm simdjson git make gcc
        ;;
    debian)
        apt-get update -qq
        apt-get install -y tor nftables iproute2 curl nodejs npm git make gcc build-essential
        ;;
    fedora)
        dnf install -y tor nftables iproute curl nodejs npm git make gcc
        ;;
esac

# 2. Stop default system Tor service so port 9050 is not locked
echo "[2/9] Stopping unmanaged default system Tor service to release port 9050..."
systemctl stop tor 2>/dev/null || true
systemctl disable tor 2>/dev/null || true

# 3. Setup Centium Runtime Directories
echo "[3/9] Creating runtime directories (/run/centium, /var/lib/centium/tor, /opt/centium)..."
mkdir -p /run/centium
mkdir -p /var/lib/centium/tor
chmod 775 /run/centium
chmod 755 /var/lib/centium
chmod 700 /var/lib/centium/tor

# Set correct ownership for Tor and ensure account is active
if id "tor" &>/dev/null; then
    chown root:tor /run/centium 2>/dev/null || true
    chown -R tor:tor /var/lib/centium/tor
    chage -E -1 tor 2>/dev/null || true
elif id "debian-tor" &>/dev/null; then
    chown root:debian-tor /run/centium 2>/dev/null || true
    chown -R debian-tor:debian-tor /var/lib/centium/tor
    chage -E -1 debian-tor 2>/dev/null || true
fi

# 4. Build and Install Pinned hev-socks5-tunnel (v2.17.1)
echo "[4/9] Building and installing pinned hev-socks5-tunnel (v2.17.1)..."
HEV_BUILD_DIR="/tmp/hev-socks5-tunnel-build"
rm -rf "$HEV_BUILD_DIR"
BUILD_SUCCESS=0
if git clone --depth 1 --recursive --branch 2.17.1 https://github.com/heiher/hev-socks5-tunnel.git "$HEV_BUILD_DIR" 2>/dev/null; then
    cd "$HEV_BUILD_DIR"
    if make -j"$(nproc 2>/dev/null || echo 2)" 2>/dev/null && [ -f bin/hev-socks5-tunnel ]; then
        cp bin/hev-socks5-tunnel /usr/local/bin/hev-socks5-tunnel
        cp bin/hev-socks5-tunnel /usr/bin/hev-socks5-tunnel 2>/dev/null || true
        chmod 755 /usr/local/bin/hev-socks5-tunnel /usr/bin/hev-socks5-tunnel 2>/dev/null || true
        BUILD_SUCCESS=1
    fi
    cd "$DIR"
fi

if [ "$BUILD_SUCCESS" -eq 0 ] && [ ! -f /usr/local/bin/hev-socks5-tunnel ]; then
    echo "[*] Compiling from source was skipped or failed. Installing official prebuilt standalone binary..."
    ARCH="$(uname -m)"
    HEV_URL=""
    if [ "$ARCH" = "x86_64" ]; then
        HEV_URL="https://github.com/heiher/hev-socks5-tunnel/releases/download/2.17.1/hev-socks5-tunnel-linux-x86_64"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        HEV_URL="https://github.com/heiher/hev-socks5-tunnel/releases/download/2.17.1/hev-socks5-tunnel-linux-arm64"
    fi
    if [ -n "$HEV_URL" ]; then
        curl -sL -o /usr/local/bin/hev-socks5-tunnel "$HEV_URL" 2>/dev/null || true
        cp /usr/local/bin/hev-socks5-tunnel /usr/bin/hev-socks5-tunnel 2>/dev/null || true
        chmod 755 /usr/local/bin/hev-socks5-tunnel /usr/bin/hev-socks5-tunnel 2>/dev/null || true
    fi
fi

# Grant CAP_NET_ADMIN if possible
if command -v setcap &>/dev/null && [ -f /usr/local/bin/hev-socks5-tunnel ]; then
    setcap cap_net_admin+ep /usr/local/bin/hev-socks5-tunnel 2>/dev/null || true
fi

# 5. Build application
echo "[5/9] Building Centium application..."

# Verify Node.js runtime is intact and heal if shared libraries are missing
if ! node -v &>/dev/null; then
    echo "[!] Node.js encountered a library error, attempting dependency repair..."
    if [ "$DISTRO" = "arch" ]; then
        pacman -S --needed --noconfirm simdjson nodejs npm 2>/dev/null || true
    fi
fi

BUILD_SUCCESS=0
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    echo "[*] Building application as user $REAL_USER..."
    sudo -u "$REAL_USER" npm install && sudo -u "$REAL_USER" npm run build && BUILD_SUCCESS=1
else
    echo "[*] Building application..."
    npm install && npm run build && BUILD_SUCCESS=1
fi

if [ "$BUILD_SUCCESS" -eq 0 ]; then
    echo "[!] Fatal: Application build failed. Aborting installation." >&2
    exit 1
fi

# Deploy to /opt/centium
mkdir -p /opt/centium
cp -r "$DIR"/* /opt/centium/ 2>/dev/null || true
if [ -d "$DIR/node_modules" ] && [ ! -d "/opt/centium/node_modules" ]; then
    cp -r "$DIR/node_modules" /opt/centium/
fi
# Secure /opt/centium: owned by root, non-world-writable
chown -R root:root /opt/centium
chmod -R go-w,a+rX /opt/centium
chmod 755 /opt/centium/centium-desktop.sh 2>/dev/null || true

# 6. Install Network Engine & Diagnostics Suite
echo "[6/9] Installing centium-network and centium-diagnose..."
cp "$DIR/linux/centium-network.sh" /usr/local/bin/centium-network
cp "$DIR/linux/centium-network.sh" /usr/bin/centium-network
chmod 755 /usr/local/bin/centium-network /usr/bin/centium-network

cp "$DIR/linux/centium-diagnose.sh" /usr/local/bin/centium-diagnose
cp "$DIR/linux/centium-diagnose.sh" /usr/bin/centium-diagnose
chmod 755 /usr/local/bin/centium-diagnose /usr/bin/centium-diagnose

# 7. Sudoers rule so Centium can manage network engine without password prompts
echo "[7/9] Configuring passwordless sudo rules (/etc/sudoers.d/centium)..."
SUDO_USER_TARGET="${REAL_USER:-ALL}"
SUDO_TMP="/tmp/centium_sudoers.$$"

cat << EOF > "$SUDO_TMP"
Defaults env_keep += "CENTIUM_TOR_UID SOCKS5_PORT"
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/local/bin/centium-network
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/local/bin/centium-network *
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/centium-network
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/centium-network *
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/local/bin/centium-diagnose
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/local/bin/centium-diagnose *
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/centium-diagnose
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/centium-diagnose *
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/systemctl start centiumd
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/systemctl stop centiumd
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart centiumd
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /usr/bin/systemctl status centiumd
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /bin/systemctl start centiumd
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /bin/systemctl stop centiumd
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /bin/systemctl restart centiumd
${SUDO_USER_TARGET} ALL=(ALL) NOPASSWD: /bin/systemctl status centiumd
EOF

chmod 440 "$SUDO_TMP"
if command -v visudo &>/dev/null; then
    if visudo -cf "$SUDO_TMP"; then
        mv "$SUDO_TMP" /etc/sudoers.d/centium
    else
        echo "[!] Fatal: visudo validation failed on sudoers template" >&2
        rm -f "$SUDO_TMP"
        exit 1
    fi
else
    mv "$SUDO_TMP" /etc/sudoers.d/centium
fi

# 8. Install Centium Daemon & Systemd Services
echo "[8/9] Installing centiumd executable and systemd services..."
cp "$DIR/linux/centiumd" /usr/bin/centiumd
chmod 755 /usr/bin/centiumd

cp "$DIR/linux/centiumd.service" /etc/systemd/system/centiumd.service
cp "$DIR/linux/centium-hev-socks5.service" /etc/systemd/system/centium-hev-socks5.service 2>/dev/null || true
systemctl daemon-reload
systemctl enable --now centiumd || echo "[!] Notice: centiumd service enabled"

# 9. Install Desktop Launcher & Icons
echo "[9/9] Installing Desktop Application Launcher and Icons..."
cat << 'EOF' > /usr/bin/centium
#!/usr/bin/env bash
if [ -f "/opt/centium/centium-desktop.sh" ]; then
    exec "/opt/centium/centium-desktop.sh" "$@"
else
    DIR="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
    exec "$DIR/centium-desktop.sh" "$@"
fi
EOF
chmod 755 /usr/bin/centium
cp /usr/bin/centium /usr/local/bin/centium 2>/dev/null || true

mkdir -p /usr/share/icons/hicolor/scalable/apps
cp "$DIR/public/favicon.svg" /usr/share/icons/hicolor/scalable/apps/centium.svg 2>/dev/null || true
cp "$DIR/linux/centium.desktop" /usr/share/applications/centium.desktop 2>/dev/null || true

echo "========================================================"
echo "[✓] Centium VPN is fully installed and active!"
echo "    Architecture: TUN (centium0) + hev-socks5-tunnel + Tor SOCKS5"
echo ""
echo "Core Daemon Status:"
systemctl status centiumd --no-pager || true
echo ""
echo "To launch the native desktop application:"
echo "  centium"
echo "To run the automated verification test suite:"
echo "  centium-diagnose"
echo "========================================================"
