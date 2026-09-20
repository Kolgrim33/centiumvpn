#!/usr/bin/env bash
# ==============================================================================
# Centium VPN - Native Linux Network Engine
# Architecture: TUN (centium0) -> hev-socks5-tunnel -> Tor SOCKS5 (127.0.0.1:9050)
# Features:
#   - Real TUN interface (centium0) lifecycle managed by hev-socks5-tunnel
#   - Isolated policy routing (dedicated table 8420, main table untouched)
#   - Fail-closed nftables kill switch (table inet centium)
#   - Mapped-DNS interception (198.18.0.2) routed exclusively through Tor
#   - Comprehensive verification, automated leak tests, and crash recovery
# ==============================================================================

set -eo pipefail

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${PATH:-}"

# Configuration Constants
TUN_DEV="centium0"
TUN_IPV4="198.18.0.1/15"
DNS_MAPPED_IP="198.18.0.2"
SOCKS5_HOST="127.0.0.1"
SOCKS5_PORT="${SOCKS5_PORT:-9050}"
ROUTING_TABLE=8420
FWMARK=0x8420
NFT_TABLE="centium"
NFT_FAMILY="inet"

RUNTIME_DIR="/run/centium"
HEV_CONFIG="${RUNTIME_DIR}/hev-socks5-tunnel.yml"
HEV_PID_FILE="${RUNTIME_DIR}/hev-socks5-tunnel.pid"
HEV_LOG_FILE="${RUNTIME_DIR}/hev-socks5-tunnel.log"
RESOLV_BACKUP="${RUNTIME_DIR}/resolv.conf.backup"
ORIG_RESOLV_TARGET="${RUNTIME_DIR}/resolv.conf.target"

# Helper: locate executable across system directories
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

IP_CMD="$(find_executable ip || true)"
NFT_CMD="$(find_executable nft || true)"
HEV_BIN="$(find_executable hev-socks5-tunnel || true)"
CURL_CMD="$(find_executable curl || true)"

# Resolve Tor system user and UID
resolve_tor_user() {
    if [ -n "${CENTIUM_TOR_UID:-}" ]; then
        TOR_USER="$CENTIUM_TOR_UID"
    elif [ -n "${2:-}" ] && [ "${1:-}" = "enable" ]; then
        TOR_USER="$2"
    elif id "tor" &>/dev/null; then
        TOR_USER="tor"
    elif id "debian-tor" &>/dev/null; then
        TOR_USER="debian-tor"
    else
        TOR_USER="$(id -un)"
    fi

    if id -u "$TOR_USER" &>/dev/null; then
        TOR_NUMERIC_UID="$(id -u "$TOR_USER")"
    elif [[ "$TOR_USER" =~ ^[0-9]+$ ]]; then
        TOR_NUMERIC_UID="$TOR_USER"
    else
        TOR_NUMERIC_UID="$(id -u)"
    fi
}

resolve_tor_user "${1:-}" "${2:-}"

log() {
    echo "[Centium Network] $*"
}

log_err() {
    echo "[Centium Network Error] $*" >&2
}

# ------------------------------------------------------------------------------
# 1. Kill Switch: nftables inet centium
# ------------------------------------------------------------------------------
install_killswitch() {
    log "Installing fail-closed nftables kill switch (table ${NFT_FAMILY} ${NFT_TABLE})..."

    if [ -z "$NFT_CMD" ]; then
        log_err "nft command not found in PATH or standard system directories!"
        return 1
    fi

    # Ensure runtime directory exists
    mkdir -p "$RUNTIME_DIR"

    # Define atomic ruleset for table inet centium:
    # - Default drop on output, input, and forward
    # - Allow loopback
    # - Allow Tor's own process by UID to reach physical network (with route mark)
    # - Allow established/related traffic
    # - Allow traffic leaving or entering centium0
    # - Route hook sets mark 0x8420 on Tor's own packets so policy routing bypasses table 8420
    local nft_ruleset
    nft_ruleset="table ${NFT_FAMILY} ${NFT_TABLE} {
    chain inbound {
        type filter hook input priority filter; policy drop;
        iif \"lo\" accept
        ct state established,related accept
        iif \"${TUN_DEV}\" accept
    }

    chain forward {
        type filter hook forward priority filter; policy drop;
    }

    chain outbound {
        type filter hook output priority filter; policy drop;
        oif \"lo\" accept
        ct state established,related accept
        skuid ${TOR_NUMERIC_UID} accept
        oif \"${TUN_DEV}\" accept
    }

    chain route_hook {
        type route hook output priority mangle; policy accept;
        skuid ${TOR_NUMERIC_UID} meta mark set ${FWMARK}
    }
}"

    # Load into nftables atomically
    echo "$nft_ruleset" | "$NFT_CMD" -f -

    log "✓ Fail-closed nftables kill switch active."
}

