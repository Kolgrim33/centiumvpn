package org.centium.vpn.data

enum class ConnectionState(
    val phaseNumber: Int,
    val totalPhases: Int = 8,
    val defaultMessage: String
) {
    DISCONNECTED(0, 8, "Disconnected"),
    STARTING_TOR(1, 8, "Initializing local Tor engine..."),
    WAITING_FOR_BOOTSTRAP(2, 8, "Bootstrapping circuit consensus..."),
    STARTING_BRIDGE(3, 8, "Spawning TUN-to-SOCKS packet bridge..."),
    ESTABLISHING_VPN(4, 8, "Activating virtual TUN network device..."),
    CONFIGURING_DNS_AND_ROUTES(5, 8, "Configuring Tor DNSPort routing..."),
    VERIFYING_PROTECTION(6, 8, "Performing leak audit and exit verification..."),
    CONNECTED(7, 8, "Protected by Centium VPN"),
    RECONNECTING(0, 8, "Re-establishing connection..."),
    DISCONNECTING(0, 8, "Stopping VPN and restoring network..."),
    ERROR(0, 8, "Connection failure");

    val isTransitioning: Boolean
        get() = this in listOf(
            STARTING_TOR,
            WAITING_FOR_BOOTSTRAP,
            STARTING_BRIDGE,
            ESTABLISHING_VPN,
            CONFIGURING_DNS_AND_ROUTES,
            VERIFYING_PROTECTION,
            RECONNECTING,
            DISCONNECTING
        )

    val isConnected: Boolean
        get() = this == CONNECTED
}
