#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Transparent Tor Routing & Kill Switch Manager
# Compatible with: Arch Linux (tor user), Debian/Ubuntu (debian-tor), Fedora
# ==============================================================================

set -eo pipefail

# Ensure standard system admin paths are in PATH (needed for non-interactive sudo)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${PATH:-}"

find_executable() {
    local name="$1"
    if command -v "$name" &>/dev/null; then
        command -v "$name"
        return 0
    fi
    for dir in /usr/local/sbin /usr/local/bin /usr/sbin /usr/bin /sbin /bin; do
        if [ -x "$dir/$name" ]; then
            echo "$dir/$name"
            return 0
        fi
    done
    return 1
}

IPTABLES="$(find_executable iptables || true)"
IP6TABLES="$(find_executable ip6tables || true)"

# 1. Determine Tor user / UID (support env vars or positional arguments)
if [ -n "${CENTIUM_TOR_UID:-}" ]; then
    TOR_UID="$CENTIUM_TOR_UID"
elif [ -n "${2:-}" ] && [ "${1:-}" = "enable" ]; then
    TOR_UID="$2"
elif id "tor" &>/dev/null; then
    TOR_UID="tor"
elif id "debian-tor" &>/dev/null; then
    TOR_UID="debian-tor"
else
    TOR_UID="$(id -un)"
fi

# Resolve to numeric UID if possible (more reliable for iptables -m owner)
if id -u "$TOR_UID" &>/dev/null; then
    NUMERIC_TOR_UID="$(id -u "$TOR_UID")"
elif [[ "$TOR_UID" =~ ^[0-9]+$ ]]; then
    NUMERIC_TOR_UID="$TOR_UID"
else
    NUMERIC_TOR_UID="$TOR_UID"
fi

if [ -n "${3:-}" ] && [ "${1:-}" = "enable" ]; then
    TRANS_PORT="$3"
else
    TRANS_PORT="${TRANS_PORT:-9040}"
fi

if [ -n "${4:-}" ] && [ "${1:-}" = "enable" ]; then
    DNS_PORT="$4"
else
    DNS_PORT="${DNS_PORT:-5353}"
fi

RESOLV_BACKUP="/run/centium/resolv.conf.backup"

load_kernel_modules() {
    local modprobe_bin
    modprobe_bin="$(find_executable modprobe || true)"
    if [ -n "$modprobe_bin" ]; then
        echo "[Centium] Probing netfilter kernel modules (nat, redirect, owner, conntrack)..."
        for mod in ip_tables iptable_filter iptable_nat nf_nat xt_REDIRECT xt_owner xt_conntrack xt_tcpudp; do
            "$modprobe_bin" "$mod" 2>/dev/null || true
        done
    fi
}

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

backup_dns() {
    mkdir -p /run/centium
    if [ -f /etc/resolv.conf ]; then
        # Only backup if resolv.conf is NOT already pointing to 127.0.0.1
        if ! grep -qE '^nameserver[[:space:]]+127\.0\.0\.1' /etc/resolv.conf 2>/dev/null; then
            echo "[Centium] Backing up /etc/resolv.conf to $RESOLV_BACKUP..."
            cp -L /etc/resolv.conf "$RESOLV_BACKUP" 2>/dev/null || cp /etc/resolv.conf "$RESOLV_BACKUP" 2>/dev/null || true
        fi
    fi
}

restore_dns() {
    if [ -f "$RESOLV_BACKUP" ]; then
        if grep -qE '^nameserver[[:space:]]+' "$RESOLV_BACKUP" 2>/dev/null && ! grep -qE '^nameserver[[:space:]]+127\.0\.0\.1' "$RESOLV_BACKUP" 2>/dev/null; then
            echo "[Centium] Restoring /etc/resolv.conf from backup..."
            cp "$RESOLV_BACKUP" /etc/resolv.conf 2>/dev/null || true
            rm -f "$RESOLV_BACKUP" 2>/dev/null || true
            return 0
        fi
        rm -f "$RESOLV_BACKUP" 2>/dev/null || true
    fi

    # Fallback to standard public DNS if backup was missing or only contained 127.0.0.1
    echo "[Centium] Ensuring functional DNS resolvers in /etc/resolv.conf..."
    cat << 'EOF' > /etc/resolv.conf 2>/dev/null || true
nameserver 1.1.1.1
nameserver 8.8.8.8
EOF
}

