#!/usr/bin/env bash
# Centium VPN - Transparent Tor Routing & Kill Switch Manager
set -euo pipefail

TOR_UID="${TOR_UID:-debian-tor}"
TRANS_PORT="${TRANS_PORT:-9040}"
DNS_PORT="${DNS_PORT:-5353}"
TUN_DEV="centium0"
RESOLV_BACKUP="/run/centium/resolv.conf.backup"

enable_routing() {
    echo "[Centium] Enabling system-wide routing via Tor..."
    if [ -f /etc/resolv.conf ] && [ ! -f "$RESOLV_BACKUP" ]; then
        mkdir -p /run/centium
        cp /etc/resolv.conf "$RESOLV_BACKUP"
    fi
    echo "nameserver 127.0.0.1" > /etc/resolv.conf

    # Block IPv6
    ip6tables -P INPUT DROP || true
    ip6tables -P OUTPUT DROP || true
    ip6tables -P FORWARD DROP || true
    ip6tables -A OUTPUT -o lo -j ACCEPT || true

    # NAT table setup
    iptables -t nat -N CENTIUM_NAT 2>/dev/null || iptables -t nat -F CENTIUM_NAT
    iptables -t nat -A CENTIUM_NAT -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"
    iptables -t nat -A CENTIUM_NAT -m owner --uid-owner "$TOR_UID" -j RETURN
    iptables -t nat -A CENTIUM_NAT -d 127.0.0.0/8 -j RETURN
    iptables -t nat -A CENTIUM_NAT -p tcp --syn -j REDIRECT --to-ports "$TRANS_PORT"

    iptables -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
    iptables -t nat -A OUTPUT -j CENTIUM_NAT

    # Kill switch filter
    iptables -t filter -N CENTIUM_FILTER 2>/dev/null || iptables -t filter -F CENTIUM_FILTER
    iptables -t filter -A CENTIUM_FILTER -o lo -j ACCEPT
    iptables -t filter -A CENTIUM_FILTER -m owner --uid-owner "$TOR_UID" -j ACCEPT
    iptables -t filter -A CENTIUM_FILTER -m state --state ESTABLISHED,RELATED -j ACCEPT
    iptables -t filter -A CENTIUM_FILTER -j DROP

    iptables -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
    iptables -t filter -A OUTPUT -j CENTIUM_FILTER
}

disable_routing() {
    echo "[Centium] Restoring network defaults..."
    iptables -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
    iptables -t nat -F CENTIUM_NAT 2>/dev/null || true
    iptables -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
    iptables -t filter -F CENTIUM_FILTER 2>/dev/null || true
    ip6tables -P INPUT ACCEPT 2>/dev/null || true
    ip6tables -P OUTPUT ACCEPT 2>/dev/null || true
    ip6tables -P FORWARD ACCEPT 2>/dev/null || true
    ip6tables -F 2>/dev/null || true
    if [ -f "$RESOLV_BACKUP" ]; then
        cp "$RESOLV_BACKUP" /etc/resolv.conf
        rm -f "$RESOLV_BACKUP"
    fi
}

case "${1:-}" in
    enable) enable_routing ;;
    disable) disable_routing ;;
    *) echo "Usage: $0 {enable|disable}"; exit 1 ;;
esac
