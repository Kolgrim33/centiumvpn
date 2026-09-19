#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Transparent Tor Routing & Kill Switch Manager
# Compatible with: Arch Linux (tor user), Debian/Ubuntu (debian-tor), Fedora
# ==============================================================================

set -euo pipefail

# Ensure standard system admin paths are in PATH (needed for non-interactive sudo)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${PATH:-}"

find_executable() {
    local name="$1"
    if command -v "$name" &>/dev/null; then
        command -v "$name"
        return 0
    fi
    for dir in /sbin /usr/sbin /usr/bin /bin /usr/local/sbin /usr/local/bin; do
        if [ -x "$dir/$name" ]; then
            echo "$dir/$name"
            return 0
        fi
    done
    return 1
}

IPTABLES="$(find_executable iptables || true)"
IP6TABLES="$(find_executable ip6tables || true)"

# 1. Determine Tor user / UID
if [ -n "${CENTIUM_TOR_UID:-}" ]; then
    TOR_UID="$CENTIUM_TOR_UID"
elif id "tor" &>/dev/null; then
    TOR_UID="tor"
elif id "debian-tor" &>/dev/null; then
    TOR_UID="debian-tor"
else
    TOR_UID="$(id -un)"
fi

TRANS_PORT="${TRANS_PORT:-9040}"
DNS_PORT="${DNS_PORT:-5353}"
RESOLV_BACKUP="/run/centium/resolv.conf.backup"

verify_prerequisites() {
    # Verify that Tor is actually listening on TransPort (9040) and SocksPort (9050)
    if command -v ss &>/dev/null; then
        if ! ss -tln | grep -q ":${TRANS_PORT}\b"; then
            echo "[Centium Error] Tor TransPort :${TRANS_PORT} is not listening!" >&2
            echo "[Centium Error] Refusing to enable transparent routing (prevents dead network)." >&2
            return 1
        fi
        if ! ss -tln | grep -q ":9050\b"; then
            echo "[Centium Error] Tor SocksPort :9050 is not listening!" >&2
            echo "[Centium Error] Refusing to enable transparent routing (prevents dead network)." >&2
            return 1
        fi
    fi
    return 0
}

enable_routing() {
    echo "[Centium] Verifying Tor listening ports before routing..."
    if ! verify_prerequisites; then
        exit 1
    fi

    # Explicitly verify iptables binary presence - failure must NEVER be a silent no-op
    if [ -z "$IPTABLES" ]; then
        echo "[Centium Error] iptables executable not found in PATH or standard directories (/sbin, /usr/sbin, /usr/bin, /bin)!" >&2
        echo "[Centium Error] iptables is strictly required for transparent routing and kill switch protection. Aborting." >&2
        exit 1
    fi

    echo "[Centium] Enabling system-wide routing via Tor (Tor identity: $TOR_UID, iptables: $IPTABLES)..."

    # Backup resolv.conf
    mkdir -p /run/centium
    if [ -f /etc/resolv.conf ] && [ ! -f "$RESOLV_BACKUP" ]; then
        cp /etc/resolv.conf "$RESOLV_BACKUP" 2>/dev/null || true
    fi

    # Block IPv6 leak vectors (with -w flag)
    if [ -n "$IP6TABLES" ]; then
        $IP6TABLES -w -P INPUT DROP 2>/dev/null || true
        $IP6TABLES -w -P OUTPUT DROP 2>/dev/null || true
        $IP6TABLES -w -P FORWARD DROP 2>/dev/null || true
        $IP6TABLES -w -F 2>/dev/null || true
        $IP6TABLES -w -A OUTPUT -o lo -j ACCEPT 2>/dev/null || true
    fi

    # Transparent TCP & DNS redirection via iptables NAT (with -w flag)
    $IPTABLES -w -t nat -N CENTIUM_NAT 2>/dev/null || $IPTABLES -w -t nat -F CENTIUM_NAT

    # DNS redirection to Tor DNSPort (5353)
    $IPTABLES -w -t nat -A CENTIUM_NAT -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"
    $IPTABLES -w -t nat -A CENTIUM_NAT -p tcp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"

    # Bypass Tor daemon process to prevent circular routing
    $IPTABLES -w -t nat -A CENTIUM_NAT -m owner --uid-owner "$TOR_UID" -j RETURN 2>/dev/null || true

    # Bypass loopback
    $IPTABLES -w -t nat -A CENTIUM_NAT -d 127.0.0.0/8 -j RETURN

    # Redirect all outbound TCP SYN packets to Tor TransPort (9040)
    $IPTABLES -w -t nat -A CENTIUM_NAT -p tcp --syn -j REDIRECT --to-ports "$TRANS_PORT"

    # Apply NAT chain to OUTPUT
    $IPTABLES -w -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
    $IPTABLES -w -t nat -A OUTPUT -j CENTIUM_NAT

    # Fail-closed Kill Switch filter
    $IPTABLES -w -t filter -N CENTIUM_FILTER 2>/dev/null || $IPTABLES -w -t filter -F CENTIUM_FILTER
    $IPTABLES -w -t filter -A CENTIUM_FILTER -o lo -j ACCEPT
    $IPTABLES -w -t filter -A CENTIUM_FILTER -m owner --uid-owner "$TOR_UID" -j ACCEPT 2>/dev/null || true
    $IPTABLES -w -t filter -A CENTIUM_FILTER -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || \
        $IPTABLES -w -t filter -m state --state ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || true
    $IPTABLES -w -t filter -A CENTIUM_FILTER -j DROP

    $IPTABLES -w -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
    $IPTABLES -w -t filter -A OUTPUT -j CENTIUM_FILTER

    # Point system DNS to local Tor resolver now that NAT redirection to DNS_PORT is active
    echo "nameserver 127.0.0.1" > /etc/resolv.conf 2>/dev/null || true

    # Post-condition verification: check that CENTIUM_NAT and CENTIUM_FILTER exist and are hooked into OUTPUT
    echo "[Centium] Performing post-routing iptables rule audit..."
    local audit_failed=0
    if ! $IPTABLES -w -t nat -C OUTPUT -j CENTIUM_NAT 2>/dev/null; then
        echo "[Centium Error] Post-condition audit failed: CENTIUM_NAT is not active in nat OUTPUT!" >&2
        audit_failed=1
    fi
    if ! $IPTABLES -w -t filter -C OUTPUT -j CENTIUM_FILTER 2>/dev/null; then
        echo "[Centium Error] Post-condition audit failed: CENTIUM_FILTER is not active in filter OUTPUT!" >&2
        audit_failed=1
    fi

    if [ "$audit_failed" -ne 0 ]; then
        echo "[Centium Error] Routing verification failed. Reverting all changes to prevent dead network..." >&2
        disable_routing
        exit 1
    fi

    echo "[Centium] System-wide Tor transparent routing and kill switch activated."
}