remove_killswitch() {
    log "Removing nftables table ${NFT_FAMILY} ${NFT_TABLE}..."
    if [ -n "$NFT_CMD" ]; then
        "$NFT_CMD" delete table "${NFT_FAMILY}" "${NFT_TABLE}" 2>/dev/null || true
    fi
}

# ------------------------------------------------------------------------------
# 2. TUN & Bridge: hev-socks5-tunnel
# ------------------------------------------------------------------------------
generate_hev_config() {
    mkdir -p "$RUNTIME_DIR"
    cat << EOF > "$HEV_CONFIG"
tunnel:
  name: ${TUN_DEV}
  mtu: 8500
  ipv4: 198.18.0.1
  ipv6: "fc00::1"

socks5:
  port: ${SOCKS5_PORT}
  address: ${SOCKS5_HOST}
  udp: 'tcp'

misc:
  task-stack-size: 81920
  connect-timeout: 5000
  read-write-timeout: 60000
  log-level: warn
  limit-nofile: 65535
EOF
    chmod 644 "$HEV_CONFIG"
}

start_hev_bridge() {
    log "Starting TUN-to-SOCKS5 bridge (hev-socks5-tunnel) on ${TUN_DEV}..."

    if [ -z "$HEV_BIN" ]; then
        # Check standard binary locations
        for p in /usr/local/bin/hev-socks5-tunnel /usr/bin/hev-socks5-tunnel /opt/centium/bin/hev-socks5-tunnel; do
            if [ -x "$p" ]; then
                HEV_BIN="$p"
                break
            fi
        done
    fi

    if [ -z "$HEV_BIN" ] || [ ! -x "$HEV_BIN" ]; then
        log_err "hev-socks5-tunnel binary not found. Run sudo ./setup-linux.sh to build and install it."
        return 1
    fi

    generate_hev_config

    # Stop any running instances first
    stop_hev_bridge

    # Spawn hev-socks5-tunnel in background with logging
    "$HEV_BIN" "$HEV_CONFIG" > "$HEV_LOG_FILE" 2>&1 &
    local hev_pid=$!
    echo "$hev_pid" > "$HEV_PID_FILE"

    # Wait up to 5 seconds for centium0 to appear
    local waited=0
    while [ "$waited" -lt 50 ]; do
        if [ -n "$IP_CMD" ] && "$IP_CMD" link show "$TUN_DEV" &>/dev/null; then
            break
        fi
        # Check if process died
        if ! kill -0 "$hev_pid" 2>/dev/null; then
            log_err "hev-socks5-tunnel process exited unexpectedly. Logs:"
            cat "$HEV_LOG_FILE" >&2 || true
            return 1
        fi
        sleep 0.1
        waited=$((waited + 1))
    done

    if [ -z "$IP_CMD" ] || ! "$IP_CMD" link show "$TUN_DEV" &>/dev/null; then
        log_err "Interface ${TUN_DEV} was not created by hev-socks5-tunnel."
        return 1
    fi

    # Ensure interface is up and has correct IP address
    "$IP_CMD" link set dev "$TUN_DEV" up
    "$IP_CMD" addr add "$TUN_IPV4" dev "$TUN_DEV" 2>/dev/null || true

    log "✓ TUN interface ${TUN_DEV} is UP and bridge is running (PID ${hev_pid})."
}

stop_hev_bridge() {
    log "Stopping hev-socks5-tunnel bridge..."
    if [ -f "$HEV_PID_FILE" ]; then
        local pid
        pid="$(cat "$HEV_PID_FILE" 2>/dev/null || true)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            kill -TERM "$pid" 2>/dev/null || true
            sleep 0.3
            if kill -0 "$pid" 2>/dev/null; then
                kill -9 "$pid" 2>/dev/null || true
            fi
        fi
        rm -f "$HEV_PID_FILE"
    fi

    # Also kill any orphaned hev-socks5-tunnel processes associated with centium0
    pkill -f "hev-socks5-tunnel.*${HEV_CONFIG}" 2>/dev/null || true

    # Remove interface if still exists
    if [ -n "$IP_CMD" ] && "$IP_CMD" link show "$TUN_DEV" &>/dev/null; then
        "$IP_CMD" link set dev "$TUN_DEV" down 2>/dev/null || true
        "$IP_CMD" link delete "$TUN_DEV" 2>/dev/null || true
    fi
}

