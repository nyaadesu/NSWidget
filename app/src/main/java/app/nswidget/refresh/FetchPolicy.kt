package app.nswidget.refresh

import app.nswidget.data.Departure

/**
 * How often to ask NS for fresh departures. One call returns delays and platform changes for every
 * train at the station, so the only question is how often to make it. Calls go where they matter:
 * often while a train is about to leave (that's when a platform change counts), rarely otherwise.
 * Combined with "only while the screen is on" this stays far inside the free tier (5,000/day).
 */
object FetchPolicy {
    private const val MINUTE = 60_000L

    /** A train leaves within this window: refresh every [SHORT_MS]. */
    const val IMMINENT_MS = 15 * MINUTE
    const val SHORT_MS = 2 * MINUTE

    /** The next train leaves within this window: refresh every [MEDIUM_MS]. */
    const val SOON_MS = 45 * MINUTE
    const val MEDIUM_MS = 5 * MINUTE

    /** Nothing leaving soon: refresh every [LONG_MS]. */
    const val LONG_MS = 10 * MINUTE

    /** After a failed call, don't retry more often than this. */
    const val FAILURE_MS = 5 * MINUTE

    /** Past this many calls in a day, slow right down - a safety net well below the 5,000 limit. */
    const val SOFT_DAILY_LIMIT = 2_000
    const val OVER_BUDGET_MS = 15 * MINUTE

    /** Journeys to favourite stations are re-planned at most this often (one call per favourite). */
    const val TRIPS_MS = 5 * MINUTE
    const val TRIPS_OVER_BUDGET_MS = 30 * MINUTE

    fun tripsIntervalMs(callsToday: Int): Long =
        if (callsToday >= SOFT_DAILY_LIMIT) TRIPS_OVER_BUDGET_MS else TRIPS_MS

    fun intervalMs(departures: List<Departure>, nowMs: Long, callsToday: Int, failing: Boolean): Long {
        val nextDeparture = departures
            .filter { !it.cancelled && it.actualAt >= nowMs }
            .minOfOrNull { it.actualAt }
        var interval = when {
            // Every train we know about has left: the list needs refilling.
            nextDeparture == null -> if (departures.isEmpty()) LONG_MS else SHORT_MS
            nextDeparture - nowMs <= IMMINENT_MS -> SHORT_MS
            nextDeparture - nowMs <= SOON_MS -> MEDIUM_MS
            else -> LONG_MS
        }
        if (failing) interval = maxOf(interval, FAILURE_MS)
        if (callsToday >= SOFT_DAILY_LIMIT) interval = maxOf(interval, OVER_BUDGET_MS)
        return interval
    }
}
