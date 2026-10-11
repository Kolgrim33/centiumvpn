package org.centium.vpn.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.centium.vpn.BuildConfig
import org.centium.vpn.data.ConnectionState
import org.centium.vpn.vpn.CentiumVpnService

/**
 * Finds, downloads, verifies and installs Centium updates from GitHub Releases.
 * Automatic checks run at most daily and only through Tor, so they never reveal
 * a user's real address; a manual check while disconnected goes direct.
 */
class UpdateManager(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val viaTor: Boolean) : State
        data class Downloading(val version: Version) : State
        data class Ready(val release: Release, val apk: File) : State
        data class Failed(val reason: String) : State
    }

    val prefs = UpdatePreferences(context)
    val currentVersion: String = BuildConfig.VERSION_NAME

    /** Test builds use a different package and key, so they can't take release updates. */
    val enabled: Boolean = BuildConfig.UPDATES_ENABLED

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val downloadDir = File(context.cacheDir, "updates")

    init {
        if (enabled) {
            scope.launch {
                CentiumVpnService.connectionState.collect { state ->
                    if (state == ConnectionState.CONNECTED) maybeAutoCheck()
                }
            }
        }
    }

    private fun maybeAutoCheck() {
        if (!prefs.autoUpdate) return
        if (System.currentTimeMillis() - prefs.lastCheckMillis < AUTO_INTERVAL_MS) return
        check(manual = false)
    }

    fun checkNow() = check(manual = true)

    private fun check(manual: Boolean) {
        if (!enabled || job?.isActive == true) return
        val socksPort = torSocksPort()
        // Never reveal the user's address in the background
        if (!manual && socksPort <= 0) return

        job = scope.launch {
            _state.value = State.Checking
            try {
                val client = httpClient(socksPort)
                val release = ReleaseFeed(client).fetchLatest()
                prefs.lastCheckMillis = System.currentTimeMillis()

                if (release == null || release.version.code <= BuildConfig.VERSION_CODE) {
                    _state.value = State.UpToDate(viaTor = socksPort > 0)
                    return@launch
                }

                CentiumVpnService.addLog("[Update] Version ${release.version} is available, downloading.")
                _state.value = State.Downloading(release.version)
                val downloader = UpdateDownloader(client, downloadDir)
                val checksum = downloader.fetchChecksum(release)
                val apk = downloader.download(release, checksum)
                try {
                    UpdateVerifier(context).verify(apk, release, checksum)
                } catch (e: UpdateException) {
                    apk.delete()
                    throw e
                }

                CentiumVpnService.addLog("[Update] ✓ Version ${release.version} verified and ready to install.")
                _state.value = State.Ready(release, apk)
                UpdateNotifier.notifyReady(context, release.version)
            } catch (e: Exception) {
                val reason = e.message ?: "Couldn't check for updates"
                CentiumVpnService.addLog("[Update Error] $reason")
                _state.value = if (manual) State.Failed(reason) else State.Idle
            }
        }
    }

    /**
     * Installs a ready update. Returns an intent the caller must start first when
     * Android still needs the user to allow installs from Centium.
     */
    fun install(): Intent? {
        val ready = _state.value as? State.Ready ?: return null
        if (!context.packageManager.canRequestPackageInstalls()) {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        }
        prefs.stashNotes(ready.release.version.code, ready.release.notes)
        prefs.reconnectAfterUpdate = CentiumVpnService.connectionState.value == ConnectionState.CONNECTED
        UpdateNotifier.cancel(context)
        CentiumVpnService.addLog("[Update] Installing version ${ready.release.version}.")
        scope.launch {
            try {
                UpdateInstaller.install(context, ready.apk)
            } catch (e: Exception) {
                onInstallFailed(e.message ?: "Install failed")
            }
        }
        return null
    }

    /** [reason] is null when the user cancelled; the update stays ready. */
    fun onInstallFailed(reason: String?) {
        prefs.reconnectAfterUpdate = false
        if (reason == null) return
        CentiumVpnService.addLog("[Update Error] $reason")
        _state.value = State.Failed(reason)
    }

    private fun torSocksPort(): Int {
        if (CentiumVpnService.connectionState.value != ConnectionState.CONNECTED) return -1
        return CentiumVpnService.activeServiceInstance?.torManager?.socksPort ?: -1
    }

    /** Centium is excluded from its own VPN, so Tor must be used explicitly via SOCKS. */
    private fun httpClient(socksPort: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
        if (socksPort > 0) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
        }
        return builder.build()
    }

    companion object {
        private const val AUTO_INTERVAL_MS = 20L * 60 * 60 * 1000
    }
}