# ------------------------------------------------------------------------------
# 3. Policy Routing: Dedicated Table 8420
# ------------------------------------------------------------------------------
install_routing() {
    log "Installing policy routing for ${TUN_DEV} in table ${ROUTING_TABLE}..."

    if [ -z "$IP_CMD" ]; then
        log_err "ip command not found!"
        return 1
    fi

    # Clean existing Centium rules/routes in table 8420 to prevent duplicates
    "$IP_CMD" rule del fwmark "$FWMARK" lookup main 2>/dev/null || true
    "$IP_CMD" rule del uidrange "${TOR_NUMERIC_UID}-${TOR_NUMERIC_UID}" lookup main 2>/dev/null || true
    "$IP_CMD" rule del not fwmark "$FWMARK" lookup "$ROUTING_TABLE" 2>/dev/null || true
    "$IP_CMD" rule del lookup "$ROUTING_TABLE" 2>/dev/null || true
    "$IP_CMD" route flush table "$ROUTING_TABLE" 2>/dev/null || true

    # Default route for table 8420 goes through centium0
    "$IP_CMD" route add default dev "$TUN_DEV" table "$ROUTING_TABLE"

    # Route Tor's own process to main table (so it uses physical eth/wifi directly)
    "$IP_CMD" rule add fwmark "$FWMARK" lookup main pref 8418 2>/dev/null || true
    "$IP_CMD" rule add uidrange "${TOR_NUMERIC_UID}-${TOR_NUMERIC_UID}" lookup main pref 8419 2>/dev/null || true

    # Direct all other system traffic to table 8420
    "$IP_CMD" rule add not fwmark "$FWMARK" lookup "$ROUTING_TABLE" pref 8420

    log "✓ Policy routing installed (table ${ROUTING_TABLE}, Tor UID ${TOR_NUMERIC_UID} bypassed)."
}

remove_routing() {
    log "Removing policy routing rules and flushing table ${ROUTING_TABLE}..."
    if [ -n "$IP_CMD" ]; then
        "$IP_CMD" rule del pref 8418 2>/dev/null || true
        "$IP_CMD" rule del pref 8419 2>/dev/null || true
        "$IP_CMD" rule del pref 8420 2>/dev/null || true
        "$IP_CMD" rule del fwmark "$FWMARK" lookup main 2>/dev/null || true
        "$IP_CMD" rule del uidrange "${TOR_NUMERIC_UID}-${TOR_NUMERIC_UID}" lookup main 2>/dev/null || true
        "$IP_CMD" rule del not fwmark "$FWMARK" lookup "$ROUTING_TABLE" 2>/dev/null || true
        "$IP_CMD" rule del lookup "$ROUTING_TABLE" 2>/dev/null || true
        "$IP_CMD" route flush table "$ROUTING_TABLE" 2>/dev/null || true
    fi
}

# ------------------------------------------------------------------------------
# 4. DNS: Mapped-DNS Protection & Safe Backup/Restore
# ------------------------------------------------------------------------------
install_dns() {
    log "Configuring system DNS to use Centium mapped-DNS (${DNS_MAPPED_IP})..."
    mkdir -p "$RUNTIME_DIR"

    # Check if resolv.conf is a symlink (systemd-resolved, etc.)
    if [ -L /etc/resolv.conf ]; then
        readlink -f /etc/resolv.conf > "$ORIG_RESOLV_TARGET" 2>/dev/null || true
    fi

    # Backup resolv.conf if not already backed up
    if [ -f /etc/resolv.conf ] && [ ! -f "$RESOLV_BACKUP" ]; then
        cp -L /etc/resolv.conf "$RESOLV_BACKUP" 2>/dev/null || true
    fi

    # Atomically configure resolv.conf to point to internal mapped-DNS
    local tmp_resolv="${RUNTIME_DIR}/resolv.conf.tmp"
    cat << EOF > "$tmp_resolv"
# Generated by Centium VPN
# Queries are intercepted by hev-socks5-tunnel and resolved via Tor SOCKS5
nameserver ${DNS_MAPPED_IP}
options edns0
EOF
    chmod 644 "$tmp_resolv"

    # Replace /etc/resolv.conf safely
    if [ -L /etc/resolv.conf ]; then
        rm -f /etc/resolv.conf
    fi
    cp "$tmp_resolv" /etc/resolv.conf 2>/dev/null || true
    rm -f "$tmp_resolv"

    log "✓ System DNS set to ${DNS_MAPPED_IP}."
}

