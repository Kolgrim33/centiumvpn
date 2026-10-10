package org.centium.vpn.vpn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var connectionJob: Job? = null
    private var supervisionJob: Job? = null

    private var tunDescriptor: ParcelFileDescriptor? = null
    private val tunConfig = TunConfiguration()

    lateinit var torManager: TorManager
        private set

    lateinit var bridge: HevTunnelBridge
        private set

    private val failClosedGuard = FailClosedGuard { alert ->
        addLog(alert)
    }

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

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    var connectedSince: Long? = null
        private set

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
            ACTION_CONNECT -> {
                val config = CentiumApplication.instance.preferencesRepository.getConfig()
                startVpn(config)
            }
            ACTION_DISCONNECT -> {
                stopVpn()
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        val vpnInterface = super.onBind(intent)
        return if (vpnInterface != null) vpnInterface else binder
    }

    override fun onRevoke() {
        addLog("[VPN Lifecycle] System revoked VPN permission.")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        activeServiceInstance = null
        stopVpn()
        super.onDestroy()
    }

    fun protectSocket(socket: Socket): Boolean {
        return protect(socket)
    }

    fun startVpn(config: CentiumConfig) {
        if (_connectionState.value.isTransitioning || _connectionState.value == ConnectionState.CONNECTED) {
            addLog("[VPN] Connection already in progress or connected.")
            return
        }

        connectionJob?.cancel()
        connectionJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // Step 1: STARTING_TOR
                updateState(ConnectionState.STARTING_TOR, "Initializing local Tor engine...")
                _bootstrapPercent.value = 0
                val torStarted = torManager.start(config)
                if (!torStarted) {
                    throw IllegalStateException("Failed to launch Tor process")
                }

                // Step 2: WAITING_FOR_BOOTSTRAP
                updateState(ConnectionState.WAITING_FOR_BOOTSTRAP, "Bootstrapping circuit consensus...")
                val bootstrapped = torManager.waitForBootstrap(config.connectionTimeoutSeconds)
                if (!bootstrapped) {
                    throw IllegalStateException("Tor circuit bootstrap timed out")
                }

                // Step 3: STARTING_BRIDGE
                updateState(ConnectionState.STARTING_BRIDGE, "Initializing TUN packet bridge...")

                // Step 4: ESTABLISHING_VPN
                updateState(ConnectionState.ESTABLISHING_VPN, "Creating virtual TUN interface...")
                val pfd = tunConfig.buildTunInterface(this@CentiumVpnService, config)
                    ?: throw IllegalStateException("Android system denied TUN interface establishment")
                tunDescriptor = pfd

                // Step 5: CONFIGURING_DNS_AND_ROUTES
                updateState(ConnectionState.CONFIGURING_DNS_AND_ROUTES, "Connecting tunnel to Tor SOCKS5...")
                val bridgeStarted = bridge.startBridge(pfd.fd, config)
                if (!bridgeStarted) {
                    throw IllegalStateException("Failed to start native hev-socks5-tunnel bridge on TUN fd")
                }
                failClosedGuard.armKillSwitch()

                // Foreground Notification
                startForeground(
                    VpnNotificationManager.NOTIFICATION_ID,
                    CentiumApplication.instance.notificationManager.buildVpnNotification(
                        state = ConnectionState.CONNECTED,
                        exitIp = null,
                        country = null
                    )
                )

                // Step 6: VERIFYING_PROTECTION (Unproxied HTTP test through TUN)
                updateState(ConnectionState.VERIFYING_PROTECTION, "Auditing Tor exit IP through tunnel...")
                val verifier = VerificationMatrix()
                val verification = verifier.verifyTunnelTraffic(config.socksPort)
                if (!verification.isTor || verification.ip == null) {
                    throw IllegalStateException("Exit verification failed: host traffic is not traversing Tor")
                }

                _publicIp.value = verification.ip
                _exitCountry.value = verification.country
                _circuit.value = torManager.controller.getCircuits()

                // Step 7: CONNECTED
                connectedSince = System.currentTimeMillis()
                updateState(ConnectionState.CONNECTED, "Protected by Centium VPN (${verification.ip})")
                addLog("[VPN] ✓ Fully connected and verified. Exit IP: ${verification.ip}")

                startSupervisionWatchdog()

            } catch (e: Exception) {
                addLog("[VPN Error] Connection failed: ${e.message}")
                updateState(ConnectionState.ERROR, e.message ?: "Connection failure")
                cleanupVpnState()
            }
        }
    }

    fun stopVpn() {
        connectionJob?.cancel()
        supervisionJob?.cancel()

        serviceScope.launch(Dispatchers.IO) {
            updateState(ConnectionState.DISCONNECTING, "Disconnecting Centium VPN...")
            cleanupVpnState()
            updateState(ConnectionState.DISCONNECTED, "Disconnected")
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupVpnState() {
        failClosedGuard.disarmKillSwitch()
        bridge.stopBridge()
        torManager.stop()

        try {
            tunDescriptor?.close()
        } catch (_: IOException) {}
        tunDescriptor = null

        connectedSince = null
        _publicIp.value = null
        _exitCountry.value = null
        _circuit.value = emptyList()
        _bootstrapPercent.value = 0
    }

    private fun startSupervisionWatchdog() {
        supervisionJob?.cancel()
        supervisionJob = serviceScope.launch(Dispatchers.IO) {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                delay(2000)

                // Update traffic counters
                val stats = bridge.getTrafficStats()
                _bytesReceived.value = stats.rxBytes
                _bytesSent.value = stats.txBytes

                // Verify Tor & Bridge health
                if (!torManager.isRunning() || !bridge.isBridgeActive()) {
                    val alertState = failClosedGuard.handleUnexpectedDrop(
                        reason = "Tor or bridge process stopped",
                        currentState = _connectionState.value
                    )
                    updateState(alertState, "Connection lost (fail-closed armed)")
                    break
                }
            }
        }
    }

    private fun updateState(state: ConnectionState, message: String) {
        _connectionState.value = state
        _statusMessage.value = message
        addLog("[State] ${state.name}: $message")

        // Update foreground notification
        if (state == ConnectionState.CONNECTED) {
            CentiumApplication.instance.notificationManager.updateNotification(
                state = state,
                exitIp = _publicIp.value,
                country = _exitCountry.value
            )
        }
    }

    fun addLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val entry = "[$timestamp] $msg"
        val current = _logs.value.toMutableList()
        if (current.size > 200) current.removeAt(0)
        current.add(entry)
        _logs.value = current
    }

    companion object {
        const val ACTION_CONNECT = "org.centium.vpn.CONNECT"
        const val ACTION_DISCONNECT = "org.centium.vpn.DISCONNECT"

        @Volatile
        var activeServiceInstance: CentiumVpnService? = null
            private set

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
