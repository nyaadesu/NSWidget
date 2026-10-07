package app.nswidget.refresh

import app.nswidget.data.ApiUsage
import app.nswidget.data.Departure
import app.nswidget.data.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class FetchPolicyTest {
    private val now = 1_700_000_000_000L
    private fun min(m: Int) = now + m * 60_000L

    private fun dep(inMinutes: Int, delay: Int = 0, cancelled: Boolean = false) = Departure(
        plannedAt = min(inMinutes),
        actualAt = min(inMinutes + delay),
        direction = "Utrecht Centraal",
        category = "IC",
        plannedTrack = "5",
        actualTrack = null,
        cancelled = cancelled,
    )

    private fun interval(departures: List<Departure>, callsToday: Int = 0, failing: Boolean = false) =
        FetchPolicy.intervalMs(departures, now, callsToday, failing) / 60_000L

    @Test
    fun everyTwoMinutesWhileATrainIsAboutToLeave() {
        assertEquals(2L, interval(listOf(dep(4), dep(20))))
        assertEquals(2L, interval(listOf(dep(15))))
    }

    @Test
    fun lessOftenWhenTheNextTrainIsFurtherAway() {
        assertEquals(5L, interval(listOf(dep(16))))
        assertEquals(5L, interval(listOf(dep(45))))
        assertEquals(10L, interval(listOf(dep(46))))
    }

    @Test
    fun aDelayedTrainCountsByItsActualTime() {
        // Planned in 10 min but running 20 late: it really leaves in 30 min.
        assertEquals(5L, interval(listOf(dep(10, delay = 20))))
    }

    @Test
    fun departedAndCancelledTrainsAreIgnored() {
        assertEquals(10L, interval(listOf(dep(-3), dep(2, cancelled = true), dep(60))))
    }

    @Test
    fun refillsSoonWhenEveryKnownTrainHasLeft() {
        assertEquals(2L, interval(listOf(dep(-5), dep(-1))))
    }

    @Test
    fun anEmptyBoardIsCheckedOnlyOccasionally() {
        assertEquals(10L, interval(emptyList()))
    }

    @Test
    fun backsOffAfterAFailure() {
        assertEquals(5L, interval(listOf(dep(4)), failing = true))
        assertEquals(10L, interval(listOf(dep(60)), failing = true))
    }

    @Test
    fun slowsRightDownPastTheDailySafetyLimit() {
        assertEquals(2L, interval(listOf(dep(4)), callsToday = FetchPolicy.SOFT_DAILY_LIMIT - 1))
        assertEquals(15L, interval(listOf(dep(4)), callsToday = FetchPolicy.SOFT_DAILY_LIMIT))
    }

    @Test
    fun dailyCountStartsAgainOnANewDay() {
        val today = LocalDate.of(2026, 10, 6)
        assertEquals(42, ApiUsage.countFor("2026-10-06", 42, today))
        assertEquals(0, ApiUsage.countFor("2026-10-05", 42, today))
        assertEquals(0, ApiUsage.countFor(null, 0, today))
    }

    @Test
    fun snapshotsSavedBeforeAttemptTrackingStillLoad() {
        // An older version didn't store "try"; the last attempt is then the last success.
        val old = """{"uic":"8400180","name":"Dordrecht","at":1700000000000,"deps":[]}"""
        val snapshot = Snapshot.fromJson(old)
        assertNotNull(snapshot)
        assertEquals(1_700_000_000_000L, snapshot!!.attemptedAt)
    }
}
