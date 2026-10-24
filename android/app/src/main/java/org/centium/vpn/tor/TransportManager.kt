package org.centium.vpn.tor

import IPtProxy.Controller
import IPtProxy.OnTransportEvents
import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs pluggable transports (Snowflake, obfs4, WebTunnel) through IPtProxy. Each started
 * transport listens on a local SOCKS port that Tor's ClientTransportPlugin points at.
 * IPtProxy allows only one Controller per process, so this is the only place that touches it.
 * The transports run in Centium's own process, which the TUN excludes, so their traffic
 * never loops back into the tunnel.
 */
class TransportManager(
    context: Context,
    private val onLog: (String) -> Unit
) {
    private val stateDir = File(context.noBackupFilesDir, "pt_state")

    private val controller: Controller
        get() = synchronized(lock) {
            logSink = onLog
            shared ?: run {
                stateDir.mkdirs()
                Controller(stateDir.path, true, false, "WARN", Events).also { shared = it }
            }
        }

    /** Starts [names] if needed and returns each one's local port. Throws if one won't start. */
    fun ensureRunning(names: Set<String>): Map<String, Int> = synchronized(lock) {
        val controller = controller
        names.associateWith { name ->
            if (name !in running) {
                onLog("[Transport] Starting $name...")
                controller.start(name, "")
                running.add(name)
            }
            val port = controller.port(name).toInt()
            check(port > 0) { "The $name transport didn't open a local port" }
            port
        }
    }

    /** Stops every running transport not in [keep]. */
    fun stopOthers(keep: Set<String> = emptySet()) {
        synchronized(lock) {
            val stale = running - keep
            if (stale.isEmpty()) return
            val controller = controller
            stale.forEach { name ->
                try {
                    controller.stop(name)
                } catch (e: Exception) {
                    onLog("[Transport] Stopping $name failed: ${e.message}")
                }
                running.remove(name)
            }
        }
    }

    // Process-wide, because the VPN service can be recreated while the process lives on
    private companion object Events : OnTransportEvents {
        // Guards start/stop sequences. The Go callbacks below never take it, so a callback
        // fired while stop() holds it can't deadlock.
        private val lock = Any()
        private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()
        private var shared: Controller? = null

        @Volatile
        private var logSink: (String) -> Unit = {}

        override fun connected(name: String?) {}

        override fun error(name: String?, error: Exception?) {
            logSink("[Transport] $name: ${error?.message}")
        }

        override fun stopped(name: String?, error: Exception?) {
            running.remove(name)
            if (error != null) logSink("[Transport] $name stopped: ${error.message}")
        }
    }
}
