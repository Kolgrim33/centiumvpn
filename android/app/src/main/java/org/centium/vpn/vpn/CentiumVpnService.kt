package org.centium.vpn.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.centium.vpn.CentiumApplication
import org.centium.vpn.bridge.HevTunnelBridge
import org.centium.vpn.data.CentiumConfig
import org.centium.vpn.data.CircuitNode
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.diagnostics.VerificationMatrix
import org.centium.vpn.notifications.VpnNotificationManager
import org.centium.vpn.security.FailClosedGuard
import org.centium.vpn.tor.TorManager

class CentiumVpnService : VpnService() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectionJob: Job? = null
    private var supervisionJob: Job? = null
    private var isForeground = false

    private var tunDescriptor: ParcelFileDescriptor? = null
    private val tunConfig = TunConfiguration()

    lateinit var torManager: TorManager
        private set

    lateinit var bridge: HevTunnelBridge
        private set

    private val failClosedGuard = FailClosedGuard { alert ->
        addLog(alert)
    }

    inner class LocalBinder : Binder() {
        fun getService(): CentiumVpnService = this@CentiumVpnService
    }

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this

        torManager = TorManager(
            context = this,
            onBootstrapProgress = { percent, msg ->
                _bootstrapPercent.value = percent
                _statusMessage.value = msg
            },
            onLog = { log -> addLog(log) }
        )

        bridge = HevTunnelBridge(this) { log -> addLog(log) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> stopVpn()
            // SERVICE_INTERFACE / null: started by Android's Always-on VPN
            ACTION_CONNECT, SERVICE_INTERFACE, null -> {
                if (!enterForeground()) return START_NOT_STICKY
                startVpn(CentiumApplication.instance.preferencesRepository.getConfig())
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        val vpnInterface = super.onBind(intent)
        return vpnInterface ?: binder
    }

    override fun onRevoke() {
        addLog("[VPN Lifecycle] System revoked VPN permission.")
        stopVpn()
    }

    override fun onDestroy() {
        activeServiceInstance = null
        connectionJob?.cancel()
        supervisionJob?.cancel()
        serviceScope.cancel()
        cleanupVpnState()
        if (_connectionState.value != ConnectionState.ERROR) {
            setState(ConnectionState.DISCONNECTED, "Disconnected")
        }
        super.onDestroy()
    }

    fun protectSocket(socket: Socket): Boolean {
        return protect(socket)
    }

    /** Must run within a few seconds of startForegroundService(), before any slow work. */
    private fun enterForeground(): Boolean {
        val notification = CentiumApplication.instance.notificationManager.buildVpnNotification(
            state = _connectionState.value.takeIf { it.isConnected || it.isTransitioning }
                ?: ConnectionState.STARTING_TOR,
            exitIp = _publicIp.value,
            country = _exitCountry.value
        )
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    VpnNotificationManager.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                )
            } else {
                startForeground(VpnNotificationManager.NOTIFICATION_ID, notification)
            }
            isForeground = true
            true
        } catch (e: Exception) {
            addLog("[VPN Error] Could not enter foreground: ${e.message}")
            setState(ConnectionState.ERROR, "VPN permission required — open Centium and tap Connect")
            stopSelf()
            false
        }
    }

    fun startVpn(config: CentiumConfig) {
        val current = _connectionState.value
        if (current.isTransitioning || current == ConnectionState.CONNECTED) {
            addLog("[VPN] Connection already in progress or connected.")
            return
        }

        // Set synchronously so a second tap can't slip past the guard above
        updateState(ConnectionState.STARTING_TOR, "Initializing local Tor engine...")
        _bootstrapPercent.value = 0

        supervisionJob?.cancel()
        connectionJob?.cancel()
        connectionJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // Step 1: STARTING_TOR
                if (!torManager.start(config)) {
                    throw IllegalStateException("Failed to start Tor (see Logs)")
                }

                // Step 2: WAITING_FOR_BOOTSTRAP
                updateState(ConnectionState.WAITING_FOR_BOOTSTRAP, "Bootstrapping circuit consensus...")
                if (!torManager.waitForBootstrap(config.connectionTimeoutSeconds)) {
                    throw IllegalStateException("Tor could not connect to the network (timed out)")
                }

                // Step 3: STARTING_BRIDGE
                updateState(ConnectionState.STARTING_BRIDGE, "Initializing TUN packet bridge...")
                bridge.stopBridge()

                // Step 4: ESTABLISHING_VPN
                updateState(ConnectionState.ESTABLISHING_VPN, "Creating virtual TUN interface...")
                val pfd = tunConfig.buildTunInterface(this@CentiumVpnService, config)
                    ?: throw IllegalStateException("VPN permission missing — open Centium and tap Connect")
                val previous = tunDescriptor
                tunDescriptor = pfd
                closeQuietly(previous)

                // Step 5: CONFIGURING_DNS_AND_ROUTES
                updateState(ConnectionState.CONFIGURING_DNS_AND_ROUTES, "Connecting tunnel to Tor SOCKS5...")
                if (!bridge.startBridge(pfd.fd, torManager.socksPort, config)) {
                    throw IllegalStateException("Failed to start native hev-socks5-tunnel bridge on TUN fd")
                }
                failClosedGuard.armKillSwitch()
                _tunnelHeld.value = true

                // Step 6: VERIFYING_PROTECTION
                updateState(ConnectionState.VERIFYING_PROTECTION, "Verifying Tor exit...")
                val verification = VerificationMatrix().verifyTunnelTraffic(torManager.socksPort)
                val status = if (verification.isTor) {
                    _publicIp.value = verification.ip
                    _exitCountry.value = verification.country
                    addLog("[VPN] ✓ Exit verified as Tor: ${verification.ip} (${verification.country})")
                    "Protected via Tor (${verification.ip})"
                } else {
                    // All traffic is already captured by the TUN and can only leave via Tor,
                    // so an unreachable check service is not a leak; just report it.
                    addLog("[VPN Warning] Could not reach check.torproject.org to confirm the exit IP.")
                    "Routing through Tor (exit check unavailable)"
                }
                _circuit.value = torManager.controller.getCircuits()

                // Step 7: CONNECTED
                connectedSince = System.currentTimeMillis()
                updateState(ConnectionState.CONNECTED, status)

                startSupervisionWatchdog(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("[VPN Error] Connection failed: ${e.message}")
                cleanupVpnState()
                updateState(ConnectionState.ERROR, e.message ?: "Connection failure")
                withContext(Dispatchers.Main) { leaveForeground() }
            }
        }
    }

    fun stopVpn() {
        serviceScope.launch {
            updateState(ConnectionState.DISCONNECTING, "Disconnecting Centium VPN...")
            supervisionJob?.cancelAndJoin()
            connectionJob?.cancelAndJoin()
            withContext(Dispatchers.IO) { cleanupVpnState() }
            updateState(ConnectionState.DISCONNECTED, "Disconnected")
            leaveForeground()
        }
    }

    private fun leaveForeground() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf()
    }

    private fun cleanupVpnState() {
        failClosedGuard.disarmKillSwitch()
        bridge.stopBridge()
        torManager.stop()

        closeQuietly(tunDescriptor)
        tunDescriptor = null
        _tunnelHeld.value = false

        connectedSince = null
        _publicIp.value = null
        _exitCountry.value = null
        _circuit.value = emptyList()
        _bootstrapPercent.value = 0
        _bytesReceived.value = 0
        _bytesSent.value = 0
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        try {
            pfd?.close()
        } catch (_: IOException) {}
    }

    private fun startSupervisionWatchdog(config: CentiumConfig) {
        supervisionJob?.cancel()
        supervisionJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(2000)

                val stats = bridge.getTrafficStats()
                _bytesReceived.value = stats.rxBytes
                _bytesSent.value = stats.txBytes

                if (!torManager.isRunning() || !bridge.isBridgeActive()) {
                    val alertState = failClosedGuard.handleUnexpectedDrop(
                        reason = "Tor or bridge process stopped",
                        currentState = _connectionState.value
                    )
                    if (config.killSwitch) {
                        // Keep the TUN up with nothing reading it: traffic is dropped, not leaked.
                        bridge.stopBridge()
                        torManager.stop()
                        updateState(alertState, "Connection lost — traffic blocked by kill switch. Disconnect or reconnect.")
                    } else {
                        cleanupVpnState()
                        updateState(alertState, "Connection lost")
                        withContext(Dispatchers.Main) { leaveForeground() }
                    }
                    break
                }
            }
        }
    }

    private fun updateState(state: ConnectionState, message: String) {
        setState(state, message)
        if (isForeground) {
            CentiumApplication.instance.notificationManager.updateNotification(
                state = state,
                exitIp = _publicIp.value,
                country = _exitCountry.value
            )
        }
    }

    companion object {
        const val ACTION_CONNECT = "org.centium.vpn.CONNECT"
        const val ACTION_DISCONNECT = "org.centium.vpn.DISCONNECT"

        // Process-wide state so the UI can observe it whether or not the service is running.
        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _statusMessage = MutableStateFlow("Ready to connect")
        val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

        private val _bootstrapPercent = MutableStateFlow(0)
        val bootstrapPercent: StateFlow<Int> = _bootstrapPercent.asStateFlow()

        private val _publicIp = MutableStateFlow<String?>(null)
        val publicIp: StateFlow<String?> = _publicIp.asStateFlow()

        private val _exitCountry = MutableStateFlow<String?>(null)
        val exitCountry: StateFlow<String?> = _exitCountry.asStateFlow()

        private val _circuit = MutableStateFlow<List<CircuitNode>>(emptyList())
        val circuit: StateFlow<List<CircuitNode>> = _circuit.asStateFlow()

        private val _bytesReceived = MutableStateFlow(0L)
        val bytesReceived: StateFlow<Long> = _bytesReceived.asStateFlow()

        private val _bytesSent = MutableStateFlow(0L)
        val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()

        /** True while the TUN is up — including a fail-closed drop with Tor down. */
        private val _tunnelHeld = MutableStateFlow(false)
        val tunnelHeld: StateFlow<Boolean> = _tunnelHeld.asStateFlow()

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs: StateFlow<List<String>> = _logs.asStateFlow()

        @Volatile
        var connectedSince: Long? = null
            private set

        @Volatile
        var activeServiceInstance: CentiumVpnService? = null
            private set

        private fun setState(state: ConnectionState, message: String) {
            _connectionState.value = state
            _statusMessage.value = message
            addLog("[State] ${state.name}: $message")
        }

        @Synchronized
        fun addLog(msg: String) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val current = _logs.value.toMutableList()
            if (current.size > 300) current.removeAt(0)
            current.add("[$timestamp] $msg")
            _logs.value = current
        }

        fun start(context: Context) {
            val intent = Intent(context, CentiumVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CentiumVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
