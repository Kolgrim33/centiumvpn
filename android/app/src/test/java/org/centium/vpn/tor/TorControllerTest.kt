package org.centium.vpn.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorControllerTest {

    @Test
    fun parsesTheSocksListenerPort() {
        assertEquals(41235, TorController.parseSocksPort("\"127.0.0.1:41235\""))
        assertEquals(9050, TorController.parseSocksPort("127.0.0.1:9050"))
        assertEquals(41235, TorController.parseSocksPort("\"127.0.0.1:41235\" \"[::1]:41236\""))
    }

    @Test
    fun noListenerMeansNoPort() {
        // With DisableNetwork 1, Tor has no SOCKS listener yet
        assertNull(TorController.parseSocksPort(""))
        assertNull(TorController.parseSocksPort("\"\""))
        assertNull(TorController.parseSocksPort("unix:/data/socks"))
    }
}