disable_routing() {
    echo "[Centium] Restoring network defaults..."

    if [ -n "$IPTABLES" ]; then
        $IPTABLES -w -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
        $IPTABLES -w -t nat -F CENTIUM_NAT 2>/dev/null || true
        $IPTABLES -w -t nat -X CENTIUM_NAT 2>/dev/null || true

        $IPTABLES -w -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
        $IPTABLES -w -t filter -F CENTIUM_FILTER 2>/dev/null || true
        $IPTABLES -w -t filter -X CENTIUM_FILTER 2>/dev/null || true
    fi

    if [ -n "$IP6TABLES" ]; then
        $IP6TABLES -w -P INPUT ACCEPT 2>/dev/null || true
        $IP6TABLES -w -P OUTPUT ACCEPT 2>/dev/null || true
        $IP6TABLES -w -P FORWARD ACCEPT 2>/dev/null || true
        $IP6TABLES -w -F 2>/dev/null || true
    fi

    if [ -f "$RESOLV_BACKUP" ]; then
        cp "$RESOLV_BACKUP" /etc/resolv.conf 2>/dev/null || true
        rm -f "$RESOLV_BACKUP" 2>/dev/null || true
    fi

    echo "[Centium] Normal network routing restored."
}

status_routing() {
    echo "=== Centium NAT Rules ==="
    if [ -n "$IPTABLES" ]; then
        $IPTABLES -w -t nat -L CENTIUM_NAT -v -n 2>/dev/null || echo "No CENTIUM_NAT chain active"
        echo ""
        echo "=== Centium Kill Switch Rules ==="
        $IPTABLES -w -t filter -L CENTIUM_FILTER -v -n 2>/dev/null || echo "No CENTIUM_FILTER chain active"
        echo ""
    else
        echo "iptables not found"
    fi
    echo "=== Active Tor Ports ==="
    if command -v ss &>/dev/null; then
        ss -lntup | grep -E "${TRANS_PORT}|${DNS_PORT}|9050" || echo "No Tor ports found listening"
    fi
}

case "${1:-}" in
    enable) enable_routing ;;
    disable) disable_routing ;;
    status) status_routing ;;
    *) echo "Usage: $0 {enable|disable|status}"; exit 1 ;;
esac
