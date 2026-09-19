#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Native Linux Desktop Installer & Setup Script
# Supports: Arch Linux, Ubuntu, Debian, Fedora
# ==============================================================================

set -euo pipefail

echo "========================================================"
echo "          Centium VPN - Linux System Setup             "
echo "========================================================"

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "[!] Please run this script with sudo: sudo ./setup-linux.sh"
    exit 1
fi

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
echo "[1/5] Installing core dependencies (tor, iptables, iproute2)..."
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
echo "[2/5] Creating runtime directories (/run/centium, /var/lib/centium)..."
mkdir -p /run/centium
mkdir -p /var/lib/centium
chmod 755 /run/centium
chmod 700 /var/lib/centium

# 3. Install System Routing Script
echo "[3/5] Installing /usr/local/bin/centium-routing..."
cp ./linux/centium-routing.sh /usr/local/bin/centium-routing
chmod +x /usr/local/bin/centium-routing

# 4. Install Systemd Daemon Service
echo "[4/5] Installing systemd service (centiumd.service)..."
cp ./linux/centiumd.service /etc/systemd/system/centiumd.service
systemctl daemon-reload

# 5. Build Desktop Application
echo "[5/5] Building Centium application..."
npm install
npm run build

echo "========================================================"
echo "[✓] Centium VPN is installed and ready on your system!"
echo ""
echo "To launch the Centium Desktop Daemon & UI:"
echo "  1. Start the daemon:    sudo systemctl start centiumd"
echo "  2. Or run directly:     npm start"
echo "  3. To enable routing:   sudo centium-routing enable"
echo "  4. To disable routing:  sudo centium-routing disable"
echo "========================================================"
