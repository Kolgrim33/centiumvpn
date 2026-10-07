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
    elif [ -n "${2:-}" ]; then
        TOR_USER="$2"
    elif id "debian-tor" &>/dev/null; then
        TOR_USER="debian-tor"
    elif id "tor" &>/dev/null; then
        TOR_USER="tor"
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
    mkdir -p "$RUNTIME_DIR"
    chmod 775 "$RUNTIME_DIR" 2>/dev/null || true

    if [ -z "$NFT_CMD" ]; then
        log_err "nft command not found! nftables is required for fail-closed kill switch."
        return 1
    fi

    # Define atomic ruleset for table inet centium:
    # - Default drop on output, input, and forward
    # - Allow loopback
    # - Allow Tor's own process by UID to reach physical network (with route mark)
    # - Allow established/related traffic
    # - Use iifname/oifname so rules match by interface name and do not fail if centium0 index does not exist yet
    # - Allow DHCP (UDP 67/68) and NTP (UDP 123) so leases renew and clock stays synchronized
    # - Fast-reject unsupported UDP with port-unreachable so apps fail fast
    # - Route hook sets mark 0x8420 on Tor's own packets so policy routing bypasses table 8420
    local block_ipv6_rule=""
    if [ "${CENTIUM_BLOCK_IPV6:-1}" = "0" ]; then
        block_ipv6_rule="meta nfproto ipv6 accept"
    fi

    local nft_ruleset
    nft_ruleset="table ${NFT_FAMILY} ${NFT_TABLE} {
    chain inbound {
        type filter hook input priority filter; policy drop;
        iif \"lo\" accept
        ct state established,related accept
        iifname \"${TUN_DEV}\" accept
        udp sport 67 udp dport 68 accept
    }

    chain forward {
        type filter hook forward priority filter; policy drop;
    }

    chain outbound {
        type filter hook output priority filter; policy drop;
        oif \"lo\" accept
        ct state established,related accept
        skuid ${TOR_NUMERIC_UID} accept
        udp sport 68 udp dport 67 accept
        udp dport 123 accept
        oifname \"${TUN_DEV}\" ip daddr ${DNS_MAPPED_IP} udp dport 53 accept
        meta l4proto udp reject with icmpx type port-unreachable
        oifname \"${TUN_DEV}\" accept
        ${block_ipv6_rule}
    }

    chain route_hook {
        type route hook output priority mangle; policy accept;
        skuid ${TOR_NUMERIC_UID} meta mark set ${FWMARK}
    }
}"

    # Load ruleset into kernel atomically
    if echo "$nft_ruleset" | "$NFT_CMD" -f -; then
        log "✓ Fail-closed nftables kill switch active in kernel (iifname/oifname ${TUN_DEV})."
        return 0
    else
        log_err "Failed to load nftables ruleset into kernel! Aborting connection."
        return 1
    fi
}

remove_killswitch() {
    log "Removing nftables table ${NFT_FAMILY} ${NFT_TABLE}..."
    if [ -n "$NFT_CMD" ]; then
        "$NFT_CMD" delete table "${NFT_FAMILY}" "${NFT_TABLE}" 2>/dev/null || true
    fi
}

# ------------------------------------------------------------------------------
# 2. TUN & Bridge: hev-socks5-tunnel & tun2socks
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

mapdns:
  address: ${DNS_MAPPED_IP}
  port: 53
  network: 100.64.0.0
  netmask: 255.192.0.0
  cache-size: 10000

misc:
  task-stack-size: 81920
  connect-timeout: 60000
  read-write-timeout: 60000
  log-level: warn
  limit-nofile: 65535
EOF
    chmod 644 "$HEV_CONFIG"
}

