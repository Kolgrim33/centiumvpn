package org.centium.vpn.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun parsesTagsWithOrWithoutPrefix() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(1, 2, 3), Version.parse("1.2.3"))
    }

    @Test
    fun rejectsMalformedVersions() {
        assertNull(Version.parse("1.2"))
        assertNull(Version.parse("v1.2.3-beta"))
        assertNull(Version.parse("1.100.0"))
        assertNull(Version.parse(""))
    }

    @Test
    fun versionCodeMatchesGradleFormula() {
        assertEquals(10100, Version(1, 1, 0).code)
        assertEquals(20304, Version(2, 3, 4).code)
    }

    @Test
    fun ordersByCode() {
        assertTrue(Version(1, 10, 0) > Version(1, 9, 9))
        assertTrue(Version(2, 0, 0) > Version(1, 99, 99))
    }
}
