package org.centium.vpn.tor

/** Parses and checks torrc bridge lines, as pasted by the user or built in. */
object BridgeLines {

    /** Transports Centium bundles through IPtProxy. */
    val SUPPORTED_TRANSPORTS = setOf("obfs4", "snowflake", "webtunnel")

    private val ADDRESS = Regex("""^(\d{1,3}(\.\d{1,3}){3}|\[[0-9a-fA-F:.]+]):\d{1,5}$""")

    /**
     * Returns one cleaned line per bridge, or throws [IllegalArgumentException] with a message
     * the user can act on. Settings shows it in a toast, which Samsung cuts at two lines, so
     * keep messages short.
     */
    fun parse(raw: String): List<String> {
        val lines = raw.lines()
            .map { it.trim().removePrefix("Bridge ").trim() }
            .filter { it.isNotEmpty() }
        require(lines.isNotEmpty()) { "Add at least one bridge, or pick another bridge option." }

        lines.forEach { line ->
            val parts = line.split(Regex("\\s+"))
            val transport = transportOf(line)
            if (transport != null) {
                require(transport in SUPPORTED_TRANSPORTS) {
                    "Centium can't use $transport bridges. Try obfs4, Snowflake or WebTunnel."
                }
                require(parts.size >= 2 && ADDRESS.matches(parts[1])) {
                    "A $transport bridge is missing its IP:port. Check for typos."
                }
            } else {
                require(ADDRESS.matches(parts[0])) { "One line isn't a bridge. Check it starts with IP:port or a transport." }
            }
        }
        return lines
    }

    /** Transports the given (already parsed) lines need; plain IP:port bridges need none. */
    fun transports(lines: List<String>): Set<String> = lines.mapNotNull { transportOf(it) }.toSet()

    // A plain bridge starts with IP:port; anything else names its transport first
    private fun transportOf(line: String): String? {
        val first = line.substringBefore(' ')
        return if (first.contains(':')) null else first.lowercase()
    }
}
