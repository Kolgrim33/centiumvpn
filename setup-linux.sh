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
echo "[1/6] Installing core dependencies (tor, iptables, iproute2)..."
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

# 2. Setup Centium Runtime Directories
echo "[2/6] Creating runtime directories (/run/centium, /var/lib/centium)..."
mkdir -p /run/centium
mkdir -p /var/lib/centium
chmod 755 /run/centium
chmod 700 /var/lib/centium

# 3. Install System Routing Script
echo "[3/6] Installing /usr/local/bin/centium-routing..."
cp "$DIR/linux/centium-routing.sh" /usr/local/bin/centium-routing
chmod 755 /usr/local/bin/centium-routing

# 4. Sudoers rule so Centium can manage transparent routing without password prompts
echo "[4/6] Configuring passwordless sudo for routing manager (/etc/sudoers.d/centium)..."
cat << 'EOF' > /etc/sudoers.d/centium
ALL ALL=(ALL) NOPASSWD: /usr/local/bin/centium-routing enable
ALL ALL=(ALL) NOPASSWD: /usr/local/bin/centium-routing disable
ALL ALL=(ALL) NOPASSWD: /usr/local/bin/centium-routing status
EOF
chmod 440 /etc/sudoers.d/centium

# 5. Install Desktop Launcher & Icons
echo "[5/6] Installing Desktop Application Launcher..."
cat << EOF > /usr/local/bin/centium
#!/usr/bin/env bash
exec "$DIR/centium-desktop.sh" "\$@"
EOF
chmod 755 /usr/local/bin/centium
chmod +x "$DIR/centium-desktop.sh"

# Install icon & desktop entry
mkdir -p /usr/share/icons/hicolor/scalable/apps
cp "$DIR/public/favicon.svg" /usr/share/icons/hicolor/scalable/apps/centium.svg 2>/dev/null || true
cp "$DIR/linux/centium.desktop" /usr/share/applications/centium.desktop 2>/dev/null || true

# 6. Build App
echo "[6/6] Building Centium application..."
if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
    sudo -u "$REAL_USER" npm install
    sudo -u "$REAL_USER" npm run build
else
    npm install
    npm run build
fi

echo "========================================================"
echo "[✓] Centium VPN is installed and ready on your Arch system!"
echo ""
echo "How to run Centium as a native desktop application:"
echo "  • From anywhere in your terminal:  centium"
echo "  • Or from your project folder:    ./centium-desktop.sh"
echo "  • Or from your App Launcher:      search 'Centium VPN'"
echo ""
echo "To test transparent routing manually at any time:"
echo "  sudo centium-routing enable"
echo "  curl https://check.torproject.org/api/ip"
echo "  sudo centium-routing disable"
echo "========================================================"
