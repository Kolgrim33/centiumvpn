package org.centium.vpn.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Accepts a downloaded APK only if it is byte-identical to what was published
 * and signed by the same key as the running app. Android re-checks the signature
 * at install time; checking here too means a bad file is never handed over.
 */
class UpdateVerifier(private val context: Context) {

    fun verify(apk: File, release: Release, expectedSha256: String) {
        val actual = sha256(apk)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw UpdateException("Download is corrupted or was altered (checksum mismatch)")
        }

        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, signatureFlags())
            ?: throw UpdateException("Downloaded file is not a valid app")
        if (archive.packageName != context.packageName) {
            throw UpdateException("Downloaded app is ${archive.packageName}, not Centium")
        }
        if (archive.longCode() != release.version.code.toLong()) {
            throw UpdateException("Downloaded app version doesn't match the release")
        }
        val installed = pm.getPackageInfo(context.packageName, signatureFlags())
        if (archive.longCode() <= installed.longCode()) {
            throw UpdateException("Downloaded app is not newer than this one")
        }
        if (signerDigests(archive).isEmpty() || signerDigests(archive) != signerDigests(installed)) {
            throw UpdateException("Downloaded app is not signed by Centium's release key")
        }
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        } ?: return emptySet()
        return signatures.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    companion object {
        private val HEX64 = Regex("[0-9a-fA-F]{64}")

        /** Accepts `sha256sum` output ("<hex>  file") or a bare digest. */
        fun parseChecksum(text: String): String? {
            val token = text.trim().split(Regex("\\s+")).firstOrNull() ?: return null
            return token.takeIf { HEX64.matches(it) }?.lowercase()
        }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