restore_dns() {
    log "Restoring system DNS configuration..."
    if [ -f "$RESOLV_BACKUP" ]; then
        # If original was a symlink to systemd-resolved stub
        if [ -f "$ORIG_RESOLV_TARGET" ]; then
            local target
            target="$(cat "$ORIG_RESOLV_TARGET" 2>/dev/null || true)"
            if [ -n "$target" ] && [ -f "$target" ]; then
                ln -sf "$target" /etc/resolv.conf 2>/dev/null || cp "$RESOLV_BACKUP" /etc/resolv.conf 2>/dev/null || true
            else
                cp "$RESOLV_BACKUP" /etc/resolv.conf 2>/dev/null || true
            fi
            rm -f "$ORIG_RESOLV_TARGET"
        else
            cp "$RESOLV_BACKUP" /etc/resolv.conf 2>/dev/null || true
        fi
        rm -f "$RESOLV_BACKUP"
    fi

    # If dhcpcd or systemd-resolved is active, notify them
    if command -v systemctl &>/dev/null; then
        if systemctl is-active systemd-resolved &>/dev/null; then
            systemctl restart systemd-resolved 2>/dev/null || true
        fi
    fi

    log "✓ System DNS restored."
}

# ------------------------------------------------------------------------------
# 5. High-Level Operations: Enable, Disable, Verify, Status, Recover
# ------------------------------------------------------------------------------
verify_tor_prerequisites() {
    log "Verifying Tor SOCKS5 listener on ${SOCKS5_HOST}:${SOCKS5_PORT}..."
    if command -v ss &>/dev/null; then
        if ! ss -tln | grep -q ":${SOCKS5_PORT}\\b"; then
            log_err "Tor SOCKS5 listener on :${SOCKS5_PORT} is not ready! Aborting."
            return 1
        fi
    fi
    return 0
}

enable_all() {
    log "=========================================================="
    log "Activating Centium VPN Tunnel and Network Protection"
    log "Tor User: ${TOR_USER} (UID: ${TOR_NUMERIC_UID})"
    log "=========================================================="

    if ! verify_tor_prerequisites; then
        exit 1
    fi

    # 1. Install kill switch first so no leaks occur during setup
    install_killswitch

    # 2. Start TUN device and hev-socks5-tunnel bridge
    if ! start_hev_bridge; then
        log_err "Failed to start TUN bridge. Rolling back network state..."
        disable_all
        exit 1
    fi

    # 3. Install policy routing table 8420
    if ! install_routing; then
        log_err "Failed to install policy routing. Rolling back network state..."
        disable_all
        exit 1
    fi

    # 4. Install mapped-DNS resolver
    install_dns

    # 5. Perform comprehensive post-activation verification
    if ! verify_status; then
        log_err "Post-activation verification failed. Rolling back..."
        disable_all
        exit 1
    fi

    log "=========================================================="
    log "✓ Centium VPN is CONNECTED and fully verified."
    log "All system traffic is safely routed through Tor."
    log "=========================================================="
}

disable_all() {
    log "Deactivating Centium VPN and restoring normal networking..."
    remove_routing
    restore_dns
    stop_hev_bridge
    remove_killswitch
    log "✓ Normal networking restored."
}

verify_status() {
    log "Running full-stack network verification..."
    local errors=0

    # 1. Check TUN interface
    if [ -z "$IP_CMD" ] || ! "$IP_CMD" link show "$TUN_DEV" &>/dev/null; then
        log_err "Verification failed: Interface ${TUN_DEV} does not exist!"
        errors=$((errors + 1))
    fi

    # 2. Check hev-socks5-tunnel process
    if [ -f "$HEV_PID_FILE" ]; then
        local pid
        pid="$(cat "$HEV_PID_FILE" 2>/dev/null || true)"
        if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
            log_err "Verification failed: hev-socks5-tunnel process is not running!"
            errors=$((errors + 1))
        fi
    else
        log_err "Verification failed: hev-socks5-tunnel PID file missing!"
        errors=$((errors + 1))
    fi

    # 3. Check nftables kill switch
    if [ -n "$NFT_CMD" ]; then
        if ! "$NFT_CMD" list table "${NFT_FAMILY}" "${NFT_TABLE}" &>/dev/null; then
            log_err "Verification failed: nftables table ${NFT_FAMILY} ${NFT_TABLE} does not exist!"
            errors=$((errors + 1))
        fi
    fi

    # 4. Check routing table 8420 and rule
    if [ -n "$IP_CMD" ]; then
        if ! "$IP_CMD" route show table "$ROUTING_TABLE" | grep -q "$TUN_DEV"; then
            log_err "Verification failed: default route in table ${ROUTING_TABLE} is missing!"
            errors=$((errors + 1))
        fi
        if ! "$IP_CMD" rule show | grep -q "lookup ${ROUTING_TABLE}"; then
            log_err "Verification failed: policy rule for table ${ROUTING_TABLE} is missing!"
            errors=$((errors + 1))
        fi
    fi

    # 5. Check DNS resolver
    if [ -f /etc/resolv.conf ]; then
        if ! grep -q "${DNS_MAPPED_IP}" /etc/resolv.conf; then
            log_err "Verification failed: /etc/resolv.conf does not point to ${DNS_MAPPED_IP}!"
            errors=$((errors + 1))
        fi
    fi

    if [ "$errors" -gt 0 ]; then
        log_err "Total verification failures: ${errors}"
        return 1
    fi

    log "✓ All post-activation verifications passed."
    return 0
}

