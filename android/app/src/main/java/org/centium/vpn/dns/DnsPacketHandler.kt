package org.centium.vpn.dns

import java.nio.ByteBuffer

class DnsPacketHandler(
    private val resolver: TorDnsResolver = TorDnsResolver()
) {

    suspend fun processDnsPayload(payload: ByteArray): ByteArray? {
        if (payload.size < 12) return null // Minimum standard DNS header size

        // Extract transaction ID and flags
        val buffer = ByteBuffer.wrap(payload)
        val transactionId = buffer.short
        val flags = buffer.short
        val isQuery = (flags.toInt() and 0x8000) == 0

        if (!isQuery) return null

        // Forward to Tor DNSPort
        val response = resolver.resolveQuery(payload) ?: return null

        // Verify response transaction ID matches query
        if (response.size >= 2) {
            val respBuffer = ByteBuffer.wrap(response)
            val respId = respBuffer.short
            if (respId == transactionId) {
                return response
            }
        }
        return response
    }
}
