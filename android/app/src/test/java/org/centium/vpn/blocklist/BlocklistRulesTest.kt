package org.centium.vpn.blocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlocklistRulesTest {

    @Test
    fun cleanAcceptsPlainDomainsLowercased() {
        assertEquals("doubleclick.net", BlocklistRules.cleanDomain("doubleclick.net"))
        assertEquals("ads.example.com", BlocklistRules.cleanDomain("  Ads.Example.COM  "))
    }

    @Test
    fun cleanStripsHostsPrefixAdblockSyntaxSchemeAndPath() {
        assertEquals("tracker.net", BlocklistRules.cleanDomain("0.0.0.0 tracker.net"))
        assertEquals("tracker.net", BlocklistRules.cleanDomain("127.0.0.1\ttracker.net"))
        assertEquals("tracker.net", BlocklistRules.cleanDomain("||tracker.net^"))
        assertEquals("tracker.net", BlocklistRules.cleanDomain("https://tracker.net/path?q=1"))
    }

    @Test
    fun cleanRejectsCommentsBlanksAndJunk() {
        assertNull(BlocklistRules.cleanDomain("# a comment"))
        assertNull(BlocklistRules.cleanDomain("! adblock comment"))
        assertNull(BlocklistRules.cleanDomain("   "))
        assertNull(BlocklistRules.cleanDomain("localhost"))   // no dot
        assertNull(BlocklistRules.cleanDomain("bad_domain!.com"))
    }

    @Test
    fun composeMergesSecurityOnlyWhenAdsExcluded() {
        val security = listOf("tracker.net", "analytics.com")
        val ads = listOf("addomain.com")
        assertEquals(
            listOf("analytics.com", "tracker.net"),
            BlocklistRules.compose(security, ads, includeAds = false, allowlistRaw = "")
        )
    }

    @Test
    fun composeAddsAdsWhenIncluded() {
        val security = listOf("tracker.net")
        val ads = listOf("addomain.com")
        assertEquals(
            listOf("addomain.com", "tracker.net"),
            BlocklistRules.compose(security, ads, includeAds = true, allowlistRaw = "")
        )
    }

    @Test
    fun composeSubtractsAllowlistDedupesAndSorts() {
        val security = listOf("tracker.net", "tracker.net", "0.0.0.0 keep.com", "analytics.com")
        val result = BlocklistRules.compose(
            security, emptyList(), includeAds = false,
            allowlistRaw = "analytics.com\n# my note\nTRACKER.net\n"
        )
        assertEquals(listOf("keep.com"), result)
    }
}
