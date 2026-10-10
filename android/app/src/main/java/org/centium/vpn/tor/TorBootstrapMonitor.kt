package org.centium.vpn.tor

import java.util.regex.Pattern

class TorBootstrapMonitor(
    private val onProgress: (percent: Int, summary: String) -> Unit
) {
    private val bootstrapPattern = Pattern.compile("Bootstrapped\\s+(\\d+)%\\s*(?:\\(([^)]+)\\))?:\\s*(.*)")

    fun parseLine(line: String) {
        val matcher = bootstrapPattern.matcher(line)
        if (matcher.find()) {
            val percent = matcher.group(1)?.toIntOrNull() ?: 0
            val tag = matcher.group(2) ?: ""
            val summary = matcher.group(3) ?: tag
            onProgress(percent, summary.ifEmpty { "Bootstrapping circuit ($percent%)" })
        }
    }
}
