package org.centium.vpn.tor

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.centium.vpn.data.CircuitNode

class TorController(
    private val host: String = "127.0.0.1",
    private val port: Int = 9051
) {

    suspend fun sendCommand(command: String): List<String> = withContext(Dispatchers.IO) {
        val responses = mutableListOf<String>()
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 2000)
            socket.soTimeout = 3000

            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

            // Authenticate (empty string for CookieAuthentication 0)
            writer.print("AUTHENTICATE \"\"\r\n")
            writer.flush()

            val authResp = reader.readLine()
            if (authResp == null || !authResp.startsWith("250")) {
                throw IllegalStateException("Tor ControlPort authentication failed: $authResp")
            }

            // Send actual command
            writer.print("$command\r\n")
            writer.flush()

            while (true) {
                val line = reader.readLine() ?: break
                responses.add(line)
                if (line.startsWith("250 OK") || line.startsWith("250 ") || line.startsWith("55") || line.startsWith("51")) {
                    break
                }
            }
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
        responses
    }

    suspend fun signalNewnym(): Boolean {
        return try {
            val resp = sendCommand("SIGNAL NEWNYM")
            resp.any { it.startsWith("250") }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getBootstrapPhase(): Int {
        return try {
            val resp = sendCommand("GETINFO status/bootstrap-phase")
            for (line in resp) {
                val match = Regex("PROGRESS=(\\d+)").find(line)
                if (match != null) {
                    return match.groupValues[1].toIntOrNull() ?: 0
                }
            }
            0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun getCircuits(): List<CircuitNode> = withContext(Dispatchers.IO) {
        val nodes = mutableListOf<CircuitNode>()
        try {
            val resp = sendCommand("GETINFO circuit-status")
            // Parse circuit lines: 1 BUILT $FINGERPRINT~NICKNAME,...
            for (line in resp) {
                if (line.contains("BUILT")) {
                    val parts = line.split(" ")
                    if (parts.size >= 3) {
                        val pathParts = parts[2].split(",")
                        for ((index, hop) in pathParts.withIndex()) {
                            val cleanHop = hop.trim().trimStart('$')
                            val (fp, nick) = if (cleanHop.contains("~")) {
                                val split = cleanHop.split("~")
                                split[0] to split.getOrElse(1) { "Relay" }
                            } else {
                                cleanHop to "Relay"
                            }
                            val role = when (index) {
                                0 -> CircuitNode.Role.Guard
                                pathParts.size - 1 -> CircuitNode.Role.Exit
                                else -> CircuitNode.Role.Middle
                            }
                            nodes.add(
                                CircuitNode(
                                    role = role,
                                    fingerprint = fp,
                                    nickname = nick
                                )
                            )
                        }
                    }
                    if (nodes.isNotEmpty()) break
                }
            }
        } catch (_: Exception) {}
        nodes
    }
}
