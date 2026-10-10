package org.centium.vpn.tor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.freehaven.tor.control.TorControlConnection
import org.centium.vpn.data.CircuitNode

/** Tor control-protocol queries over TorService's authenticated connection. */
class TorController(
    private val connection: () -> TorControlConnection?
) {

    data class BootstrapPhase(val percent: Int, val summary: String)

    private suspend fun getInfo(key: String): String? = withContext(Dispatchers.IO) {
        try {
            connection()?.getInfo(key)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signalNewnym(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = connection() ?: return@withContext false
            conn.signal("NEWNYM")
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Parses e.g. `NOTICE BOOTSTRAP PROGRESS=75 TAG=enough_dirinfo SUMMARY="Loaded enough directory info"`. */
    suspend fun getBootstrapPhase(): BootstrapPhase? {
        val info = getInfo("status/bootstrap-phase") ?: return null
        val percent = Regex("PROGRESS=(\\d+)").find(info)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val summary = Regex("SUMMARY=\"([^\"]*)\"").find(info)?.groupValues?.get(1)
            ?: "Bootstrapping circuit ($percent%)"
        return BootstrapPhase(percent, summary)
    }

    suspend fun getCircuits(): List<CircuitNode> {
        val info = getInfo("circuit-status") ?: return emptyList()
        // Lines look like: 7 BUILT $FP~nick,$FP~nick,$FP~nick BUILD_FLAGS=... PURPOSE=GENERAL ...
        val line = info.lines().firstOrNull {
            it.contains(" BUILT ") && it.contains("PURPOSE=GENERAL")
        } ?: return emptyList()

        val parts = line.trim().split(" ")
        if (parts.size < 3) return emptyList()
        val hops = parts[2].split(",")

        return hops.mapIndexed { index, hop ->
            val clean = hop.trim().trimStart('$')
            val fp = clean.substringBefore('~')
            val nick = if (clean.contains('~')) clean.substringAfter('~') else "Relay"
            val role = when (index) {
                0 -> CircuitNode.Role.Guard
                hops.size - 1 -> CircuitNode.Role.Exit
                else -> CircuitNode.Role.Middle
            }
            CircuitNode(role = role, fingerprint = fp, nickname = nick)
        }
    }
}
