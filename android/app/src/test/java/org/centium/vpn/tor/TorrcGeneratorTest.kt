package org.centium.vpn.tor

import org.centium.vpn.data.BridgeMode
import org.centium.vpn.data.CentiumConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrcGeneratorTest {

    private val generator = TorrcGenerator()

    @Test
    fun torStartsWithTheNetworkOffSoTheFirstRouteCanBeAppliedLive() {
        val torrc = generator.generateTorrc(CentiumConfig())
        assertTrue(torrc.lines().contains("DisableNetwork 1"))
        assertTrue(torrc.lines().contains("SocksPort 127.0.0.1:auto"))
        assertFalse(torrc.contains("Bridge"))
    }

    @Test
    fun exitCountryGoesIntoTorrc() {
        val torrc = generator.generateTorrc(CentiumConfig(exitLocation = "DE"))
        assertTrue(torrc.lines().contains("ExitNodes {de}"))
        assertTrue(torrc.lines().contains("StrictNodes 1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAMalformedExitCountry() {
        generator.generateTorrc(CentiumConfig(exitLocation = "germany"))
    }

    @Test
    fun directUsesNoBridgesAndTurnsTheNetworkOn() {
        assertEquals(
            listOf("UseBridges 0", "DisableNetwork 0"),
            generator.routeConf(Route.DIRECT, CentiumConfig(), emptyMap())
        )
    }

    @Test
    fun noEntryIsABareKey() {
        // jtorctl's setConf crashes on an entry without a value, so every entry needs one
        val config = CentiumConfig(customBridges = "192.0.2.10:9001 4352E58420E68F5E40BF7C74FADDCCD9D1349413")
        val ports = mapOf("snowflake" to 1, "obfs4" to 2)
        Route.entries.forEach { route ->
            generator.routeConf(route, config, ports).forEach { assertTrue("$route: $it", it.contains(' ')) }
        }
    }

    @Test
    fun routeKeysCoverEverythingARouteSets() {
        val conf = generator.routeConf(Route.OBFS4, CentiumConfig(), mapOf("obfs4" to 2))
        val keys = conf.map { it.substringBefore(' ') }.toSet() - "DisableNetwork"
        assertEquals(keys, generator.routeKeys.toSet())
    }

    @Test
    fun snowflakeUsesTheBuiltInBridgesAndTheLocalTransportPort() {
        val conf = generator.routeConf(Route.SNOWFLAKE, CentiumConfig(), mapOf("snowflake" to 41001))
        assertEquals("UseBridges 1", conf.first())
        assertEquals("DisableNetwork 0", conf.last())
        assertEquals(BuiltinBridges.SNOWFLAKE.map { "Bridge $it" }, conf.filter { it.startsWith("Bridge ") })
        assertEquals(listOf("ClientTransportPlugin snowflake socks5 127.0.0.1:41001"), conf.filter { it.startsWith("ClientTransportPlugin") })
    }

    @Test
    fun obfs4UsesTheBuiltInObfs4Bridges() {
        val conf = generator.routeConf(Route.OBFS4, CentiumConfig(), mapOf("obfs4" to 41002))
        assertEquals(BuiltinBridges.OBFS4.size, conf.count { it.startsWith("Bridge obfs4 ") })
        assertTrue(conf.contains("ClientTransportPlugin obfs4 socks5 127.0.0.1:41002"))
    }

    @Test
    fun customBridgesGetAPluginPerTransportTheyUse() {
        val config = CentiumConfig(
            bridgeMode = BridgeMode.CUSTOM,
            customBridges = "webtunnel 192.0.2.5:443 1A2B3C4D5E6F708192A3B4C5D6E7F8091A2B3C4D url=https://example.com/x\n" +
                "192.0.2.10:9001 4352E58420E68F5E40BF7C74FADDCCD9D1349413"
        )
        val conf = generator.routeConf(Route.CUSTOM, config, mapOf("webtunnel" to 41003))
        assertEquals(2, conf.count { it.startsWith("Bridge ") })
        assertEquals(listOf("ClientTransportPlugin webtunnel socks5 127.0.0.1:41003"), conf.filter { it.startsWith("ClientTransportPlugin") })
    }

    @Test
    fun plainCustomBridgesNeedNoPlugin() {
        val config = CentiumConfig(customBridges = "192.0.2.10:9001 4352E58420E68F5E40BF7C74FADDCCD9D1349413")
        val conf = generator.routeConf(Route.CUSTOM, config, emptyMap())
        assertEquals(listOf("UseBridges 1", "Bridge 192.0.2.10:9001 4352E58420E68F5E40BF7C74FADDCCD9D1349413", "DisableNetwork 0"), conf)
    }

    @Test(expected = IllegalStateException::class)
    fun failsIfATransportIsNotRunning() {
        generator.routeConf(Route.SNOWFLAKE, CentiumConfig(), emptyMap())
    }

    @Test
    fun transportsNeededPerRoute() {
        assertEquals(emptySet<String>(), generator.transportsFor(Route.DIRECT, CentiumConfig()))
        assertEquals(setOf("snowflake"), generator.transportsFor(Route.SNOWFLAKE, CentiumConfig()))
        assertEquals(setOf("obfs4"), generator.transportsFor(Route.OBFS4, CentiumConfig()))
        assertEquals(
            setOf("obfs4", "webtunnel"),
            generator.transportsFor(
                Route.CUSTOM,
                CentiumConfig(
                    customBridges = "obfs4 192.0.2.1:443 1A2B3C4D5E6F708192A3B4C5D6E7F8091A2B3C4D cert=x iat-mode=0\n" +
                        "webtunnel 192.0.2.5:443 1A2B3C4D5E6F708192A3B4C5D6E7F8091A2B3C4D url=https://example.com/x"
                )
            )
        )
    }
}
