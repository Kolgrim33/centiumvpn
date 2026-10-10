package org.centium.vpn.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TorDnsResolver(
    private val torDnsHost: String = "127.0.0.1",
    private val torDnsPort: Int = 9053
) {

    suspend fun resolveQuery(queryBytes: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 4000

            val torAddress = InetAddress.getByName(torDnsHost)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, torAddress, torDnsPort)
            socket.send(sendPacket)

            val buffer = ByteArray(4096)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)

            val response = ByteArray(receivePacket.length)
            System.arraycopy(receivePacket.data, 0, response, 0, receivePacket.length)
            response
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }
}
