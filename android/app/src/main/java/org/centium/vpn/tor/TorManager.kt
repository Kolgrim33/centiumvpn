package org.centium.vpn.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.centium.vpn.data.CentiumConfig
import org.torproject.jni.TorService

/**
 * Runs Tor in-process via tor-android's [TorService] (bundled libtor.so).
 * Binding starts Tor; unbinding shuts it down. Control traffic uses the
 * private Unix ControlSocket TorService sets up, never a TCP port.
 */
class TorManager(
    private val context: Context,
    private val onBootstrapProgress: (Int, String) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val torrcGenerator = TorrcGenerator()

    @Volatile
    private var torService: TorService? = null
    private var serviceConnection: ServiceConnection? = null

    val controller = TorController { torService?.torControlConnection }

    /** SOCKS port Tor actually bound (Tor picks a free one), or -1 if unknown. */
    @Volatile
    var socksPort: Int = -1
        private set

    fun isRunning(): Boolean {
        val service = torService ?: return false
        return try {
            service.getInfo("version") != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun start(config: CentiumConfig): Boolean {
        stop()

        onLog("[Tor] Writing torrc...")
        val torrc = try {
            torrcGenerator.generateTorrc(config)
        } catch (e: IllegalArgumentException) {
            onLog("[Tor Error] ${e.message}")
            return false
        }
        withContext(Dispatchers.IO) { TorService.getTorrc(context).writeText(torrc) }

        onLog("[Tor] Starting embedded Tor ${TorService.VERSION_NAME}...")
        val connected = CompletableDeferred<TorService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? TorService.LocalBinder)?.service ?: return
                torService = service
                connected.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                torService = null
            }
        }
        serviceConnection = connection

        val bound = context.bindService(
            Intent(context, TorService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound) {
            onLog("[Tor Error] Could not bind TorService.")
            serviceConnection = null
            return false
        }

        return withTimeoutOrNull(15_000) { connected.await() } != null
    }

    suspend fun waitForBootstrap(timeoutSeconds: Int = 60): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        var lastPercent = -1

        while (System.currentTimeMillis() < deadline) {
            val service = torService
            if (service == null) {
                onLog("[Tor Error] Tor service stopped while bootstrapping.")
                return@withContext false
            }

            val phase = controller.getBootstrapPhase()
            if (phase != null && phase.percent != lastPercent) {
                lastPercent = phase.percent
                onBootstrapProgress(phase.percent, phase.summary)
                onLog("[Tor] Bootstrapped ${phase.percent}%: ${phase.summary}")
            }

            if (phase != null && phase.percent >= 100) {
                socksPort = service.socksPort
                if (socksPort > 0) {
                    onLog("[Tor] SOCKS5 listening on 127.0.0.1:$socksPort")
                    return@withContext true
                }
            }

            delay(500)
        }

        onLog("[Tor Warning] Bootstrap timed out after ${timeoutSeconds}s.")
        false
    }

    fun stop() {
        serviceConnection?.let {
            try {
                context.unbindService(it)
            } catch (_: IllegalArgumentException) {}
        }
        serviceConnection = null
        torService = null
        socksPort = -1
    }
}
