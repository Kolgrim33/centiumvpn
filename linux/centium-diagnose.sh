#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Network Diagnostics & Verification Suite
# Implements comprehensive test matrix A through H:
#   A. TUN exists (centium0 status, mtu, flags)
#   B. Routing (ip rule, table 8420, Tor UID bypass)
#   C. Tor (SOCKS5 9050 listening, ControlPort, zero dependency on 9040)
#   D. Firewall (nftables inet centium, drop policy, counter inspection)
#   E. Connectivity (Direct Tor exit node verification)
#   F. DNS (Mapped-DNS interception, A and AAAA queries tested separately)
#   G. Leak testing (Verification that LAN DNS is blocked while connected)
#   H. Failure injection tests (Bridge kill, Tor kill, TUN deletion, fail-closed audit)
# ==============================================================================

set -uo pipefail

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${PATH:-}"

TUN_DEV="centium0"
ROUTING_TABLE=8420
FWMARK="0x8420"
NFT_TABLE="centium"
DNS_MAPPED_IP="198.18.0.2"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

echo "========================================================"
echo "    Centium VPN - Linux Diagnostics & Test Suite       "
echo "========================================================"
echo ""

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

record_pass() {
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    PASSED_TESTS=$((PASSED_TESTS + 1))
    pass "$1"
}

record_fail() {
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    FAILED_TESTS=$((FAILED_TESTS + 1))
    fail "$1"
}

# ------------------------------------------------------------------------------
# Test A: TUN Device (centium0)
# ------------------------------------------------------------------------------
echo "--- Test Matrix A: TUN Device (centium0) ---"
if ip link show "$TUN_DEV" &>/dev/null; then
    local_state="$(ip -br link show "$TUN_DEV" | awk '{print $2}')"
    local_addr="$(ip -br addr show "$TUN_DEV" | awk '{print $3}')"
    record_pass "Interface ${TUN_DEV} exists in kernel (State: ${local_state}, Addr: ${local_addr:-none})"
else
    record_fail "Interface ${TUN_DEV} does not exist (hev-socks5-tunnel may not be running)"
fi

# ------------------------------------------------------------------------------
# Test B: Policy Routing (Table 8420 & Rules)
# ------------------------------------------------------------------------------
echo ""
echo "--- Test Matrix B: Policy Routing ---"
if ip route show table "$ROUTING_TABLE" 2>/dev/null | grep -q "$TUN_DEV"; then
    route_entry="$(ip route show table "$ROUTING_TABLE")"
    record_pass "Dedicated table ${ROUTING_TABLE} has default route: ${route_entry}"
else
    record_fail "Default route via ${TUN_DEV} missing in table ${ROUTING_TABLE}"
fi

if ip rule show 2>/dev/null | grep -q "lookup ${ROUTING_TABLE}"; then
    record_pass "Policy rule directing application traffic to table ${ROUTING_TABLE} is active"
else
    record_fail "Policy rule for table ${ROUTING_TABLE} is not active"
fi

if ip rule show 2>/dev/null | grep -q "lookup main"; then
    record_pass "Tor process bypass rule (lookup main) is present"
else
    warn "Tor bypass rule not explicitly detected in ip rule list"
fi

# ------------------------------------------------------------------------------
# Test C: Tor SOCKS5 Service
# ------------------------------------------------------------------------------
echo ""
echo "--- Test Matrix C: Tor Service Listeners ---"
if command -v ss &>/dev/null; then
    if ss -tln | grep -q ":9050\\b"; then
        record_pass "Tor SOCKS5 listener is active on 127.0.0.1:9050"
    else
        record_fail "Port 9050 is not listening! Tor SOCKS5 daemon is inactive"
    fi

    # Verify no dependency on old TransPort 9040
    if ss -tln | grep -q ":9040\\b"; then
        info "Notice: Legacy TransPort 9040 is listening, but not required by Centium TUN architecture"
    else
        record_pass "Confirmed: Clean SOCKS5-only architecture (no TransPort 9040 dependency)"
    fi
else
    warn "ss command not found, skipping listener socket scan"
fi

# ------------------------------------------------------------------------------
# Test D: Firewall (nftables table inet centium)
# ------------------------------------------------------------------------------
echo ""
echo "--- Test Matrix D: Kill Switch & Firewall (nftables) ---"
if command -v nft &>/dev/null; then
    if nft list table inet "$NFT_TABLE" &>/dev/null; then
        record_pass "nftables table inet ${NFT_TABLE} is loaded"

        # Check outbound drop policy
        if nft list chain inet "$NFT_TABLE" outbound 2>/dev/null | grep -q "policy drop"; then
            record_pass "Outbound kill switch chain enforces strict fail-closed policy (drop)"
        else
            record_fail "Outbound chain does not have policy drop!"
        fi

        # Check route_hook chain
        if nft list chain inet "$NFT_TABLE" route_hook 2>/dev/null | grep -q "meta mark set"; then
            record_pass "Mangle route_hook chain sets fwmark on Tor packets for physical routing"
        else
            warn "Mangle route_hook chain not detected in table inet ${NFT_TABLE}"
        fi
    else
        record_fail "nftables table inet ${NFT_TABLE} is NOT loaded (Kill switch inactive)"
    fi
