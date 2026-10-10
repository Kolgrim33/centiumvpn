package org.centium.vpn.bridge

import android.content.Context
import hev.htproxy.TProxyService
import java.io.File
import org.centium.vpn.data.CentiumConfig
import org.centium.vpn.vpn.TunConfiguration

class HevTunnelBridge(
    private val context: Context,
    private val onLog: (String) -> Unit
) {
    private var isRunning = false
    private val configFile: File by lazy { File(context.filesDir, "hev-socks5-tunnel.yml") }

    fun startBridge(tunFd: Int, socksPort: Int, config: CentiumConfig): Boolean {
        onLog("[Bridge] Generating native TUN-to-SOCKS5 bridge configuration...")

        // Without an IPv6 address on the netif, lwIP drops IPv6 packets (block mode)
        val ipv6Line = if (config.blockIpv6) "" else "\n  ipv6: '${TunConfiguration.TUN_IPV6}'"

        // Tor's SOCKS port has no UDP ASSOCIATE, so non-DNS UDP fails closed.
        // DNS is answered by mapdns with fake IPs; connections to those are sent
        // to Tor as SOCKS5 hostname requests, so lookups never leave via the ISP.
        val yaml = """
tunnel:
  mtu: ${TunConfiguration.MTU}
  ipv4: ${TunConfiguration.TUN_IPV4}$ipv6Line

socks5:
  port: $socksPort
  address: 127.0.0.1
  udp: 'udp'

mapdns:
  address: ${TunConfiguration.MAPDNS_ADDRESS}
  port: 53
  network: 100.64.0.0
  netmask: 255.192.0.0
  cache-size: 10000

misc:
  task-stack-size: 81920
  connect-timeout: 30000
  tcp-read-write-timeout: 300000
  log-level: warn
"""

        configFile.writeText(yaml)

        onLog("[Bridge] Starting TProxyService on TUN fd: $tunFd -> SOCKS 127.0.0.1:$socksPort")
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
            false
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
