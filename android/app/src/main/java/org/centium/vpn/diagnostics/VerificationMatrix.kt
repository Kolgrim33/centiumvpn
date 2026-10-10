package org.centium.vpn.diagnostics

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Verifies the exit through Tor's SOCKS port — the same path hev-socks5-tunnel
 * forwards TUN traffic to. Centium's own process is excluded from the VPN (to
 * keep Tor's relay connections out of the tunnel), so a plain request from here
 * would bypass Tor and always see the real IP.
 */
class VerificationMatrix {

    suspend fun verifyTunnelTraffic(socksPort: Int, attempts: Int = 3): VerificationResult =
        withContext(Dispatchers.IO) {
            if (socksPort <= 0) return@withContext VerificationResult.FAILED

            val client = torClient(socksPort)
            repeat(attempts) { attempt ->
                checkTorProject(client)?.let { ip ->
                    val (country, code) = resolveCountry(client, ip)
                    return@withContext VerificationResult(true, ip, country, code)
                }
                if (attempt < attempts - 1) delay(2000)
            }
            VerificationResult.FAILED
        }

    private fun torClient(socksPort: Int): OkHttpClient {
        // OkHttp leaves hostnames unresolved for SOCKS proxies, so Tor resolves them
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Returns the exit IP if check.torproject.org confirms it is a Tor exit. */
    private suspend fun checkTorProject(client: OkHttpClient): String? {
        return try {
            val req = Request.Builder()
                .url("https://check.torproject.org/api/ip")
                .header("User-Agent", "Centium-Android/1.0")
                .build()
            client.newCall(req).await().use { response ->
                if (!response.isSuccessful) return null
                val json = Gson().fromJson(response.body?.string(), JsonObject::class.java)
                val isTor = json?.get("IsTor")?.asBoolean ?: false
                val ip = json?.get("IP")?.asString
                if (isTor) ip else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveCountry(client: OkHttpClient, ip: String): Pair<String, String> {
        return try {
            val req = Request.Builder().url("https://ipwho.is/$ip").build()
            client.newCall(req).await().use { resp ->
                if (resp.isSuccessful) {
                    val json = Gson().fromJson(resp.body?.string(), JsonObject::class.java)
                    val country = json?.get("country")?.asString ?: "Tor Relay"
                    val code = json?.get("country_code")?.asString ?: "--"
                    country to code
                } else {
                    "Tor Relay" to "--"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "Tor Relay" to "--"
        }
    }

    /** Async call that is cancelled with the coroutine (blocking execute() is not). */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    data class VerificationResult(
        val isTor: Boolean,
        val ip: String?,
        val country: String?,
        val countryCode: String?
    ) {
        companion object {
            val FAILED = VerificationResult(false, null, null, null)
        }
    }
}
