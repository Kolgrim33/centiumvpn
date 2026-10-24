package org.centium.vpn.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BridgeLinesTest {

    private val obfs4 = "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=abc iat-mode=0"
    private val webtunnel = "webtunnel [2001:db8::1]:443 1A2B3C4D5E6F708192A3B4C5D6E7F8091A2B3C4D url=https://example.com/path ver=0.0.1"
    private val vanilla = "192.0.2.10:9001 4352E58420E68F5E40BF7C74FADDCCD9D1349413"

    private fun parseFails(raw: String): String = try {
        BridgeLines.parse(raw)
        fail("expected parse to reject: $raw")
        ""
    } catch (e: IllegalArgumentException) {
        e.message ?: ""
    }

    @Test
    fun acceptsObfs4WebTunnelSnowflakeAndPlainBridges() {
        val lines = BridgeLines.parse("$obfs4\n$webtunnel\n$vanilla\n${BuiltinBridges.SNOWFLAKE.first()}")
        assertEquals(4, lines.size)
        assertEquals(setOf("obfs4", "webtunnel", "snowflake"), BridgeLines.transports(lines))
    }

    @Test
    fun stripsBridgePrefixesBlankLinesAndSpaces() {
        assertEquals(listOf(obfs4, vanilla), BridgeLines.parse("  Bridge $obfs4  \n\n\t$vanilla\n"))
    }

    @Test
    fun plainBridgesNeedNoTransport() {
        assertTrue(BridgeLines.transports(BridgeLines.parse(vanilla)).isEmpty())
    }

    @Test
    fun rejectsNoBridges() {
        assertTrue(parseFails("  \n ").contains("at least one", ignoreCase = true))
    }

    @Test
    fun rejectsTransportsCentiumDoesNotBundle() {
        assertTrue(parseFails("meek_lite 192.0.2.18:80 BE776A53492E1E044A26F17306E1BC46A55A1625 url=https://x").contains("meek_lite"))
        assertTrue(parseFails("scramblesuit 192.0.2.1:443 password=x").contains("scramblesuit"))
    }

    @Test
    fun rejectsLinesWithoutAnAddress() {
        assertTrue(parseFails("obfs4").contains("obfs4"))
        assertTrue(parseFails("hello world").isNotEmpty())
    }

    @Test
    fun builtInListsParseCleanly() {
        assertEquals(setOf("snowflake"), BridgeLines.transports(BridgeLines.parse(BuiltinBridges.SNOWFLAKE.joinToString("\n"))))
        assertEquals(setOf("obfs4"), BridgeLines.transports(BridgeLines.parse(BuiltinBridges.OBFS4.joinToString("\n"))))
    }
}