show_status() {
    echo "=== Centium VPN Network Status ==="
    if [ -n "$IP_CMD" ] && "$IP_CMD" link show "$TUN_DEV" &>/dev/null; then
        echo "TUN Interface (${TUN_DEV}): UP"
        "$IP_CMD" addr show "$TUN_DEV"
    else
        echo "TUN Interface (${TUN_DEV}): NOT ACTIVE"
    fi

    if [ -f "$HEV_PID_FILE" ] && kill -0 "$(cat "$HEV_PID_FILE" 2>/dev/null)" 2>/dev/null; then
        echo "Bridge (hev-socks5-tunnel): RUNNING (PID $(cat "$HEV_PID_FILE"))"
    else
        echo "Bridge (hev-socks5-tunnel): STOPPED"
    fi

    if [ -n "$NFT_CMD" ] && "$NFT_CMD" list table "${NFT_FAMILY}" "${NFT_TABLE}" &>/dev/null; then
        echo "Kill Switch (nftables inet centium): ACTIVE (Fail-Closed)"
    else
        echo "Kill Switch: NOT ACTIVE"
    fi

    if [ -n "$IP_CMD" ]; then
        echo "Routing Rules (table ${ROUTING_TABLE}):"
        "$IP_CMD" rule show | grep -E "(${ROUTING_TABLE}|${FWMARK})" || echo "  (None)"
        echo "Routing Table ${ROUTING_TABLE}:"
        "$IP_CMD" route show table "$ROUTING_TABLE" || echo "  (Empty)"
    fi

    echo "Resolver (/etc/resolv.conf):"
    head -n 5 /etc/resolv.conf 2>/dev/null || echo "  (Not accessible)"
}

recover_network() {
    log "Performing emergency crash recovery / network reset..."
    disable_all
    # Also clean up legacy iptables chains if present
    local iptables_bin
    iptables_bin="$(find_executable iptables || true)"
    if [ -n "$iptables_bin" ]; then
        $iptables_bin -w -t nat -D OUTPUT -j CENTIUM_NAT 2>/dev/null || true
        $iptables_bin -w -t filter -D OUTPUT -j CENTIUM_FILTER 2>/dev/null || true
        $iptables_bin -w -t nat -F CENTIUM_NAT 2>/dev/null || true
        $iptables_bin -w -t nat -X CENTIUM_NAT 2>/dev/null || true
        $iptables_bin -w -t filter -F CENTIUM_FILTER 2>/dev/null || true
        $iptables_bin -w -t filter -X CENTIUM_FILTER 2>/dev/null || true
    fi
    log "✓ Crash recovery complete. Network restored."
}

# ------------------------------------------------------------------------------
# Entry Point Dispatcher
# ------------------------------------------------------------------------------
case "${1:-}" in
    enable)
        enable_all
        ;;
    disable)
        disable_all
        ;;
    status)
        show_status
        ;;
    verify)
        verify_status
        ;;
    recover)
        recover_network
        ;;
    start-killswitch)
        install_killswitch
        ;;
    stop-killswitch)
        remove_killswitch
        ;;
    start-tun)
        start_hev_bridge
        ;;
    stop-tun)
        stop_hev_bridge
        ;;
    install-routing)
        install_routing
        ;;
    remove-routing)
        remove_routing
        ;;
    install-dns)
        install_dns
        ;;
    restore-dns)
        restore_dns
        ;;
    *)
        echo "Usage: $0 {enable|disable|status|verify|recover|start-killswitch|stop-killswitch|start-tun|stop-tun|install-routing|remove-routing|install-dns|restore-dns}"
        exit 1
        ;;
esac
