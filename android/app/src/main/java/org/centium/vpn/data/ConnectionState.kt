package org.centium.vpn.data

enum class ConnectionState(
    val phaseNumber: Int,
    val totalPhases: Int = 8,
    val defaultMessage: String
) {
    DISCONNECTED(0, 8, "Not using Tor"),
    STARTING_TOR(1, 8, "Starting Tor"),
    WAITING_FOR_BOOTSTRAP(2, 8, "Connecting to the Tor network"),
    STARTING_BRIDGE(3, 8, "Starting the tunnel"),
    ESTABLISHING_VPN(4, 8, "Starting the tunnel"),
    CONFIGURING_DNS_AND_ROUTES(5, 8, "Routing apps through Tor"),
    VERIFYING_PROTECTION(6, 8, "Checking your Tor exit"),
    CONNECTED(7, 8, "Using Tor"),
    RECONNECTING(0, 8, "Reconnecting"),
    DISCONNECTING(0, 8, "Disconnecting"),
    ERROR(0, 8, "Couldn't connect");

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
