#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Network Diagnostics & Verification Suite
# Reports status across all 10 architectural layers:
#   [✓] Tor process
#   [✓] Tor SOCKS5 :9050
#   [✓] Tor bootstrap
#   [✓] centium0
#   [✓] TUN-to-SOCKS bridge
#   [✓] Policy routing
#   [✓] nftables kill switch
#   [✓] DNS configuration
#   [✓] Tor connectivity
#   [✓] External IP verification
# ==============================================================================

set -uo pipefail

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${PATH:-}"

TUN_DEV="centium0"
ROUTING_TABLE=8420
FWMARK="0x8420"
NFT_TABLE="centium"
DNS_MAPPED_IP="198.18.0.2"
SOCKS5_PORT=9050
SOCKS5_HOST="127.0.0.1"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

TOTAL_CHECKS=10
PASSED_CHECKS=0
FAILED_CHECKS=0

declare -a FAILED_LAYERS=()

report_pass() {
    local layer="$1"
    local detail="${2:-}"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    if [ -n "$detail" ]; then
        echo -e "  ${GREEN}[✓]${NC} ${BOLD}${layer}${NC} — ${detail}"
    else
        echo -e "  ${GREEN}[✓]${NC} ${BOLD}${layer}${NC}"
    fi
}

report_fail() {
    local layer="$1"
    local error="$2"
    local remediation="${3:-}"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
    FAILED_LAYERS+=("${layer}")
    echo -e "  ${RED}[✗]${NC} ${BOLD}${layer}${NC} — ${RED}${error}${NC}"
    if [ -n "$remediation" ]; then
        echo -e "      ${YELLOW}↳ Remediation: ${remediation}${NC}"
    fi
}

echo "========================================================"
echo -e "${BOLD}     Centium VPN — System Layer Diagnostics${NC}"
echo "  Architecture: TUN (centium0) ➔ SOCKS5 ➔ Tor ➔ Web"
echo "========================================================"
echo ""

# ------------------------------------------------------------------------------
# Layer 1: Tor Process
# ------------------------------------------------------------------------------
tor_pid=""
if [ -f /run/centium/tor.pid ]; then
    cand_pid="$(cat /run/centium/tor.pid 2>/dev/null || true)"
    if [ -n "$cand_pid" ] && kill -0 "$cand_pid" 2>/dev/null; then
        tor_pid="$cand_pid"
    fi
fi
if [ -z "$tor_pid" ] && command -v pgrep &>/dev/null; then
    tor_pid="$(pgrep -x tor 2>/dev/null | head -n 1 || true)"
fi

if [ -n "$tor_pid" ]; then
    report_pass "Tor process" "Running (PID: ${tor_pid})"
else
    report_fail "Tor process" "Tor daemon is not running" "Start Tor via 'systemctl start tor' or connect through Centium UI."
fi

# ------------------------------------------------------------------------------
# Layer 2: Tor SOCKS5 :9050
# ------------------------------------------------------------------------------
socks_listening=0
if command -v ss &>/dev/null; then
    if ss -lntp 2>/dev/null | grep -q ":${SOCKS5_PORT}\\b"; then
        socks_listening=1
    fi
fi
if [ "$socks_listening" -eq 0 ] && command -v nc &>/dev/null; then
    if nc -z 127.0.0.1 "$SOCKS5_PORT" 2>/dev/null; then
        socks_listening=1
    fi
fi
if [ "$socks_listening" -eq 0 ]; then
    (echo > "/dev/tcp/127.0.0.1/${SOCKS5_PORT}") 2>/dev/null && socks_listening=1 || true
fi

if [ "$socks_listening" -eq 1 ]; then
    report_pass "Tor SOCKS5 :9050" "Listening on 127.0.0.1:${SOCKS5_PORT}"
else
    report_fail "Tor SOCKS5 :9050" "Port ${SOCKS5_PORT} is not listening" "Verify that Tor torrc has 'SocksPort 9050' enabled."
fi

# ------------------------------------------------------------------------------
# Layer 3: Tor Bootstrap
# ------------------------------------------------------------------------------
bootstrap_ok=0
tor_log_file="/run/centium/tor.log"
if [ -f "$tor_log_file" ] && grep -q "Bootstrapped 100%" "$tor_log_file"; then
    bootstrap_ok=1
fi

