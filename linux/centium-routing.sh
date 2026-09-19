#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Transparent Tor Routing & Kill Switch Manager
# Compatible with: Arch Linux (tor user), Debian/Ubuntu (debian-tor), Fedora
# ==============================================================================

set -euo pipefail

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
    # Verify that Tor is actually listening on TransPort (9040) and DNSPort (5353)
    if command -v ss &>/dev/null; then
        if ! ss -tln | grep -q ":${TRANS_PORT}\b"; then
            echo "[Centium Error] Tor TransPort :${TRANS_PORT} is not listening!" >&2
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

    echo "[Centium] Enabling system-wide routing via Tor (Tor identity: $TOR_UID)..."

    # Backup resolv.conf
    mkdir -p /run/centium
    if [ -f /etc/resolv.conf ] && [ ! -f "$RESOLV_BACKUP" ]; then
        cp /etc/resolv.conf "$RESOLV_BACKUP" 2>/dev/null || true
    fi

    # Point system DNS to local Tor resolver
    echo "nameserver 127.0.0.1" > /etc/resolv.conf 2>/dev/null || true

    # Block IPv6 leak vectors
    if command -v ip6tables &>/dev/null; then
        ip6tables -P INPUT DROP 2>/dev/null || true
        ip6tables -P OUTPUT DROP 2>/dev/null || true
        ip6tables -P FORWARD DROP 2>/dev/null || true
        ip6tables -F 2>/dev/null || true
        ip6tables -A OUTPUT -o lo -j ACCEPT 2>/dev/null || true
    fi

    # Transparent TCP & DNS redirection via iptables NAT
    if command -v iptables &>/dev/null; then
        iptables -t nat -N CENTIUM_NAT 2>/dev/null || iptables -t nat -F CENTIUM_NAT

        # DNS redirection to Tor DNSPort (5353)
        iptables -t nat -A CENTIUM_NAT -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"
        iptables -t nat -A CENTIUM_NAT -p tcp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"

        # Bypass Tor daemon process to prevent circular routing
        iptables -t nat -A CENTIUM_NAT -m owner --uid-owner "$TOR_UID" -j RETURN 2>/dev/null || true

        # Bypass loopback
        iptables -t nat -A CENTIUM_NAT -d 127.0.0.0/8 -j RETURN

        # Redirect all outbound TCP SYN packets to Tor TransPort (9040)
        iptables -t nat -A CENTIUM_NAT -p tcp --syn -j REDIRECT --to-ports "$TRANS_PORT"

        # Apply NAT chain to OUTPUT
        iptables -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
        iptables -t nat -A OUTPUT -j CENTIUM_NAT

        # Fail-closed Kill Switch filter
        iptables -t filter -N CENTIUM_FILTER 2>/dev/null || iptables -t filter -F CENTIUM_FILTER
        iptables -t filter -A CENTIUM_FILTER -o lo -j ACCEPT
        iptables -t filter -A CENTIUM_FILTER -m owner --uid-owner "$TOR_UID" -j ACCEPT 2>/dev/null || true
        iptables -t filter -A CENTIUM_FILTER -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || \
            iptables -t filter -A CENTIUM_FILTER -m state --state ESTABLISHED,RELATED -j ACCEPT 2>/dev/null || true
        iptables -t filter -A CENTIUM_FILTER -j DROP

        iptables -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
        iptables -t filter -A OUTPUT -j CENTIUM_FILTER
    fi

    echo "[Centium] System-wide Tor transparent routing and kill switch activated."
}

disable_routing() {
    echo "[Centium] Restoring network defaults..."

    if command -v iptables &>/dev/null; then
        iptables -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
        iptables -t nat -F CENTIUM_NAT 2>/dev/null || true
        iptables -t nat -X CENTIUM_NAT 2>/dev/null || true

        iptables -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
        iptables -t filter -F CENTIUM_FILTER 2>/dev/null || true
        iptables -t filter -X CENTIUM_FILTER 2>/dev/null || true
    fi

    if command -v ip6tables &>/dev/null; then
        ip6tables -P INPUT ACCEPT 2>/dev/null || true
        ip6tables -P OUTPUT ACCEPT 2>/dev/null || true
        ip6tables -P FORWARD ACCEPT 2>/dev/null || true
        ip6tables -F 2>/dev/null || true
    fi

    if [ -f "$RESOLV_BACKUP" ]; then
        cp "$RESOLV_BACKUP" /etc/resolv.conf 2>/dev/null || true
        rm -f "$RESOLV_BACKUP" 2>/dev/null || true
    fi

    echo "[Centium] Normal network routing restored."
}

status_routing() {
    echo "=== Centium NAT Rules ==="
    iptables -t nat -L CENTIUM_NAT -v -n 2>/dev/null || echo "No CENTIUM_NAT chain active"
    echo ""
    echo "=== Centium Kill Switch Rules ==="
    iptables -t filter -L CENTIUM_FILTER -v -n 2>/dev/null || echo "No CENTIUM_FILTER chain active"
    echo ""
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
