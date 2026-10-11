package org.centium.vpn.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request

/** A published release that has everything needed to update. */
data class Release(
    val version: Version,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val checksumUrl: String
)

/** Reads the latest GitHub release for the project. */
class ReleaseFeed(private val client: OkHttpClient) {

    fun fetchLatest(): Release? {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Centium-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null // no releases published yet
            if (!response.isSuccessful) throw UpdateException("GitHub answered ${response.code}")
            return parse(response.body?.string().orEmpty())
        }
    }

    companion object {
        const val REPO = "Kolgrim33/centiumvpn"
        private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

        /** Returns null for anything that isn't a complete, final release. */
        fun parse(json: String): Release? {
            val root = try {
                JsonParser.parseString(json).asJsonObject
            } catch (_: Exception) {
                return null
            }
            if (root.bool("draft") || root.bool("prerelease")) return null
            val version = Version.parse(root.str("tag_name") ?: return null) ?: return null

            val assets = root.getAsJsonArray("assets")?.mapNotNull { it as? JsonObject } ?: return null
            val apkName = "centium-$version.apk"
            val apk = assets.firstOrNull { it.str("name") == apkName } ?: return null
            val sum = assets.firstOrNull { it.str("name") == "$apkName.sha256" } ?: return null

            return Release(
                version = version,
                notes = root.str("body").orEmpty().replace("\r\n", "\n").trim(),
                apkUrl = apk.str("browser_download_url") ?: return null,
                apkSize = apk.get("size")?.asLong ?: 0L,
                checksumUrl = sum.str("browser_download_url") ?: return null
            )
        }

        private fun JsonObject.str(key: String): String? =
            get(key)?.takeUnless { it.isJsonNull }?.asString

        private fun JsonObject.bool(key: String): Boolean =
            get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
    }
}

class UpdateException(message: String) : Exception(message)
