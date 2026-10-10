package org.centium.vpn.data

data class CircuitNode(
    val role: Role,
    val ip: String? = null,
    val fingerprint: String,
    val nickname: String,
    val country: String = "Unknown",
    val countryCode: String = "--"
) {
    enum class Role {
        Guard,
        Middle,
        Exit
    }
}
