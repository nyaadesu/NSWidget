package app.nswidget.refresh

import app.nswidget.discount.PeakRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

class RefreshSchedulerTest {
    private fun at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(PeakRules.ZONE)
    private fun minutes(ms: Long) = ms / 60_000.0

    @Test
    fun relaxedWhenTheDiscountBoundaryIsFarAway() {
        assertEquals(15.0, minutes(RefreshScheduler.nextDelayMs(at("2026-10-06T07:00:00"))), 0.001)
        assertEquals(15.0, minutes(RefreshScheduler.nextDelayMs(at("2026-10-06T11:00:00"))), 0.001)
    }

    @Test
    fun everyFiveMinutesInTheHourBeforeTheBoundary() {
        assertEquals(5.0, minutes(RefreshScheduler.nextDelayMs(at("2026-10-06T08:00:00"))), 0.001)
        assertEquals(5.0, minutes(RefreshScheduler.nextDelayMs(at("2026-10-06T15:30:00"))), 0.001)
    }

    @Test
    fun everyMinuteInTheLastTenMinutes() {
        assertEquals(1.0, minutes(RefreshScheduler.nextDelayMs(at("2026-10-06T08:50:00"))), 0.001)
        assertEquals(1.0, minutes(RefreshScheduler.nextDelayMs(at("2026-10-06T08:58:00"))), 0.001)
    }

    @Test
    fun landsJustAfterTheBoundarySoTheBannerFlipsOnTime() {
        // 30 s before 09:00 -> next tick 1 s after 09:00.
        assertEquals(31_000L, RefreshScheduler.nextDelayMs(at("2026-10-06T08:59:30")))
    }

    @Test
    fun neverBusyLoops() {
        // Right at the boundary the next change is hours away, so there is no tiny delay.
        assertTrue(RefreshScheduler.nextDelayMs(at("2026-10-06T09:00:00")) >= 30_000L)
        assertTrue(RefreshScheduler.nextDelayMs(at("2026-10-06T08:59:59")) >= 30_000L)
    }
}