ensure_chain() {
    local table="$1"
    local chain="$2"
    if $IPTABLES -w -t "$table" -L "$chain" -n &>/dev/null; then
        echo "[Centium] Flushing existing $table chain '$chain'..."
        $IPTABLES -w -t "$table" -F "$chain"
    else
        echo "[Centium] Creating $table chain '$chain'..."
        $IPTABLES -w -t "$table" -N "$chain"
    fi
}

enable_routing() {
    local enable_completed=0
    cleanup_on_enable_failure() {
        if [ "$enable_completed" -ne 1 ]; then
            echo "[Centium Error] An error occurred during enable_routing. Rolling back network changes to prevent dead network..." >&2
            disable_routing || true
        fi
    }
    trap cleanup_on_enable_failure EXIT

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

    echo "[Centium] Enabling system-wide routing via Tor (Tor user: $TOR_UID [UID: $NUMERIC_TOR_UID], iptables: $IPTABLES)..."

    # Pre-load kernel netfilter modules
    load_kernel_modules

    # Backup resolv.conf safely
    backup_dns

    # Block IPv6 leak vectors (with -w flag)
    if [ -n "$IP6TABLES" ]; then
        echo "[Centium] Blocking IPv6 leak vectors..."
        $IP6TABLES -w -P INPUT DROP 2>/dev/null || true
        $IP6TABLES -w -P OUTPUT DROP 2>/dev/null || true
        $IP6TABLES -w -P FORWARD DROP 2>/dev/null || true
        $IP6TABLES -w -F 2>/dev/null || true
        $IP6TABLES -w -A OUTPUT -o lo -j ACCEPT 2>/dev/null || true
    fi

    # Create / flush CENTIUM_NAT in table nat
    ensure_chain nat CENTIUM_NAT

    echo "[Centium] Adding NAT redirection rules..."
    # DNS redirection to Tor DNSPort (5353)
    $IPTABLES -w -t nat -A CENTIUM_NAT -p udp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"
    $IPTABLES -w -t nat -A CENTIUM_NAT -p tcp --dport 53 -j REDIRECT --to-ports "$DNS_PORT"

    # Bypass Tor daemon process to prevent circular loop
    if [ -n "$NUMERIC_TOR_UID" ]; then
        $IPTABLES -w -t nat -A CENTIUM_NAT -m owner --uid-owner "$NUMERIC_TOR_UID" -j RETURN
    else
        $IPTABLES -w -t nat -A CENTIUM_NAT -m owner --uid-owner "$TOR_UID" -j RETURN
    fi

    # Bypass loopback
    $IPTABLES -w -t nat -A CENTIUM_NAT -d 127.0.0.0/8 -j RETURN

    # Redirect all outbound TCP SYN packets to Tor TransPort (9040)
    $IPTABLES -w -t nat -A CENTIUM_NAT -p tcp --syn -j REDIRECT --to-ports "$TRANS_PORT"

    # Hook NAT chain to OUTPUT (insert at position 1 to ensure precedence)
    echo "[Centium] Hooking CENTIUM_NAT into nat OUTPUT..."
    $IPTABLES -w -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
    $IPTABLES -w -t nat -I OUTPUT 1 -j CENTIUM_NAT

    # Create / flush CENTIUM_FILTER in table filter
    ensure_chain filter CENTIUM_FILTER

    echo "[Centium] Adding Kill Switch filter rules..."
    $IPTABLES -w -t filter -A CENTIUM_FILTER -o lo -j ACCEPT

    if [ -n "$NUMERIC_TOR_UID" ]; then
        $IPTABLES -w -t filter -A CENTIUM_FILTER -m owner --uid-owner "$NUMERIC_TOR_UID" -j ACCEPT
    else
        $IPTABLES -w -t filter -A CENTIUM_FILTER -m owner --uid-owner "$TOR_UID" -j ACCEPT
    fi

    # Allow established and related connections
    if ! $IPTABLES -w -t filter -A CENTIUM_FILTER -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT 2>/dev/null; then
        $IPTABLES -w -t filter -A CENTIUM_FILTER -m state --state ESTABLISHED,RELATED -j ACCEPT
    fi

    # Drop any other non-Tor outbound traffic (Kill Switch)
    $IPTABLES -w -t filter -A CENTIUM_FILTER -j DROP

    # Hook FILTER chain to OUTPUT (insert at position 1 to ensure kill switch priority)
    echo "[Centium] Hooking CENTIUM_FILTER into filter OUTPUT..."
    $IPTABLES -w -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
    $IPTABLES -w -t filter -I OUTPUT 1 -j CENTIUM_FILTER

    # Point system DNS to local Tor resolver now that NAT redirection to DNS_PORT is active
    echo "[Centium] Setting system DNS resolver to 127.0.0.1..."
    echo "nameserver 127.0.0.1" > /etc/resolv.conf 2>/dev/null || true

    # Post-condition verification: check that CENTIUM_NAT and CENTIUM_FILTER exist and are hooked into OUTPUT
    echo "[Centium] Performing post-routing iptables rule audit in kernel..."
    local audit_failed=0
    if ! $IPTABLES -w -t nat -C OUTPUT -j CENTIUM_NAT; then
        echo "[Centium Error] Post-condition audit failed: CENTIUM_NAT is not active in nat OUTPUT!" >&2
        audit_failed=1
    fi
    if ! $IPTABLES -w -t filter -C OUTPUT -j CENTIUM_FILTER; then
        echo "[Centium Error] Post-condition audit failed: CENTIUM_FILTER is not active in filter OUTPUT!" >&2
        audit_failed=1
    fi

    if [ "$audit_failed" -ne 0 ]; then
        echo "[Centium Error] Routing verification failed. Kernel audit did not pass." >&2
        exit 1
    fi

    enable_completed=1
    trap - EXIT
    echo "[Centium] System-wide Tor transparent routing and kill switch activated successfully."
}