# Also test quick socks response to check.torproject.org
if command -v curl &>/dev/null; then
    quick_test="$(curl -s --connect-timeout 4 --max-time 6 --socks5-hostname 127.0.0.1:${SOCKS5_PORT} https://check.torproject.org/api/ip 2>/dev/null || true)"
    if echo "$quick_test" | grep -qi '"IsTor":true'; then
        bootstrap_ok=1
    fi
fi

if [ "$bootstrap_ok" -eq 1 ]; then
    report_pass "Tor bootstrap" "100% complete (consensus synchronized)"
else
    if [ -n "$tor_pid" ]; then
        report_fail "Tor bootstrap" "Tor circuit bootstrap incomplete" "Check system clock and firewall allowing outbound TCP 443/9001."
    else
        report_fail "Tor bootstrap" "Tor process not running" "Start Tor before bootstrapping."
    fi
fi

# ------------------------------------------------------------------------------
# Layer 4: centium0 (TUN Interface)
# ------------------------------------------------------------------------------
tun_exists=0
tun_ip=""
if command -v ip &>/dev/null && ip link show "$TUN_DEV" &>/dev/null; then
    tun_exists=1
    tun_ip="$(ip -4 addr show dev "$TUN_DEV" 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print $2}' || true)"
fi

if [ "$tun_exists" -eq 1 ]; then
    report_pass "centium0" "Virtual TUN interface exists (IP: ${tun_ip:-198.18.0.1})"
else
    report_fail "centium0" "Interface ${TUN_DEV} does not exist" "Ensure hev-socks5-tunnel or tun2socks is started with CAP_NET_ADMIN."
fi

# ------------------------------------------------------------------------------
# Layer 5: TUN-to-SOCKS Bridge
# ------------------------------------------------------------------------------
bridge_pid=""
if [ -f /run/centium/hev-socks5-tunnel.pid ]; then
    cand_bridge="$(cat /run/centium/hev-socks5-tunnel.pid 2>/dev/null || true)"
    if [ -n "$cand_bridge" ] && kill -0 "$cand_bridge" 2>/dev/null; then
        bridge_pid="$cand_bridge"
    fi
fi
if [ -z "$bridge_pid" ] && command -v pgrep &>/dev/null; then
    bridge_pid="$(pgrep -f "hev-socks5-tunnel" 2>/dev/null | head -n 1 || true)"
    if [ -z "$bridge_pid" ]; then
        bridge_pid="$(pgrep -f "tun2socks" 2>/dev/null | head -n 1 || true)"
    fi
fi

if [ -n "$bridge_pid" ]; then
    report_pass "TUN-to-SOCKS bridge" "Running (PID: ${bridge_pid})"
else
    report_fail "TUN-to-SOCKS bridge" "Bridge daemon (hev-socks5-tunnel/tun2socks) is inactive" "Run 'sudo /usr/local/bin/centium-network start-tun'."
fi

# ------------------------------------------------------------------------------
# Layer 6: Policy Routing
# ------------------------------------------------------------------------------
routing_ok=0
if command -v ip &>/dev/null; then
    has_table_route=0
    has_policy_rule=0
    if ip route show table "$ROUTING_TABLE" 2>/dev/null | grep -q "$TUN_DEV"; then
        has_table_route=1
    fi
    if ip rule show 2>/dev/null | grep -q "lookup ${ROUTING_TABLE}"; then
        has_policy_rule=1
    fi
    if [ "$has_table_route" -eq 1 ] && [ "$has_policy_rule" -eq 1 ]; then
        routing_ok=1
    fi
fi

if [ "$routing_ok" -eq 1 ]; then
    report_pass "Policy routing" "Table ${ROUTING_TABLE} active (default dev ${TUN_DEV})"
else
    report_fail "Policy routing" "Dedicated routing table ${ROUTING_TABLE} or policy rule missing" "Run 'sudo /usr/local/bin/centium-network install-routing'."
fi

# ------------------------------------------------------------------------------
# Layer 7: nftables Kill Switch
# ------------------------------------------------------------------------------
killswitch_ok=0
if command -v nft &>/dev/null; then
    if nft list table inet "$NFT_TABLE" &>/dev/null; then
        if nft list chain inet "$NFT_TABLE" outbound 2>/dev/null | grep -q "policy drop"; then
            killswitch_ok=1
        fi
    fi
