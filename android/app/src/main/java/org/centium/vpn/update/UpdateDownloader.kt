package org.centium.vpn.update

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a release's APK and checksum into [dir], reusing a finished download. */
class UpdateDownloader(private val client: OkHttpClient, private val dir: File) {

    fun apkFile(release: Release) = File(dir, "centium-${release.version}.apk")

    /** Returns the published SHA-256 for [release]. */
    fun fetchChecksum(release: Release): String {
        val text = get(release.checksumUrl) { it.body?.string().orEmpty() }
        return UpdateVerifier.parseChecksum(text) ?: throw UpdateException("Release checksum is unreadable")
    }

    fun download(release: Release, expectedSha256: String): File {
        dir.mkdirs()
        // Old versions are never needed again
        dir.listFiles()?.filter { it.name != apkFile(release).name }?.forEach { it.delete() }

        val target = apkFile(release)
        if (target.exists() && UpdateVerifier.sha256(target) == expectedSha256) return target

        val partial = File(dir, target.name + ".part")
        try {
            fetchTo(release.apkUrl, partial)
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
        if (!partial.renameTo(target)) throw UpdateException("Couldn't save the download")
        return target
    }

    private fun fetchTo(url: String, partial: File) {
        get(url) { response ->
            val body = response.body ?: throw UpdateException("Empty download")
            if (body.contentLength() > MAX_BYTES) throw UpdateException("Download is unexpectedly large")
            partial.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw UpdateException("Download is unexpectedly large")
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun <T> get(url: String, read: (okhttp3.Response) -> T): T {
        val request = Request.Builder().url(url).header("User-Agent", "Centium-Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException("Download failed (${response.code})")
            return read(response)
        }
    }

    companion object {
        private const val MAX_BYTES = 200L * 1024 * 1024
    }
}
