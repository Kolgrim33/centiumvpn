package org.centium.vpn.bridge

import android.content.Context
import hev.htproxy.TProxyService
import java.io.File
import org.centium.vpn.data.CentiumConfig

class HevTunnelBridge(
    private val context: Context,
    private val onLog: (String) -> Unit
) {
    private var isRunning = false
    private val configFile: File by lazy { File(context.filesDir, "hev-socks5-tunnel.yml") }

    fun startBridge(tunFd: Int, config: CentiumConfig): Boolean {
        onLog("[Bridge] Generating native TUN-to-SOCKS5 bridge configuration...")

        val yaml = """
            tunnel:
              mtu: 1500
              ipv4: 198.18.0.1
              ipv6: "fc00::1"

            socks5:
              port: ${config.socksPort}
              address: 127.0.0.1
              udp: 'udp'

            misc:
              task-stack-size: 81920
              connect-timeout: 30000
              read-write-timeout: 60000
              log-level: warn
              limit-nofile: 65535
        """.trimIndent()

        configFile.writeText(yaml)

        onLog("[Bridge] Starting TProxyService on TUN fd: $tunFd...")
        return try {
            val started = TProxyService.TProxyStartService(configFile.absolutePath, tunFd)
            isRunning = started
            if (started) {
                onLog("[Bridge] ✓ hev-socks5-tunnel active on virtual device.")
            } else {
                onLog("[Bridge Error] TProxyService.TProxyStartService returned false.")
            }
            started
        } catch (e: Throwable) {
            onLog("[Bridge Error] Failed to invoke native TProxyService: ${e.message}")
            false
        }
    }

    fun stopBridge() {
        if (!isRunning) return
        onLog("[Bridge] Stopping native tunnel bridge...")
        try {
            TProxyService.TProxyStopService()
        } catch (_: Throwable) {}
        isRunning = false
    }

    fun isBridgeActive(): Boolean {
        return try {
            TProxyService.TProxyIsRunning()
        } catch (_: Throwable) {
            isRunning
        }
    }

    fun getTrafficStats(): TrafficStats {
        return try {
            val stats = TProxyService.TProxyGetStats()
            if (stats.size >= 4) {
                TrafficStats(
                    txPackets = stats[0],
                    txBytes = stats[1],
                    rxPackets = stats[2],
                    rxBytes = stats[3]
                )
            } else {
                TrafficStats()
            }
        } catch (_: Throwable) {
            TrafficStats()
        }
    }

    data class TrafficStats(
        val txPackets: Long = 0,
        val txBytes: Long = 0,
        val rxPackets: Long = 0,
        val rxBytes: Long = 0
    )
}
