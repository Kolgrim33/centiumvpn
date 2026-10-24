package org.centium.vpn.tor

import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.BridgeType
import org.centium.vpn.data.CentiumConfig

/** One way of reaching the Tor network. */
enum class Route(val label: String) {
    DIRECT("Tor directly"),
    SNOWFLAKE("Snowflake"),
    OBFS4("obfs4 bridges"),
    CUSTOM("your bridges")
}

/** Decides which routes a connect attempt tries, in order. */
object ConnectStrategy {

    private val AUTOMATIC = listOf(Route.DIRECT, Route.SNOWFLAKE, Route.OBFS4)

    /**
     * Automatic tries direct, then Snowflake, then obfs4, starting with whichever worked last
     * time. Any explicit choice in Settings is the only route tried.
     */
    fun routes(config: CentiumConfig, lastWorking: Route?): List<Route> = when (config.bridgeMode) {
        BridgeMode.NONE -> listOf(Route.DIRECT)
        BridgeMode.CUSTOM -> listOf(Route.CUSTOM)
        BridgeMode.BUILTIN -> when (config.bridgeType) {
            BridgeType.SNOWFLAKE -> listOf(Route.SNOWFLAKE)
            BridgeType.OBFS4 -> listOf(Route.OBFS4)
            else -> automatic(lastWorking)
        }
        BridgeMode.AUTO -> automatic(lastWorking)
    }

    private fun automatic(lastWorking: Route?): List<Route> =
        if (lastWorking != null && lastWorking in AUTOMATIC) listOf(lastWorking) + (AUTOMATIC - lastWorking)
        else AUTOMATIC
}
