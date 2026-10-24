package org.centium.vpn.tor

import org.centium.vpn.data.CentiumConfig

/**
 * Generates the user torrc for TorService, and the settings that switch Tor onto a route.
 * TorService itself supplies DataDirectory, ControlSocket and RunAsDaemon on the command line.
 */
class TorrcGenerator {

    /**
     * Tor starts with the network off. [routeConf] then turns it on for the first route, so
     * every route, including switching to the next one, goes through the same SETCONF path.
     */
    fun generateTorrc(config: CentiumConfig): String {
        val lines = mutableListOf<String>()

        // Let Tor pick a free port so we never collide with Orbot etc.
        lines.add("SocksPort 127.0.0.1:auto")
        lines.add("ClientOnly 1")
        lines.add("AvoidDiskWrites 1")
        lines.add("DisableNetwork 1")

        // Exit node country selection
        if (config.exitLocation.isNotEmpty() && config.exitLocation != "auto") {
            val code = config.exitLocation.lowercase()
            require(code.matches(Regex("[a-z]{2}"))) {
                "Exit country must be a two-letter code like de or us (got \"${config.exitLocation}\")"
            }
            lines.add("ExitNodes {$code}")
            lines.add("StrictNodes 1")
        }

        return lines.joinToString("\n") + "\n"
    }

    /** Pluggable transports [route] needs running before [routeConf] can be applied. */
    fun transportsFor(route: Route, config: CentiumConfig): Set<String> =
        BridgeLines.transports(bridgeLines(route, config))

    /** Settings [routeConf] replaces; [TorManager] resets them before applying a new route. */
    val routeKeys = listOf("UseBridges", "Bridge", "ClientTransportPlugin")

    /**
     * SETCONF entries ("Key value") that put Tor on [route], ending by turning the network on.
     * [transportPorts] maps each running transport to its local SOCKS port.
     */
    fun routeConf(route: Route, config: CentiumConfig, transportPorts: Map<String, Int>): List<String> {
        val bridges = bridgeLines(route, config)
        if (bridges.isEmpty()) return listOf("UseBridges 0", "DisableNetwork 0")

        val plugins = BridgeLines.transports(bridges).sorted().map { transport ->
            val port = checkNotNull(transportPorts[transport]) { "The $transport transport isn't running" }
            "ClientTransportPlugin $transport socks5 127.0.0.1:$port"
        }
        return listOf("UseBridges 1") + bridges.map { "Bridge $it" } + plugins + "DisableNetwork 0"
    }

    private fun bridgeLines(route: Route, config: CentiumConfig): List<String> = when (route) {
        Route.DIRECT -> emptyList()
        Route.SNOWFLAKE -> BuiltinBridges.SNOWFLAKE
        Route.OBFS4 -> BuiltinBridges.OBFS4
        Route.CUSTOM -> BridgeLines.parse(config.customBridges)
    }
}
