package org.centium.vpn.data

data class CentiumConfig(
    var exitLocation: String = "auto",
    var bridgeMode: BridgeMode = BridgeMode.AUTO,
    var bridgeType: BridgeType = BridgeType.NONE,
    var customBridges: String = "",
    var killSwitch: Boolean = true,
    var blockIpv6: Boolean = true,
    var autoConnect: Boolean = false,
    var dnsProtection: Boolean = true,
    var connectionTimeoutSeconds: Int = 120,
    var disallowPackages: Set<String> = emptySet()
)

enum class BridgeMode {
    AUTO,
    BUILTIN,
    CUSTOM,
    NONE
}

enum class BridgeType {
    NONE,
    OBFS4,
    SNOWFLAKE,
    MEEK
}