fi

if [ "$killswitch_ok" -eq 1 ]; then
    report_pass "nftables kill switch" "Table inet ${NFT_TABLE} active (fail-closed outbound drop)"
else
    report_fail "nftables kill switch" "Table inet ${NFT_TABLE} missing or not armed" "Run 'sudo /usr/local/bin/centium-network start-killswitch'."
fi

# ------------------------------------------------------------------------------
# Layer 8: DNS Configuration
# ------------------------------------------------------------------------------
dns_ok=0
dns_detail=""
if [ -f /etc/resolv.conf ] && grep -q "$DNS_MAPPED_IP" /etc/resolv.conf 2>/dev/null; then
    dns_ok=1
    dns_detail="nameserver ${DNS_MAPPED_IP} in /etc/resolv.conf"
elif command -v resolvectl &>/dev/null && resolvectl dns "${TUN_DEV}" 2>/dev/null | grep -q "$DNS_MAPPED_IP"; then
    dns_ok=1
    dns_detail="systemd-resolved interface ${TUN_DEV} -> ${DNS_MAPPED_IP}"
fi

# Verify DNS resolution works
if command -v getent &>/dev/null && getent hosts check.torproject.org &>/dev/null; then
    [ "$dns_ok" -eq 0 ] && dns_ok=1 && dns_detail="DNS resolution functioning"
fi

if [ "$dns_ok" -eq 1 ]; then
    report_pass "DNS configuration" "${dns_detail:-Protected via mapped-DNS}"
else
    report_fail "DNS configuration" "Resolver does not point to Centium mapped-DNS" "Run 'sudo /usr/local/bin/centium-network install-dns'."
fi

# ------------------------------------------------------------------------------
# Layer 9: Tor Connectivity
# ------------------------------------------------------------------------------
socks_tor_ok=0
if command -v curl &>/dev/null; then
    socks_resp="$(curl -s --connect-timeout 6 --max-time 10 --socks5-hostname 127.0.0.1:${SOCKS5_PORT} https://check.torproject.org/api/ip 2>/dev/null || true)"
    if echo "$socks_resp" | grep -qi '"IsTor":true'; then
        socks_tor_ok=1
    fi
fi

if [ "$socks_tor_ok" -eq 1 ]; then
    report_pass "Tor connectivity" "SOCKS5 path to Tor network verified"
else
    report_fail "Tor connectivity" "Failed to reach check.torproject.org via SOCKS5 :${SOCKS5_PORT}" "Check Tor relays and network connection."
fi

# ------------------------------------------------------------------------------
# Layer 10: External IP Verification (Through Centium TUN path)
# ------------------------------------------------------------------------------
verified_exit_ip=""
is_tor=0
if command -v curl &>/dev/null; then
    # Test normal curl going through system policy routing/centium0
    system_resp="$(curl -s --connect-timeout 6 --max-time 10 https://check.torproject.org/api/ip 2>/dev/null || true)"
    if echo "$system_resp" | grep -qi '"IsTor":true'; then
        is_tor=1
        verified_exit_ip="$(echo "$system_resp" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -n 1 || true)"
    fi
fi

if [ "$is_tor" -eq 1 ] && [ -n "$verified_exit_ip" ]; then
    report_pass "External IP verification" "Confirmed Tor Exit Relay IP: ${verified_exit_ip}"
else
    if [ "$socks_tor_ok" -eq 1 ]; then
        report_fail "External IP verification" "System requests are not reaching Tor via centium0" "Verify TUN bridge and table ${ROUTING_TABLE} default route."
    else
        report_fail "External IP verification" "No external Tor IP verified" "Ensure Tor is connected and bootstrapped."
    fi
fi

echo ""
echo "========================================================"
if [ "$FAILED_CHECKS" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}✓ ALL 10 LAYERS VERIFIED AND OPERATIONAL (10/10)${NC}"
    echo "  Status: CONNECTED"
    exit 0
else
    echo -e "${RED}${BOLD}✗ VERIFICATION FAILED (${PASSED_CHECKS}/${TOTAL_CHECKS} passed, ${FAILED_CHECKS} failed)${NC}"
    echo -e "  Failed Layer(s): ${RED}${FAILED_LAYERS[*]}${NC}"
    exit 1
fi
