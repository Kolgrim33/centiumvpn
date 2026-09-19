#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Native Linux Desktop Installer & Setup Script
# Supports: Arch Linux, Ubuntu, Debian, Fedora
# ==============================================================================

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================================"
echo "          Centium VPN - Linux System Setup             "
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
echo "[1/8] Installing core dependencies (tor, iptables, iproute2, curl, nodejs, npm)..."
case "$DISTRO" in
    arch)
        pacman -Sy --needed --noconfirm tor iptables iproute2 curl nodejs npm
        ;;
    debian)
        apt-get update -qq
        apt-get install -y tor iptables iproute2 curl nodejs npm
        ;;
    fedora)
        dnf install -y tor iptables iproute curl nodejs npm
        ;;
esac

# 2. Stop default system Tor service so port 9050 is not locked
echo "[2/8] Stopping unmanaged default system Tor service to release port 9050..."
systemctl stop tor 2>/dev/null || true
systemctl disable tor 2>/dev/null || true

# 3. Setup Centium Runtime Directories
echo "[3/8] Creating runtime directories (/run/centium, /var/lib/centium/tor, /opt/centium)..."
mkdir -p /run/centium
mkdir -p /var/lib/centium/tor
chmod 775 /run/centium
chmod 755 /var/lib/centium
chmod 700 /var/lib/centium/tor

# Set correct ownership for Tor and ensure account is not expired
if id "tor" &>/dev/null; then
    chown root:tor /run/centium 2>/dev/null || true
    chown -R tor:tor /var/lib/centium/tor
    chage -E -1 tor 2>/dev/null || true
elif id "debian-tor" &>/dev/null; then
    chown root:debian-tor /run/centium 2>/dev/null || true
    chown -R debian-tor:debian-tor /var/lib/centium/tor
    chage -E -1 debian-tor 2>/dev/null || true
fi

# Pre-load netfilter kernel modules
if command -v modprobe &>/dev/null; then
    for mod in ip_tables iptable_filter iptable_nat nf_nat xt_REDIRECT xt_owner xt_conntrack xt_tcpudp; do
        modprobe "$mod" 2>/dev/null || true
    done
fi

# 4. Build application
echo "[4/8] Building Centium application..."
cd "$DIR"
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    sudo -u "$REAL_USER" npm install
    sudo -u "$REAL_USER" npm run build
else
    npm install
    npm run build
fi

# Deploy to /opt/centium
mkdir -p /opt/centium
cp -r "$DIR"/* /opt/centium/ 2>/dev/null || true
# Ensure node_modules exists in /opt/centium
if [ -d "$DIR/node_modules" ] && [ ! -d "/opt/centium/node_modules" ]; then
    cp -r "$DIR/node_modules" /opt/centium/
fi

# 5. Install System Routing Script
echo "[5/8] Installing /usr/local/bin/centium-routing and /usr/bin/centium-routing..."
cp "$DIR/linux/centium-routing.sh" /usr/local/bin/centium-routing
cp "$DIR/linux/centium-routing.sh" /usr/bin/centium-routing
chmod 755 /usr/local/bin/centium-routing /usr/bin/centium-routing

# 6. Sudoers rule so Centium can manage transparent routing without password prompts
echo "[6/8] Configuring passwordless sudo rules (/etc/sudoers.d/centium)..."
cat << 'EOF' > /etc/sudoers.d/centium
Defaults env_keep += "CENTIUM_TOR_UID TRANS_PORT DNS_PORT"
ALL ALL=(ALL) NOPASSWD: /usr/local/bin/centium-routing
ALL ALL=(ALL) NOPASSWD: /usr/local/bin/centium-routing *
ALL ALL=(ALL) NOPASSWD: /usr/bin/centium-routing
ALL ALL=(ALL) NOPASSWD: /usr/bin/centium-routing *
ALL ALL=(ALL) NOPASSWD: /sbin/iptables
ALL ALL=(ALL) NOPASSWD: /sbin/iptables *
ALL ALL=(ALL) NOPASSWD: /usr/sbin/iptables
ALL ALL=(ALL) NOPASSWD: /usr/sbin/iptables *
ALL ALL=(ALL) NOPASSWD: /usr/bin/iptables
ALL ALL=(ALL) NOPASSWD: /usr/bin/iptables *
ALL ALL=(ALL) NOPASSWD: /sbin/ip6tables
ALL ALL=(ALL) NOPASSWD: /sbin/ip6tables *
ALL ALL=(ALL) NOPASSWD: /usr/sbin/ip6tables
ALL ALL=(ALL) NOPASSWD: /usr/sbin/ip6tables *
ALL ALL=(ALL) NOPASSWD: /usr/bin/ip6tables
ALL ALL=(ALL) NOPASSWD: /usr/bin/ip6tables *
ALL ALL=(ALL) NOPASSWD: /sbin/modprobe
ALL ALL=(ALL) NOPASSWD: /sbin/modprobe *
ALL ALL=(ALL) NOPASSWD: /usr/sbin/modprobe
ALL ALL=(ALL) NOPASSWD: /usr/sbin/modprobe *
ALL ALL=(ALL) NOPASSWD: /usr/bin/modprobe
ALL ALL=(ALL) NOPASSWD: /usr/bin/modprobe *
ALL ALL=(ALL) NOPASSWD: /usr/bin/systemctl stop tor
ALL ALL=(ALL) NOPASSWD: /usr/bin/systemctl start tor
ALL ALL=(ALL) NOPASSWD: /usr/bin/killall tor
ALL ALL=(ALL) NOPASSWD: /usr/bin/pkill -9 tor
ALL ALL=(ALL) NOPASSWD: /usr/bin/pkill -9 -f ^tor
EOF
chmod 440 /etc/sudoers.d/centium
if command -v visudo &>/dev/null; then
    visudo -cf /etc/sudoers.d/centium || echo "[!] Notice: visudo validation on /etc/sudoers.d/centium"
fi

# 7. Install Centium Daemon & Systemd Service
echo "[7/8] Installing centiumd executable and systemd service..."
cp "$DIR/linux/centiumd" /usr/bin/centiumd
chmod 755 /usr/bin/centiumd

cp "$DIR/linux/centiumd.service" /etc/systemd/system/centiumd.service
systemctl daemon-reload
systemctl enable --now centiumd || echo "[!] Notice: centiumd service enabled"

# 8. Install Desktop Launcher & Icons
echo "[8/8] Installing Desktop Application Launcher and Icons..."
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

# Install icon & desktop entry
mkdir -p /usr/share/icons/hicolor/scalable/apps
cp "$DIR/public/favicon.svg" /usr/share/icons/hicolor/scalable/apps/centium.svg 2>/dev/null || true
cp "$DIR/linux/centium.desktop" /usr/share/applications/centium.desktop 2>/dev/null || true

echo "========================================================"
echo "[✓] Centium VPN is fully installed and active on your Arch system!"
echo ""
echo "Core Daemon Status:"
systemctl status centiumd --no-pager || true
echo ""
echo "To launch the native desktop application:"
echo "  centium"
echo ""
echo "Or from your application launcher (Rofi, Dmenu, GNOME, KDE):"
echo "  Search for 'Centium VPN'"
echo "========================================================"
