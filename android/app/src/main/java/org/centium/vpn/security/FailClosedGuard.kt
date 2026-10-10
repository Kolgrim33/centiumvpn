package org.centium.vpn.security

import org.centium.vpn.data.ConnectionState

class FailClosedGuard(
    private val onLeakAlert: (String) -> Unit
) {

    @Volatile
    private var isTunnelArmed = false

    fun armKillSwitch() {
        isTunnelArmed = true
    }

    fun disarmKillSwitch() {
        isTunnelArmed = false
    }

    fun isArmed(): Boolean = isTunnelArmed

    fun handleUnexpectedDrop(reason: String, currentState: ConnectionState): ConnectionState {
        if (!isTunnelArmed) return ConnectionState.DISCONNECTED
        onLeakAlert("[Fail-Closed Alert] Tunnel dropped unexpectedly: $reason. Freezing traffic to prevent cleartext leakage.")
        return ConnectionState.ERROR
    }

    fun shouldDropUnsupportedPacket(protocol: String): Boolean {
        // Tor does not route arbitrary non-DNS UDP (WebRTC/QUIC).
        // Return true to drop safely and prevent leakage over physical network.
        return when (protocol.uppercase()) {
            "UDP" -> true
            "ICMP" -> false
            else -> false
        }
    }
}
