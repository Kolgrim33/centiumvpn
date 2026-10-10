package org.centium.vpn.tor

import java.io.File
import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.BridgeType
import org.centium.vpn.data.CentiumConfig

class TorrcGenerator {

    fun generateTorrc(
        config: CentiumConfig,
        dataDir: File,
        pidFile: File,
        logFile: File,
        appFilesDir: File
    ): String {
        val lines = mutableListOf<String>()

        lines.add("DataDirectory ${dataDir.absolutePath}")
        lines.add("PidFile ${pidFile.absolutePath}")
        lines.add("SocksPort 127.0.0.1:${config.socksPort}")
        lines.add("ControlPort 127.0.0.1:${config.controlPort}")
        lines.add("DNSPort 127.0.0.1:${config.dnsPort}")
        lines.add("AutomapHostsOnResolve 1")
        lines.add("AutomapHostsSuffixes .exit,.onion")
        lines.add("VirtualAddrNetworkIPv4 10.192.0.0/10")
        lines.add("CookieAuthentication 0")
        lines.add("ExitRelay 0")
        lines.add("ClientOnly 1")
        lines.add("RunAsDaemon 0")
        lines.add("Log notice file ${logFile.absolutePath}")

        // Exit node country selection
        if (config.exitLocation.isNotEmpty() && config.exitLocation != "auto") {
            val code = config.exitLocation.lowercase()
            lines.add("ExitNodes {$code}")
            lines.add("StrictNodes 1")
        }

        // Bridge configuration
        when (config.bridgeMode) {
            BridgeMode.BUILTIN -> {
                when (config.bridgeType) {
                    BridgeType.SNOWFLAKE -> {
                        lines.add("UseBridges 1")
                        lines.add("Bridge snowflake 192.0.2.3:1 2B280B23E1107BB62ABFC40DDCC816AE1BF03482")
                        lines.add("Bridge snowflake 192.0.2.4:1 8838EA4445A2D3D96CF7BA7269F860286E2130AD")
                    }
                    BridgeType.OBFS4 -> {
                        if (config.customBridges.isNotBlank()) {
                            lines.add("UseBridges 1")
                            config.customBridges.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                                val clean = if (line.startsWith("Bridge ")) line.substring(7) else line
                                lines.add("Bridge $clean")
                            }
                        }
                    }
                    else -> {}
                }
            }
            BridgeMode.CUSTOM -> {
                if (config.customBridges.isNotBlank()) {
                    lines.add("UseBridges 1")
                    config.customBridges.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                        val clean = if (line.startsWith("Bridge ")) line.substring(7) else line
                        lines.add("Bridge $clean")
                    }
                }
            }
            else -> {}
        }

        return lines.joinToString("\n") + "\n"
    }
}
