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
    private val transports = TransportManager(context, onLog)

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

        val service = withTimeoutOrNull(15_000) { connected.await() } ?: return false

        // Tor starts with DisableNetwork 1; routes are applied over the control connection
        val control = withTimeoutOrNull(15_000) {
            while (service.torControlConnection == null) delay(100)
            true
        }
        if (control == null) onLog("[Tor Error] Tor's control connection didn't come up.")
        return control != null
    }

    /**
     * Puts the running Tor onto [route]: starts the transports it needs, pauses the network,
     * clears the previous route, applies the new one (which turns the network back on), then
     * stops transports no longer used. Pausing first means Tor never connects with a
     * half-applied route. Throws if any step fails.
     */
    suspend fun applyRoute(route: Route, config: CentiumConfig) = withContext(Dispatchers.IO) {
        val needed = torrcGenerator.transportsFor(route, config)
        val conf = torrcGenerator.routeConf(route, config, transports.ensureRunning(needed))
        controller.setConf(listOf("DisableNetwork 1"))
        controller.resetConf(torrcGenerator.routeKeys)
        controller.setConf(conf)
        transports.stopOthers(keep = needed)
    }

    /** Polls bootstrap until [watch] decides; throws if Tor itself goes away meanwhile. */
    suspend fun waitForBootstrap(watch: BootstrapWatch): BootstrapWatch.Verdict =
        withContext(Dispatchers.IO) { pollBootstrap(watch) }

    private suspend fun pollBootstrap(watch: BootstrapWatch): BootstrapWatch.Verdict {
        val started = System.currentTimeMillis()
        var lastPercent = -1
        var doneAt = 0L

        while (true) {
            val service = torService ?: throw IllegalStateException("Tor stopped while connecting")

            val phase = controller.getBootstrapPhase()
            if (phase != null && phase.percent != lastPercent) {
                lastPercent = phase.percent
                onBootstrapProgress(phase.percent, phase.summary)
                onLog("[Tor] Bootstrapped ${phase.percent}%: ${phase.summary}")
            }

            val now = System.currentTimeMillis()
            when (val verdict = watch.verdict(phase?.percent ?: 0, now - started)) {
                BootstrapWatch.Verdict.WAIT -> {}
                BootstrapWatch.Verdict.DONE -> {
                    socksPort = service.socksPort
                    if (socksPort > 0) {
                        onLog("[Tor] SOCKS5 listening on 127.0.0.1:$socksPort")
                        return verdict
                    }
                    // Bootstrapped, but TorService hasn't reported the SOCKS port yet
                    if (doneAt == 0L) doneAt = now
                    if (now - doneAt > 15_000) {
                        onLog("[Tor Error] Tor bootstrapped but never reported its SOCKS port.")
                        return BootstrapWatch.Verdict.TIMED_OUT
                    }
                }
                else -> return verdict
            }

            delay(500)
        }
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
        transports.stopOthers()
    }
}
