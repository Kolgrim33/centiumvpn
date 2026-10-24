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

    /**
     * Applies SETCONF entries, each "Key value", in one command so Tor validates them together.
     * Throws with Tor's reason if it refuses them. jtorctl crashes on an entry without a value,
     * so use [resetConf] to clear settings.
     */
    suspend fun setConf(entries: List<String>) = withContext(Dispatchers.IO) {
        require(entries.all { ' ' in it }) { "Every SETCONF entry needs a value: $entries" }
        val conn = connection() ?: throw IllegalStateException("Tor's control connection is closed")
        conn.setConf(entries)
    }

    /** Resets settings to Tor's defaults (RESETCONF). Throws if Tor refuses. */
    suspend fun resetConf(keys: List<String>) = withContext(Dispatchers.IO) {
        val conn = connection() ?: throw IllegalStateException("Tor's control connection is closed")
        conn.resetConf(keys)
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
        // Skip one-hop directory fetches and internal (onion-service) circuits; prefer the newest.
        val hops = info.lines()
            .map { it.trim().split(" ") }
            .filter { parts ->
                parts.size >= 3 && parts[1] == "BUILT" &&
                    parts.any { it == "PURPOSE=GENERAL" } &&
                    parts.none { it.startsWith("BUILD_FLAGS=") && ("ONEHOP_TUNNEL" in it || "IS_INTERNAL" in it) } &&
                    parts[2].split(",").size >= 3
            }
            .maxByOrNull { it[0].toIntOrNull() ?: 0 }
            ?.get(2)?.split(",")
            ?: return emptyList()

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
