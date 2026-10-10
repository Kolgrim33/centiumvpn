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
            setMtu(1500)

            // IPv4 Virtual Address & Default Routing Table
            addAddress("198.18.0.1", 15)
            addRoute("0.0.0.0", 0)

            // DNS: Point to local loopback (intercepted & routed to Tor DNSPort 9053)
            addDnsServer("127.0.0.1")

            // IPv6 Handling
            if (!config.blockIpv6) {
                try {
                    addAddress("fc00::1", 128)
                    addRoute("::", 0)
                } catch (_: Exception) {}
            } else {
                // Route IPv6 into blackhole / drop path to prevent direct IPv6 egress leak
                try {
                    addAddress("2001:db8::1", 128)
                    addRoute("2000::", 3)
                } catch (_: Exception) {}
            }

            // Exclude Centium's own process from VPN to protect Tor's upstream relay connections
            try {
                addDisallowedApplication(service.packageName)
            } catch (_: Exception) {}

            // Per-app routing if configured
            for (pkg in config.disallowPackages) {
                try {
                    addDisallowedApplication(pkg)
                } catch (_: Exception) {}
            }

            setBlocking(false)
        }

        return builder.establish()
    }
}
