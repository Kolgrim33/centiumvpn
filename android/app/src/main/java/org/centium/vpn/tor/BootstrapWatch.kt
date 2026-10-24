package org.centium.vpn.tor

/**
 * Judges a route's bootstrap progress. Where Tor is blocked, bootstrap never gets past
 * connecting to a first relay, so a route that hasn't reached one in [reachMillis] is
 * called blocked and the next route is tried. A route that has reached one gets the full
 * [totalMillis], since a slow network isn't a blocked one. The last route is never called
 * blocked early: it simply gets the full time.
 */
class BootstrapWatch(
    private val reachMillis: Long,
    private val totalMillis: Long,
    private val hasFallback: Boolean
) {
    enum class Verdict { WAIT, DONE, BLOCKED, TIMED_OUT }

    fun verdict(percent: Int, elapsedMillis: Long): Verdict = when {
        percent >= 100 -> Verdict.DONE
        hasFallback && percent < REACHED_RELAY && elapsedMillis >= reachMillis -> Verdict.BLOCKED
        elapsedMillis >= totalMillis -> Verdict.TIMED_OUT
        else -> Verdict.WAIT
    }

    companion object {
        /** Tor's bootstrap reaches 15% ("handshake_done") once it has talked to a relay or bridge. */
        const val REACHED_RELAY = 15

        /** Snowflake's rendezvous through the broker is slow, so it gets longer than the others. */
        fun reachMillis(route: Route): Long = when (route) {
            Route.DIRECT -> 30_000
            Route.OBFS4 -> 40_000
            Route.SNOWFLAKE -> 75_000
            Route.CUSTOM -> 75_000
        }
    }
}
