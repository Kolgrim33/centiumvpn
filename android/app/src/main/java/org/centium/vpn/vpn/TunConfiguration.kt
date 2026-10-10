package org.centium.vpn.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import org.centium.vpn.data.CentiumConfig

class TunConfiguration {

    fun buildTunInterface(
        service: VpnService,
        config: CentiumConfig
    ): ParcelFileDescriptor? {
        val builder = service.Builder().apply {
            setSession("Centium VPN")
            setMtu(MTU)

            // Capture all IPv4 traffic
            addAddress(TUN_IPV4, 32)
            addRoute("0.0.0.0", 0)

            // Always capture IPv6 too so it can never bypass the tunnel. Whether it
            // is forwarded to Tor or dropped is decided by the bridge config.
            addAddress(TUN_IPV6, 128)
            addRoute("::", 0)

            // DNS goes to a virtual resolver inside the tunnel (hev mapdns), which
            // hands hostnames to Tor so resolution happens at the exit relay.
            addDnsServer(MAPDNS_ADDRESS)

            // Exclude Centium's own process so Tor's relay connections don't loop
            addDisallowedApplication(service.packageName)

            for (pkg in config.disallowPackages) {
                try {
                    addDisallowedApplication(pkg)
                } catch (_: Exception) {}
            }
        }

        return builder.establish()
    }

    companion object {
        const val MTU = 8500
        const val TUN_IPV4 = "198.18.0.1"
        const val TUN_IPV6 = "fc00::1"
        const val MAPDNS_ADDRESS = "198.18.0.2"
    }
}
