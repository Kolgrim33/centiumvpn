package org.centium.vpn.data

data class DiagnosticResult(
    val torProcessAlive: Boolean = false,
    val socksPortListening: Boolean = false,
    val bootstrapPercent: Int = 0,
    val vpnInterfaceActive: Boolean = false,
    val bridgeProcessActive: Boolean = false,
    val routesConfigured: Boolean = false,
    val failClosedArmed: Boolean = false,
    val dnsProtected: Boolean = false,
    val socksTorReachable: Boolean = false,
    val directExitVerified: Boolean = false,
    val verifiedExitIp: String? = null,
    val isTorExit: Boolean = false,
    val latencyMs: Long = 0,
    val layerDetails: List<LayerCheck> = emptyList()
) {
    data class LayerCheck(
        val layerNumber: Int,
        val layerName: String,
        val passed: Boolean,
        val description: String,
        val remediation: String? = null
    )

    val passedCount: Int
        get() = layerDetails.count { it.passed }

    val isAllPassed: Boolean
        get() = layerDetails.isNotEmpty() && layerDetails.all { it.passed }
}