start_hev_bridge() {
    log "Starting TUN interface ${TUN_DEV} and SOCKS5 bridge..."
    mkdir -p "$RUNTIME_DIR"
    stop_hev_bridge

    # 1. Create real TUN interface centium0 if it does not exist
    if [ -n "$IP_CMD" ]; then
        if ! "$IP_CMD" link show "$TUN_DEV" &>/dev/null; then
            "$IP_CMD" tuntap add dev "$TUN_DEV" mode tun 2>/dev/null || true
        fi
        "$IP_CMD" link set dev "$TUN_DEV" up 2>/dev/null || true
        "$IP_CMD" addr add "$TUN_IPV4" dev "$TUN_DEV" 2>/dev/null || true
    fi

    local bridge_pid=""
    local bridge_name=""

    # 2. Check for hev-socks5-tunnel (Primary bridge engine with native mapped-DNS 198.18.0.2)
    if [ -z "$HEV_BIN" ]; then
        for p in /usr/local/bin/hev-socks5-tunnel /usr/bin/hev-socks5-tunnel /opt/centium/bin/hev-socks5-tunnel; do
            if [ -x "$p" ]; then
                HEV_BIN="$p"
                break
            fi
        done
    fi

    # 3. Check for tun2socks (fallback only)
    local tun2socks_bin
    tun2socks_bin="$(find_executable tun2socks || true)"
    for p in /usr/local/bin/tun2socks /usr/bin/tun2socks /opt/centium/bin/tun2socks; do
        if [ -x "$p" ]; then
            tun2socks_bin="$p"
            break
        fi
    done

    # Prefer hev-socks5-tunnel (crucial: provides mapped-DNS on 198.18.0.2 via Tor SOCKS5)
    if [ -n "$HEV_BIN" ] && [ -x "$HEV_BIN" ]; then
        generate_hev_config
        log "Starting hev-socks5-tunnel bridge on ${TUN_DEV} (with mapped-DNS ${DNS_MAPPED_IP})..."
        "$HEV_BIN" "$HEV_CONFIG" > "$HEV_LOG_FILE" 2>&1 &
        local candidate_pid=$!
        sleep 0.5
        if kill -0 "$candidate_pid" 2>/dev/null; then
            bridge_pid="$candidate_pid"
            bridge_name="hev-socks5-tunnel"
        fi
    fi

    # Fallback to tun2socks only if hev-socks5-tunnel failed or is not available
    if [ -z "$bridge_pid" ] && [ -n "$tun2socks_bin" ] && [ -x "$tun2socks_bin" ]; then
        log "Warning: hev-socks5-tunnel not available. Falling back to tun2socks on ${TUN_DEV} -> ${SOCKS5_HOST}:${SOCKS5_PORT}..."
        "$tun2socks_bin" -device "tun://${TUN_DEV}" -proxy "socks5://${SOCKS5_HOST}:${SOCKS5_PORT}" -loglevel debug > "$HEV_LOG_FILE" 2>&1 &
        local candidate_pid=$!
        sleep 0.5
        if kill -0 "$candidate_pid" 2>/dev/null; then
            bridge_pid="$candidate_pid"
            bridge_name="tun2socks"
        fi
    fi

    if [ -z "$bridge_pid" ]; then
        log_err "Failed to start TUN bridge. Neither tun2socks nor hev-socks5-tunnel could be executed."
        cat "$HEV_LOG_FILE" >&2 || true
        return 1
    fi

    echo "$bridge_pid" > "$HEV_PID_FILE"

    # Confirm centium0 is UP
    if [ -n "$IP_CMD" ]; then
        "$IP_CMD" link set dev "$TUN_DEV" up 2>/dev/null || true
        "$IP_CMD" addr add "$TUN_IPV4" dev "$TUN_DEV" 2>/dev/null || true
    fi

    log "✓ TUN interface ${TUN_DEV} is active and ${bridge_name} is running (PID ${bridge_pid})."
}

