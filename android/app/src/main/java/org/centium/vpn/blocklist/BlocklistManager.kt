package org.centium.vpn.blocklist

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.centium.vpn.BuildConfig
import org.centium.vpn.data.CentiumConfig
import org.centium.vpn.data.PreferencesRepository

/**
 * Composes the domain blocklist the native mapdns reads, and refreshes it over Tor.
 *
 * Two categories ship as bundled assets: a security list (trackers and malware) and an ad-network
 * list. The security list is used on every flavor; the ad list only where [BuildConfig.ADS_BLOCKING_ALLOWED]
 * is set (not Play, where blocking ads in other apps risks removal). A newer copy fetched over Tor,
 * if present, is preferred over the bundled asset. The user's allowlist is subtracted. The result is
 * written as plain lowercase domains, one per line, which the tunnel loads when it starts.
 */
class BlocklistManager(
    private val context: Context,
    private val prefs: PreferencesRepository
) {
    private val dir = File(context.filesDir, "blocklists").apply { mkdirs() }
    private val activeFile = File(dir, "active.txt")

    /**
     * Writes the active blocklist and returns its file, or null when blocking is off or the list is
     * empty (so callers can skip wiring it into the tunnel).
     */
    fun activeListFile(config: CentiumConfig): File? {
        if (!config.adBlockEnabled) return null

        val domains = BlocklistRules.compose(
            security = readCategory(SECURITY),
            ads = readCategory(ADS),
            includeAds = BuildConfig.ADS_BLOCKING_ALLOWED,
            allowlistRaw = prefs.adBlockAllowlist
        )
        if (domains.isEmpty()) return null

        activeFile.writeText(domains.joinToString("\n", postfix = "\n"))
        return activeFile
    }

    /** Prefer a copy downloaded over Tor; fall back to the bundled asset. */
    private fun readCategory(name: String): List<String> {
        val downloaded = File(dir, "$name.dl")
        if (downloaded.exists() && downloaded.length() > 0) {
            return runCatching { downloaded.readLines() }.getOrDefault(emptyList())
        }
        return runCatching {
            context.assets.open("blocklists/$name.txt").bufferedReader().use { it.readLines() }
        }.getOrDefault(emptyList())
    }

    /**
     * Refreshes the lists over Tor at most once per [UPDATE_INTERVAL_MS]. No-op unless blocking is on
     * and Tor's SOCKS port is available. Safe to call on a background thread after connecting.
     */
    fun maybeUpdate(socksPort: Int) {
        if (socksPort <= 0) return
        val age = System.currentTimeMillis() - prefs.blocklistUpdatedAt
        if (prefs.blocklistUpdatedAt != 0L && age < UPDATE_INTERVAL_MS) return

        val client = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
            .build()

        val categories = buildList {
            add(SECURITY)
            if (BuildConfig.ADS_BLOCKING_ALLOWED) add(ADS)
        }

        var anyOk = false
        for (name in categories) {
            val ok = runCatching {
                client.newCall(Request.Builder().url("$BASE_URL/$name.txt").build()).execute().use { res ->
                    val body = res.body?.string()
                    if (res.isSuccessful && !body.isNullOrBlank()) {
                        File(dir, "$name.dl").writeText(body)
                        true
                    } else {
                        false
                    }
                }
            }.getOrDefault(false)
            anyOk = anyOk || ok
        }
        if (anyOk) prefs.blocklistUpdatedAt = System.currentTimeMillis()
    }

    companion object {
        private const val SECURITY = "security"
        private const val ADS = "ads"
        private const val BASE_URL = "https://centium.web.app/blocklists"
        private val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(3)
    }
}