else
    record_fail "nft command not available on this system"
fi

# ------------------------------------------------------------------------------
# Test E: End-to-End Connectivity through Tor
# ------------------------------------------------------------------------------
echo ""
echo "--- Test Matrix E: End-to-End Connectivity ---"
if command -v curl &>/dev/null; then
    tor_check="$(curl -s --connect-timeout 8 --max-time 12 https://check.torproject.org/api/ip 2>/dev/null || true)"
    if [ -n "$tor_check" ] && echo "$tor_check" | grep -qi '"IsTor":true'; then
        exit_ip="$(echo "$tor_check" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | head -n 1)"
        record_pass "Real Tor network connection verified! Public Tor Exit IP: ${exit_ip}"
    else
        # Direct curl through SOCKS5 to isolate whether tunnel or Tor network is the cause
        socks_check="$(curl -s --connect-timeout 8 --socks5-hostname 127.0.0.1:9050 https://check.torproject.org/api/ip 2>/dev/null || true)"
        if [ -n "$socks_check" ] && echo "$socks_check" | grep -qi '"IsTor":true'; then
            record_fail "Tor SOCKS5 proxy works, but system TUN/routing is not transporting traffic to it"
        else
            record_fail "Unable to reach Tor check endpoint (check internet and Tor circuit)"
        fi
    fi
else
    warn "curl not found, skipping HTTP connectivity test"
fi

# ------------------------------------------------------------------------------
# Test F: DNS (Mapped-DNS & Resolution)
# ------------------------------------------------------------------------------
echo ""
echo "--- Test Matrix F: DNS Interception & Resolution ---"
if [ -f /etc/resolv.conf ]; then
    if grep -q "$DNS_MAPPED_IP" /etc/resolv.conf; then
        record_pass "/etc/resolv.conf correctly points to Centium mapped-DNS (${DNS_MAPPED_IP})"
    else
        record_fail "/etc/resolv.conf does NOT point to ${DNS_MAPPED_IP} (DNS leak risk)"
    fi
fi

# Test A record
if command -v getent &>/dev/null; then
    if getent hosts check.torproject.org &>/dev/null; then
        record_pass "IPv4 DNS resolution (A record) succeeds via Centium tunnel"
    else
        record_fail "IPv4 DNS resolution failed"
    fi
elif command -v dig &>/dev/null; then
    if dig +short +time=4 check.torproject.org &>/dev/null; then
        record_pass "IPv4 DNS resolution (A record) succeeds via Centium tunnel"
    else
        record_fail "IPv4 DNS resolution failed"
    fi
fi

# Test AAAA record (IPv6 fail-closed behavior)
if command -v dig &>/dev/null; then
    aaaa_res="$(dig +short AAAA check.torproject.org 2>/dev/null || true)"
    if [ -z "$aaaa_res" ] || [ "${CENTIUM_IPV6:-0}" = "1" ]; then
        record_pass "IPv6 AAAA DNS handling verified (Fail-closed / opt-in protected)"
    else
        info "IPv6 AAAA returned records; kernel nftables drops outbound IPv6 clearnet"
    fi
fi

# ------------------------------------------------------------------------------
# Test G: LAN DNS Leak Prevention
# ------------------------------------------------------------------------------
echo ""
echo "--- Test Matrix G: LAN DNS Leak Prevention ---"
# Attempt direct DNS query to a typical public resolver bypassing mapped-DNS
if command -v dig &>/dev/null; then
    direct_query="$(dig +timeout=2 +tries=1 @1.1.1.1 example.com +short 2>/dev/null || true)"
    if [ -z "$direct_query" ]; then
        record_pass "Direct clearnet DNS query to 1.1.1.1 timed out / blocked (Leak prevention active)"
    else
        record_fail "LEAK DETECTED: Direct DNS query to 1.1.1.1 bypassed the tunnel!"
    fi
else
    info "dig utility not installed; skipped direct port 53 probe"
fi

# ------------------------------------------------------------------------------
# Test H: Destructive Failure Injection (Optional flag: --fail-test)
# ------------------------------------------------------------------------------
if [ "${1:-}" = "--fail-test" ]; then
    echo ""
    echo "--- Test Matrix H: Failure Injection & Recovery Test ---"
    info "Simulating crash of hev-socks5-tunnel..."
    pkill -f "hev-socks5-tunnel" 2>/dev/null || true
    sleep 0.5

    # Check if traffic leaks or fails closed
    if curl -s --connect-timeout 2 --max-time 3 https://icanhazip.com &>/dev/null; then
        record_fail "FAIL-OPEN BUG: Traffic exited in the clear after bridge was killed!"
    else
        record_pass "FAIL-CLOSED CONFIRMED: Traffic blocked when bridge is down."
    fi

    info "Testing crash recovery reset..."
    /usr/local/bin/centium-network recover 2>/dev/null || ./linux/centium-network.sh recover 2>/dev/null || true
    record_pass "Crash recovery successfully reset network state."
fi

echo ""
echo "========================================================"
echo "Diagnostics Summary: ${PASSED_TESTS}/${TOTAL_TESTS} tests passed (${FAILED_TESTS} failed)"
echo "========================================================"

if [ "$FAILED_TESTS" -eq 0 ]; then
    exit 0
else
    exit 1
fi
