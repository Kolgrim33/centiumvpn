package org.centium.vpn.tor

import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.BridgeType
import org.centium.vpn.data.CentiumConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectStrategyTest {

    private fun config(mode: BridgeMode, type: BridgeType = BridgeType.NONE) =
        CentiumConfig(bridgeMode = mode, bridgeType = type)

    @Test
    fun automaticTriesDirectThenSnowflakeThenObfs4() {
        assertEquals(
            listOf(Route.DIRECT, Route.SNOWFLAKE, Route.OBFS4),
            ConnectStrategy.routes(config(BridgeMode.AUTO), lastWorking = null)
        )
    }

    @Test
    fun automaticTriesWhatWorkedLastTimeFirst() {
        assertEquals(
            listOf(Route.SNOWFLAKE, Route.DIRECT, Route.OBFS4),
            ConnectStrategy.routes(config(BridgeMode.AUTO), lastWorking = Route.SNOWFLAKE)
        )
        assertEquals(
            listOf(Route.OBFS4, Route.DIRECT, Route.SNOWFLAKE),
            ConnectStrategy.routes(config(BridgeMode.AUTO), lastWorking = Route.OBFS4)
        )
    }

    @Test
    fun automaticIgnoresARememberedCustomRoute() {
        assertEquals(
            listOf(Route.DIRECT, Route.SNOWFLAKE, Route.OBFS4),
            ConnectStrategy.routes(config(BridgeMode.AUTO), lastWorking = Route.CUSTOM)
        )
    }

    @Test
    fun aChosenRouteIsTheOnlyOneTried() {
        assertEquals(listOf(Route.DIRECT), ConnectStrategy.routes(config(BridgeMode.NONE), Route.SNOWFLAKE))
        assertEquals(
            listOf(Route.SNOWFLAKE),
            ConnectStrategy.routes(config(BridgeMode.BUILTIN, BridgeType.SNOWFLAKE), null)
        )
        assertEquals(
            listOf(Route.OBFS4),
            ConnectStrategy.routes(config(BridgeMode.BUILTIN, BridgeType.OBFS4), null)
        )
        assertEquals(listOf(Route.CUSTOM), ConnectStrategy.routes(config(BridgeMode.CUSTOM), Route.DIRECT))
    }

    @Test
    fun aBuiltInChoiceWithNoUsableTransportFallsBackToAutomatic() {
        val auto = listOf(Route.DIRECT, Route.SNOWFLAKE, Route.OBFS4)
        assertEquals(auto, ConnectStrategy.routes(config(BridgeMode.BUILTIN, BridgeType.MEEK), null))
        assertEquals(auto, ConnectStrategy.routes(config(BridgeMode.BUILTIN, BridgeType.NONE), null))
    }
}
