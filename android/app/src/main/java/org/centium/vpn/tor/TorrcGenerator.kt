package org.centium.vpn.tor

import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.BridgeType
import org.centium.vpn.data.CentiumConfig

/**
 * Generates the user torrc for TorService. TorService itself supplies
 * DataDirectory, ControlSocket and RunAsDaemon on the command line.
 */
class TorrcGenerator {

    fun generateTorrc(config: CentiumConfig): String {
        val lines = mutableListOf<String>()

        // Let Tor pick a free port so we never collide with Orbot etc.
        lines.add("SocksPort 127.0.0.1:auto")
        lines.add("ClientOnly 1")
        lines.add("AvoidDiskWrites 1")

        // Exit node country selection
        if (config.exitLocation.isNotEmpty() && config.exitLocation != "auto") {
            val code = config.exitLocation.lowercase()
            require(code.matches(Regex("[a-z]{2}"))) {
                "Exit country must be a two-letter code like de or us (got \"${config.exitLocation}\")"
            }
            lines.add("ExitNodes {$code}")
            lines.add("StrictNodes 1")
        }

        val bridgeLines = when {
            config.bridgeMode == BridgeMode.BUILTIN && config.bridgeType == BridgeType.SNOWFLAKE ->
                throw IllegalArgumentException(PT_UNSUPPORTED)
            config.bridgeMode == BridgeMode.CUSTOM ||
                (config.bridgeMode == BridgeMode.BUILTIN && config.bridgeType == BridgeType.OBFS4) ->
                parseBridgeLines(config.customBridges)
            else -> emptyList()
        }

        if (bridgeLines.isNotEmpty()) {
            lines.add("UseBridges 1")
            bridgeLines.forEach { lines.add("Bridge $it") }
        }

        return lines.joinToString("\n") + "\n"
    }

    private fun parseBridgeLines(raw: String): List<String> {
        return raw.lines()
            .map { it.trim().removePrefix("Bridge ").trim() }
            .filter { it.isNotEmpty() }
            .onEach { line ->
                // A vanilla bridge starts with IP:port; anything else names a transport
                val first = line.substringBefore(' ')
                if (!first.contains(':')) throw IllegalArgumentException(PT_UNSUPPORTED)
            }
    }

    companion object {
        private const val PT_UNSUPPORTED =
            "Pluggable-transport bridges (obfs4/snowflake/meek/webtunnel) are not bundled in this build. " +
                "Use plain IP:port bridges or disable bridges in Settings."
    }
}
