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
    var socksPort: Int = 9050,
    var controlPort: Int = 9051,
    var dnsPort: Int = 9053,
    var connectionTimeoutSeconds: Int = 60,
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
