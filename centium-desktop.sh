#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Native Linux Desktop Application Launcher
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

PORT=3000
URL="http://127.0.0.1:$PORT"

# 1. Start Centium Core server if not already active
if ! curl -s "$URL/api/health" &>/dev/null; then
    echo "[Centium] Core daemon not detected. Starting centiumd service with elevated privileges..."
    if command -v pkexec &>/dev/null; then
        pkexec systemctl start centiumd 2>/dev/null || true
    elif command -v sudo &>/dev/null; then
        sudo systemctl start centiumd 2>/dev/null || true
    elif command -v systemctl &>/dev/null; then
        systemctl start centiumd 2>/dev/null || true
    fi

    # Wait for daemon to become active
    for i in {1..20}; do
        if curl -s "$URL/api/health" &>/dev/null; then
            echo "[Centium] Core system daemon is active and healthy."
            break
        fi
        sleep 0.5
    done
fi

if ! curl -s "$URL/api/health" &>/dev/null; then
    echo "[!] Warning: Centium daemon (centiumd) is not active and requires root privileges."
    echo "    Please start the daemon with: sudo systemctl start centiumd"
    # If running as root (e.g. debugging/headless), start the local engine directly
    if [ "$(id -u)" -eq 0 ]; then
        echo "[Centium] Running as root, starting engine directly..."
        export NODE_ENV=production
        if [ -f "$DIR/dist/server.cjs" ]; then
            node "$DIR/dist/server.cjs" &
        else
            node "$DIR/dist/server.cjs" &
        fi
        SERVER_PID=$!
        trap 'kill $SERVER_PID 2>/dev/null || true' EXIT

        for i in {1..30}; do
            if curl -s "$URL/api/health" &>/dev/null; then
                echo "[Centium] Core engine ready."
                break
            fi
            sleep 0.5
        done
    fi
fi

# 2. Launch as a standalone Desktop Window (no browser tabs, isolated session)
echo "[Centium] Opening desktop application window..."
PID_CLIENT=""

cleanup_desktop() {
    if [ -n "$SERVER_PID" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
    fi
}
trap cleanup_desktop EXIT INT TERM

if command -v electron &>/dev/null; then
    electron "$DIR/electron/main.cjs"
elif npx --no-install electron -v &>/dev/null; then
    npx electron "$DIR/electron/main.cjs"
elif command -v chromium &>/dev/null; then
    chromium --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v google-chrome-stable &>/dev/null; then
    google-chrome-stable --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v google-chrome &>/dev/null; then
    google-chrome --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v brave-browser &>/dev/null; then
    brave-browser --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v brave &>/dev/null; then
    brave --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v flatpak &>/dev/null && flatpak info org.chromium.Chromium &>/dev/null; then
    flatpak run org.chromium.Chromium --app="$URL" --user-data-dir="/tmp/centium-app-profile"
else
    echo "[Centium] Opening in default viewer..."
    xdg-open "$URL"
fi