disable_routing() {
    echo "[Centium] Disabling Tor routing and restoring network defaults..."

    # 1. Detach and flush Centium iptables chains (with -w flag)
    if [ -n "$IPTABLES" ]; then
        echo "[Centium] Removing CENTIUM_NAT and CENTIUM_FILTER iptables chains..."
        $IPTABLES -w -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
        $IPTABLES -w -t nat -F CENTIUM_NAT 2>/dev/null || true
        $IPTABLES -w -t nat -X CENTIUM_NAT 2>/dev/null || true

        $IPTABLES -w -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
        $IPTABLES -w -t filter -F CENTIUM_FILTER 2>/dev/null || true
        $IPTABLES -w -t filter -X CENTIUM_FILTER 2>/dev/null || true
    fi

    # 2. Restore IPv6 default policy
    if [ -n "$IP6TABLES" ]; then
        echo "[Centium] Restoring IPv6 default policies..."
        $IP6TABLES -w -P INPUT ACCEPT 2>/dev/null || true
        $IP6TABLES -w -P OUTPUT ACCEPT 2>/dev/null || true
        $IP6TABLES -w -P FORWARD ACCEPT 2>/dev/null || true
        $IP6TABLES -w -F 2>/dev/null || true
    fi

    # 3. Restore DNS resolver
    restore_dns

    echo "[Centium] Normal network routing restored."
}

status_routing() {
    echo "=== Centium NAT Rules ==="
    if [ -n "$IPTABLES" ]; then
        if $IPTABLES -w -t nat -L CENTIUM_NAT -v -n 2>/dev/null; then
            echo ""
            echo "--- NAT OUTPUT Hook ---"
            $IPTABLES -w -t nat -S OUTPUT 2>/dev/null | grep "CENTIUM_NAT" || echo "CENTIUM_NAT NOT hooked in OUTPUT"
        else
            echo "No CENTIUM_NAT chain active"
        fi
        echo ""
        echo "=== Centium Kill Switch Rules ==="
        if $IPTABLES -w -t filter -L CENTIUM_FILTER -v -n 2>/dev/null; then
            echo ""
            echo "--- Filter OUTPUT Hook ---"
            $IPTABLES -w -t filter -S OUTPUT 2>/dev/null | grep "CENTIUM_FILTER" || echo "CENTIUM_FILTER NOT hooked in OUTPUT"
        else
            echo "No CENTIUM_FILTER chain active"
        fi
        echo ""
    else
        echo "iptables not found"
    fi
    echo "=== Active Tor Ports ==="
    if command -v ss &>/dev/null; then
        ss -lntup | grep -E "${TRANS_PORT}|${DNS_PORT}|9050" || echo "No Tor ports found listening"
    fi
    echo ""
    echo "=== DNS Configuration (/etc/resolv.conf) ==="
    cat /etc/resolv.conf 2>/dev/null || echo "Unable to read /etc/resolv.conf"
}

case "${1:-}" in
    enable) enable_routing ;;
    disable) disable_routing ;;
    status) status_routing ;;
    verify)
        if [ -z "$IPTABLES" ]; then
            echo "[Centium Error] iptables executable not found" >&2
            exit 1
        fi
        $IPTABLES -w -t nat -C OUTPUT -j CENTIUM_NAT && \
        $IPTABLES -w -t filter -C OUTPUT -j CENTIUM_FILTER
        ;;
    *) echo "Usage: $0 {enable|disable|status|verify}"; exit 1 ;;
esac
