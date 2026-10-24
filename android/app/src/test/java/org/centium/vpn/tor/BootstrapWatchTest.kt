package org.centium.vpn.tor

import org.centium.vpn.tor.BootstrapWatch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapWatchTest {

    // A route that must reach a relay within 30 s, with 120 s in total
    private val withFallback = BootstrapWatch(reachMillis = 30_000, totalMillis = 120_000, hasFallback = true)
    private val lastRoute = BootstrapWatch(reachMillis = 30_000, totalMillis = 120_000, hasFallback = false)

    @Test
    fun doneAtOneHundredPercent() {
        assertEquals(Verdict.DONE, withFallback.verdict(percent = 100, elapsedMillis = 5_000))
    }

    @Test
    fun keepsWaitingWhileThereIsTime() {
        assertEquals(Verdict.WAIT, withFallback.verdict(percent = 5, elapsedMillis = 29_999))
        assertEquals(Verdict.WAIT, withFallback.verdict(percent = 0, elapsedMillis = 0))
    }

    @Test
    fun blockedWhenNoRelayIsReachedInTime() {
        assertEquals(Verdict.BLOCKED, withFallback.verdict(percent = 10, elapsedMillis = 30_000))
        assertEquals(Verdict.BLOCKED, withFallback.verdict(percent = 0, elapsedMillis = 31_000))
    }

    @Test
    fun aRouteThatReachedARelayIsGivenTheFullTime() {
        assertEquals(Verdict.WAIT, withFallback.verdict(percent = BootstrapWatch.REACHED_RELAY, elapsedMillis = 90_000))
        assertEquals(Verdict.WAIT, withFallback.verdict(percent = 50, elapsedMillis = 119_999))
        assertEquals(Verdict.TIMED_OUT, withFallback.verdict(percent = 50, elapsedMillis = 120_000))
    }

    @Test
    fun theLastRouteIsNeverCalledBlockedEarly() {
        assertEquals(Verdict.WAIT, lastRoute.verdict(percent = 5, elapsedMillis = 60_000))
        assertEquals(Verdict.TIMED_OUT, lastRoute.verdict(percent = 5, elapsedMillis = 120_000))
    }

    @Test
    fun reachTimesPerRoute() {
        assertEquals(30_000L, BootstrapWatch.reachMillis(Route.DIRECT))
        assertEquals(40_000L, BootstrapWatch.reachMillis(Route.OBFS4))
        assertEquals(75_000L, BootstrapWatch.reachMillis(Route.SNOWFLAKE))
    }
}