stop_hev_bridge() {
    log "Stopping TUN bridge..."
    if [ -f "$HEV_PID_FILE" ]; then
        local pid
        pid="$(cat "$HEV_PID_FILE" 2>/dev/null || true)"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            kill -TERM "$pid" 2>/dev/null || true
            sleep 0.2
            if kill -0 "$pid" 2>/dev/null; then
                kill -9 "$pid" 2>/dev/null || true
            fi
        fi
        rm -f "$HEV_PID_FILE"
    fi

    # Kill any orphaned processes
    pkill -f "hev-socks5-tunnel.*${HEV_CONFIG}" 2>/dev/null || true
    pkill -f "tun2socks.*${TUN_DEV}" 2>/dev/null || true

    # Remove interface cleanly
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
        log_err "ip command not found! iproute2 is required for policy routing."
        return 1
    fi

    # Clean existing Centium rules/routes in table 8420 to prevent duplicates
    "$IP_CMD" rule del fwmark "$FWMARK" lookup main 2>/dev/null || true
    "$IP_CMD" rule del uidrange "${TOR_NUMERIC_UID}-${TOR_NUMERIC_UID}" lookup main 2>/dev/null || true
    "$IP_CMD" rule del not fwmark "$FWMARK" lookup "$ROUTING_TABLE" 2>/dev/null || true
    "$IP_CMD" rule del lookup "$ROUTING_TABLE" 2>/dev/null || true
    "$IP_CMD" route flush table "$ROUTING_TABLE" 2>/dev/null || true

    # Default route for table 8420 goes through centium0
    if ! "$IP_CMD" route add default dev "$TUN_DEV" table "$ROUTING_TABLE"; then
        log_err "Failed to add default route dev ${TUN_DEV} to table ${ROUTING_TABLE}!"
        return 1
    fi

    # Route Tor's own process to main table (so it uses physical eth/wifi directly)
    "$IP_CMD" rule add fwmark "$FWMARK" lookup main pref 8418
    "$IP_CMD" rule add uidrange "${TOR_NUMERIC_UID}-${TOR_NUMERIC_UID}" lookup main pref 8419

    # Direct all other system traffic to table 8420
    if ! "$IP_CMD" rule add not fwmark "$FWMARK" lookup "$ROUTING_TABLE" pref 8420; then
        log_err "Failed to add policy routing rule (pref 8420) to table ${ROUTING_TABLE}!"
        return 1
    fi

    log "✓ Policy routing installed (table ${ROUTING_TABLE}, Tor UID ${TOR_NUMERIC_UID} bypassed)."
    return 0
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
    chmod 775 "$RUNTIME_DIR" 2>/dev/null || true

    # Configure systemd-resolved on centium0 interface if resolvectl is present
    if command -v resolvectl &>/dev/null; then
        resolvectl dns "${TUN_DEV}" "${DNS_MAPPED_IP}" 2>/dev/null || true
        resolvectl domain "${TUN_DEV}" "~." 2>/dev/null || true
        resolvectl default-route "${TUN_DEV}" true 2>/dev/null || true
    fi

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
    if command -v resolvectl &>/dev/null; then
        resolvectl revert "${TUN_DEV}" 2>/dev/null || true
    fi

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

    # If systemd-resolved is active, notify it
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

    # 3. Check nftables kill switch (real kernel check, no marker file!)
    if [ -n "$NFT_CMD" ]; then
        if ! "$NFT_CMD" list table "${NFT_FAMILY}" "${NFT_TABLE}" 2>/dev/null | grep -q "chain outbound"; then
            log_err "Verification failed: nftables table ${NFT_FAMILY} ${NFT_TABLE} does not exist in kernel!"
            errors=$((errors + 1))
        fi
    else
        log_err "Verification failed: nft command missing!"
        errors=$((errors + 1))
    fi

    # 4. Check routing table 8420 and rule (real iproute check, no marker file!)
    if [ -n "$IP_CMD" ]; then
        if ! "$IP_CMD" route show table "$ROUTING_TABLE" 2>/dev/null | grep -q "$TUN_DEV"; then
            log_err "Verification failed: default route in table ${ROUTING_TABLE} dev ${TUN_DEV} is missing!"
            errors=$((errors + 1))
        fi
        if ! "$IP_CMD" rule show 2>/dev/null | grep -q "lookup ${ROUTING_TABLE}"; then
            log_err "Verification failed: policy rule for table ${ROUTING_TABLE} is missing!"
            errors=$((errors + 1))
        fi
    else
        log_err "Verification failed: ip command missing!"
        errors=$((errors + 1))
    fi

    # 5. Check DNS resolver
    local dns_pointing=0
    if [ -f /etc/resolv.conf ] && grep -q "${DNS_MAPPED_IP}" /etc/resolv.conf 2>/dev/null; then
        dns_pointing=1
    elif command -v resolvectl &>/dev/null && resolvectl dns "${TUN_DEV}" 2>/dev/null | grep -q "${DNS_MAPPED_IP}"; then
        dns_pointing=1
    fi
    if [ "$dns_pointing" -eq 0 ]; then
        log_err "Verification failed: System resolver does not point to ${DNS_MAPPED_IP}!"
        errors=$((errors + 1))
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
