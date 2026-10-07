package app.nswidget.refresh

import app.nswidget.discount.PeakRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

class RefreshSchedulerTest {
    private fun at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(PeakRules.ZONE)
    private fun delayAt(s: String) = RefreshScheduler.nextDelayMs(at(s))

    @Test
    fun ticksJustAfterEachClockMinute() {
        assertEquals(61_000L, delayAt("2026-10-06T07:00:00"))   // next tick 07:01:01
        assertEquals(60_000L, delayAt("2026-10-06T07:00:01"))   // a tick that landed on time
        assertEquals(31_000L, delayAt("2026-10-06T11:00:30"))
    }

    @Test
    fun tickRightAfterTheDiscountBoundarySoTheBannerFlipsOnTime() {
        // 30 s before 09:00 -> next tick 1 s after 09:00.
        assertEquals(31_000L, delayAt("2026-10-06T08:59:30"))
    }

    @Test
    fun neverSchedulesAnAlmostImmediateTick() {
        // At 07:00:58 the next minute is only 3 s away; skip ahead to 07:02:01 instead.
        assertEquals(63_000L, delayAt("2026-10-06T07:00:58"))
    }

    @Test
    fun delayIsAlwaysBetweenFiveSecondsAndAMinuteAndAHalf() {
        for (second in 0..59) {
            val delay = delayAt("2026-10-06T07:00:%02d".format(second))
            assertTrue("$second s -> $delay ms", delay in 5_000L..65_000L)
        }
    }
}
