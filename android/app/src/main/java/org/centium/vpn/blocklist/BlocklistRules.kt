package org.centium.vpn.blocklist

/**
 * Pure helpers for turning raw blocklist and allowlist text into the clean, plain-domain
 * file the native mapdns reads (one lowercase domain per line). Kept free of Android types
 * so it can be unit-tested on the JVM.
 */
object BlocklistRules {

    private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private val IP_PREFIXES = setOf("0.0.0.0", "127.0.0.1", "::", "::1")

    /**
     * Normalises one line to a bare domain, or null if it isn't one. Handles hosts-file lines
     * ("0.0.0.0 domain"), AdGuard syntax ("||domain^"), a scheme/path, and surrounding space.
     */
    fun cleanDomain(raw: String): String? {
        var s = raw.substringBefore('#').trim()
        if (s.isEmpty() || s.startsWith('!')) return null

        // Hosts format: drop a leading sink IP and take the first host token.
        val tokens = s.split(Regex("\\s+"))
        s = if (tokens.size > 1 && tokens[0] in IP_PREFIXES) tokens[1] else tokens[0]

        s = s.lowercase()
            .removePrefix("||")
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('^')
            .removePrefix("*.")
            .removeSuffix(".")

        return if (DOMAIN.matches(s)) s else null
    }

    /**
     * Builds the active domain set: the security list, plus the ads list when [includeAds],
     * minus the user's allowlist, deduplicated and sorted for a stable file.
     */
    fun compose(
        security: List<String>,
        ads: List<String>,
        includeAds: Boolean,
        allowlistRaw: String
    ): List<String> {
        val blocked = sequence {
            yieldAll(security)
            if (includeAds) yieldAll(ads)
        }.mapNotNull(::cleanDomain).toMutableSet()

        val allow = allowlistRaw.lineSequence().mapNotNull(::cleanDomain).toSet()
        blocked.removeAll(allow)
        return blocked.sorted()
    }
}
