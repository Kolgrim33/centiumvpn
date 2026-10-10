package org.centium.vpn.diagnostics

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class VerificationMatrix {

    private val directClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun verifyTunnelTraffic(socksPort: Int): VerificationResult = withContext(Dispatchers.IO) {
        // 1. Primary check: check.torproject.org API directly through the active tunnel
        try {
            val req = Request.Builder()
                .url("https://check.torproject.org/api/ip")
                .header("User-Agent", "Centium-Android/1.0")
                .build()

            directClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = Gson().fromJson(body, JsonObject::class.java)
                        val isTor = json.get("IsTor")?.asBoolean ?: false
                        val ip = json.get("IP")?.asString

                        if (isTor && ip != null) {
                            val countryInfo = resolveCountry(ip, socksPort)
                            return@withContext VerificationResult(
                                isTor = true,
                                ip = ip,
                                country = countryInfo.first,
                                countryCode = countryInfo.second
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Secondary check: icanhazip validated against Onionoo Tor directory
        try {
            val req = Request.Builder()
                .url("https://icanhazip.com")
                .header("User-Agent", "Centium-Android/1.0")
                .build()

            directClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val directIp = response.body?.string()?.trim()
                    if (directIp != null && directIp.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                        val isRelay = checkOnionoo(directIp, socksPort)
                        if (isRelay) {
                            val countryInfo = resolveCountry(directIp, socksPort)
                            return@withContext VerificationResult(
                                isTor = true,
                                ip = directIp,
                                country = countryInfo.first,
                                countryCode = countryInfo.second
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        VerificationResult(isTor = false, ip = null, country = null, countryCode = null)
    }

    private fun checkOnionoo(ip: String, socksPort: Int): Boolean {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val client = OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder()
                .url("https://onionoo.torproject.org/details?search=$ip&type=relay")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    body.contains(ip)
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveCountry(ip: String, socksPort: Int): Pair<String, String> {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val client = OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder()
                .url("https://ipwho.is/$ip")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = Gson().fromJson(body, JsonObject::class.java)
                    val country = json.get("country")?.asString ?: "Tor Relay"
                    val code = json.get("country_code")?.asString ?: "--"
                    country to code
                } else {
                    "Tor Relay" to "--"
                }
            }
        } catch (_: Exception) {
            "Tor Relay" to "--"
        }
    }

    data class VerificationResult(
        val isTor: Boolean,
        val ip: String?,
        val country: String?,
        val countryCode: String?
    )
}
