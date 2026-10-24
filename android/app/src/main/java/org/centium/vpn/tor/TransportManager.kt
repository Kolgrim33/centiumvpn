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
            // IPtProxy's port is 0 exactly when the transport has no listener, i.e. isn't running
            if (controller.port(name) <= 0) {
                onLog("[Transport] Starting $name...")
                lastErrors.remove(name)
                controller.start(name, "")
            }
            val port = controller.port(name).toInt()
            check(port > 0) { "The $name transport didn't open a local port" }
            port
        }
    }

    /** Stops every running transport not in [keep]. */
    fun stopOthers(keep: Set<String> = emptySet()) {
        synchronized(lock) {
            val controller = shared ?: return
            (BridgeLines.SUPPORTED_TRANSPORTS - keep).filter { controller.port(it) > 0 }.forEach { name ->
                try {
                    controller.stop(name)
                } catch (e: Exception) {
                    onLog("[Transport] Stopping $name failed: ${e.message}")
                }
            }
        }
    }

    // Process-wide, because the VPN service can be recreated while the process lives on
    private companion object Events : OnTransportEvents {
        // Guards Controller calls. The Go callbacks below never take it, so a callback fired
        // while stop() holds it can't deadlock.
        private val lock = Any()
        private var shared: Controller? = null

        // Last error logged per transport, so a bridge that keeps failing is logged once
        private val lastErrors = ConcurrentHashMap<String, String>()

        @Volatile
        private var logSink: (String) -> Unit = {}

        override fun connected(name: String?) {}

        override fun error(name: String?, error: Exception?) = report(name, error)

        // Despite the name, IPtProxy calls this whenever one connection through the transport
        // ends; the transport itself keeps listening until Controller.stop.
        override fun stopped(name: String?, error: Exception?) = report(name, error)

        private fun report(name: String?, error: Exception?) {
            val message = error?.message ?: return
            if (lastErrors.put(name ?: "", message) != message) logSink("[Transport] $name: $message")
        }
    }
}
