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
    echo "[Centium] Starting local daemon engine on port $PORT..."
    npm start &
    SERVER_PID=$!
    trap 'kill $SERVER_PID 2>/dev/null || true' EXIT

    # Wait for server ready
    for i in {1..30}; do
        if curl -s "$URL/api/health" &>/dev/null; then
            echo "[Centium] Core engine ready."
            break
        fi
        sleep 0.5
    done
fi

# 2. Launch as a standalone Desktop Window (no browser tabs, isolated session)
echo "[Centium] Opening desktop application window..."

if command -v electron &>/dev/null; then
    exec electron "$DIR/electron/main.cjs"
elif npx --no-install electron -v &>/dev/null; then
    exec npx electron "$DIR/electron/main.cjs"
elif command -v chromium &>/dev/null; then
    exec chromium --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v google-chrome-stable &>/dev/null; then
    exec google-chrome-stable --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v google-chrome &>/dev/null; then
    exec google-chrome --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v brave-browser &>/dev/null; then
    exec brave-browser --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v brave &>/dev/null; then
    exec brave --app="$URL" --user-data-dir="/tmp/centium-app-profile" --class="CentiumVPN" --window-size=960,680
elif command -v flatpak &>/dev/null && flatpak info org.chromium.Chromium &>/dev/null; then
    exec flatpak run org.chromium.Chromium --app="$URL" --user-data-dir="/tmp/centium-app-profile"
else
    echo "[Centium] Opening in default viewer..."
    xdg-open "$URL"
fi
