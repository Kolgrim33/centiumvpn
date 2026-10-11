package org.centium.vpn.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseFeedTest {

    private fun json(tag: String, prerelease: Boolean = false, draft: Boolean = false, assets: String? = null) = """
        {
          "tag_name": "$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "body": "## What's new\r\n- Faster connect",
          "assets": ${assets ?: """[
            {"name": "centium-1.1.0.apk", "size": 4000, "browser_download_url": "https://example.com/centium-1.1.0.apk"},
            {"name": "centium-1.1.0.apk.sha256", "size": 90, "browser_download_url": "https://example.com/centium-1.1.0.apk.sha256"}
          ]"""}
        }
    """.trimIndent()

    @Test
    fun parsesLatestRelease() {
        val release = ReleaseFeed.parse(json("v1.1.0"))!!
        assertEquals(Version(1, 1, 0), release.version)
        assertEquals("https://example.com/centium-1.1.0.apk", release.apkUrl)
        assertEquals("https://example.com/centium-1.1.0.apk.sha256", release.checksumUrl)
        assertEquals(4000L, release.apkSize)
        assertEquals("## What's new\n- Faster connect", release.notes)
    }

    @Test
    fun skipsDraftsAndPrereleases() {
        assertNull(ReleaseFeed.parse(json("v1.1.0", prerelease = true)))
        assertNull(ReleaseFeed.parse(json("v1.1.0", draft = true)))
    }

    @Test
    fun requiresBothApkAndChecksumAssets() {
        val onlyApk = """[{"name": "centium-1.1.0.apk", "size": 1, "browser_download_url": "https://example.com/a.apk"}]"""
        assertNull(ReleaseFeed.parse(json("v1.1.0", assets = onlyApk)))
    }

    @Test
    fun rejectsUnparseableTagOrJson() {
        assertNull(ReleaseFeed.parse(json("nightly")))
        assertNull(ReleaseFeed.parse("not json"))
    }

    @Test
    fun parsesSha256sumOutput() {
        val hex = "a".repeat(64)
        assertEquals(hex, UpdateVerifier.parseChecksum("$hex  centium-1.1.0.apk\n"))
        assertEquals(hex, UpdateVerifier.parseChecksum(hex.uppercase()))
        assertNull(UpdateVerifier.parseChecksum("abc  file"))
    }
}
