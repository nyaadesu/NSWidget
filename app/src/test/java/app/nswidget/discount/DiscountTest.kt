package app.nswidget.discount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DiscountTest {
    /** Amsterdam wall-clock time. 2026-10-06 is a Tuesday. */
    private fun at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(PeakRules.ZONE)

    private fun label(s: String): DiscountLabel {
        val now = at(s)
        return DiscountText.label(discountStatus(now), now)
    }

    @Test
    fun peakBoundariesAreStartInclusiveEndExclusive() {
        assertFalse(PeakRules.isPeak(at("2026-10-06T06:29:59")))
        assertTrue(PeakRules.isPeak(at("2026-10-06T06:30:00")))
        assertTrue(PeakRules.isPeak(at("2026-10-06T08:59:59")))
        assertFalse(PeakRules.isPeak(at("2026-10-06T09:00:00")))
        assertTrue(PeakRules.isPeak(at("2026-10-06T16:00:00")))
        assertTrue(PeakRules.isPeak(at("2026-10-06T18:29:59")))
        assertFalse(PeakRules.isPeak(at("2026-10-06T18:30:00")))
    }

    @Test
    fun morningPeakMoreThanAnHourFromDiscount() {
        val s = discountStatus(at("2026-10-06T07:00:00"))
        assertEquals(DiscountPhase.PEAK, s.phase)
        assertEquals(120L, s.minutesUntilChange)
        assertEquals(at("2026-10-06T09:00:00"), s.changesAt)
        assertFalse(s.discountActive)
    }

    @Test
    fun waitMinutesWhenDiscountStartsWithinTheHour() {
        val s = discountStatus(at("2026-10-06T08:10:00"))
        assertEquals(DiscountPhase.PEAK_ENDING, s.phase)
        assertEquals(50L, s.minutesUntilChange)

        val l = label("2026-10-06T08:10:00")
        assertEquals("Wait 50 min for discount", l.title)
        assertEquals("Wait 50 min", l.compactTitle)
        assertEquals("Off-peak from 09:00", l.subtitle)
    }

    @Test
    fun exactlyOneHourAwayCountsAsWithinTheHour() {
        assertEquals(DiscountPhase.PEAK_ENDING, discountStatus(at("2026-10-06T08:00:00")).phase)
        assertEquals("Wait 60 min for discount", label("2026-10-06T08:00:00").title)
        assertEquals(DiscountPhase.PEAK, discountStatus(at("2026-10-06T07:59:00")).phase)
    }

    @Test
    fun minutesRoundUp() {
        assertEquals(1L, discountStatus(at("2026-10-06T08:59:30")).minutesUntilChange)
        assertEquals(1L, discountStatus(at("2026-10-06T08:59:00")).minutesUntilChange)
        assertEquals(2L, discountStatus(at("2026-10-06T08:58:01")).minutesUntilChange)
    }

    @Test
    fun discountStartsExactlyAtNine() {
        val s = discountStatus(at("2026-10-06T09:00:00"))
        assertEquals(DiscountPhase.OFF_PEAK, s.phase)
        assertTrue(s.discountActive)
        assertEquals(at("2026-10-06T16:00:00"), s.changesAt)
        assertEquals("Off-peak · discount active", label("2026-10-06T09:00:00").title)
        assertEquals("Peak starts 16:00", label("2026-10-06T09:00:00").subtitle)
    }

    @Test
    fun warnsWhenDiscountIsAboutToEnd() {
        val s = discountStatus(at("2026-10-06T15:30:00"))
        assertEquals(DiscountPhase.OFF_PEAK_ENDING, s.phase)
        assertTrue(s.discountActive)
        assertEquals("Discount ends in 30 min", label("2026-10-06T15:30:00").title)
        assertEquals("Check in before 16:00", label("2026-10-06T15:30:00").subtitle)
    }

    @Test
    fun eveningPeak() {
        assertEquals(DiscountPhase.PEAK, discountStatus(at("2026-10-06T17:00:00")).phase)
        val s = discountStatus(at("2026-10-06T17:45:00"))
        assertEquals(DiscountPhase.PEAK_ENDING, s.phase)
        assertEquals(45L, s.minutesUntilChange)
        assertEquals(at("2026-10-06T18:30:00"), s.changesAt)
    }

    @Test
    fun weekendIsOffPeakUntilMondayMorning() {
        val s = discountStatus(at("2026-10-10T07:30:00")) // Saturday
        assertEquals(DiscountPhase.OFF_PEAK, s.phase)
        assertEquals(at("2026-10-12T06:30:00"), s.changesAt)
        assertEquals("Peak starts Mon 06:30", label("2026-10-10T07:30:00").subtitle)
    }

    @Test
    fun fridayEveningRollsOverToMonday() {
        val s = discountStatus(at("2026-10-09T18:30:00")) // Friday
        assertEquals(DiscountPhase.OFF_PEAK, s.phase)
        assertEquals(at("2026-10-12T06:30:00"), s.changesAt)
    }

    @Test
    fun holidayMondayHasNoPeak() {
        // 2026-04-27 is King's Day and a Monday.
        val s = discountStatus(at("2026-04-27T07:00:00"))
        assertEquals(DiscountPhase.OFF_PEAK, s.phase)
        assertEquals(at("2026-04-28T06:30:00"), s.changesAt)
        assertEquals("Peak starts tomorrow 06:30", label("2026-04-27T07:00:00").subtitle)
    }

    @Test
    fun beforeTheMorningPeak() {
        val s = discountStatus(at("2026-10-06T06:00:00"))
        assertEquals(DiscountPhase.OFF_PEAK_ENDING, s.phase)
        assertEquals(30L, s.minutesUntilChange)
    }

    @Test
    fun countdownIsCorrectAcrossTheSpringDstChange() {
        // Clocks went forward on Sunday 2026-03-29; the Monday after is a normal peak day.
        val s = discountStatus(at("2026-03-30T06:00:00"))
        assertEquals(30L, s.minutesUntilChange)
    }

    @Test
    fun inputInAnyTimeZoneIsInterpretedAsAmsterdamTime() {
        val utc = ZonedDateTime.of(2026, 10, 6, 5, 0, 0, 0, ZoneOffset.UTC) // 07:00 in Amsterdam (CEST)
        assertEquals(DiscountPhase.PEAK, discountStatus(utc).phase)
        assertEquals(120L, discountStatus(utc).minutesUntilChange)
    }

    @Test
    fun easterDates() {
        assertEquals(LocalDate.of(2024, 3, 31), Holidays.easterSunday(2024))
        assertEquals(LocalDate.of(2025, 4, 20), Holidays.easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easterSunday(2026))
    }

    @Test
    fun holidays2026() {
        val h = Holidays.of(2026)
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 3),   // Good Friday
            LocalDate.of(2026, 4, 6),   // Easter Monday
            LocalDate.of(2026, 4, 27),  // King's Day
            LocalDate.of(2026, 5, 14),  // Ascension
            LocalDate.of(2026, 5, 25),  // Whit Monday
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2026, 12, 26),
        ).forEach { assertTrue("$it should be a holiday", it in h) }
        assertFalse("Liberation Day is only a holiday every 5th year", LocalDate.of(2026, 5, 5) in h)
    }

    @Test
    fun liberationDayEveryFifthYear() {
        assertTrue(LocalDate.of(2025, 5, 5) in Holidays.of(2025))
        assertTrue(LocalDate.of(2030, 5, 5) in Holidays.of(2030))
    }

    @Test
    fun kingsDayMovesWhenTheTwentySeventhIsASunday() {
        val h = Holidays.of(2025) // 27 April 2025 is a Sunday
        assertTrue(LocalDate.of(2025, 4, 26) in h)
        assertFalse(LocalDate.of(2025, 4, 27) in h)
    }
}
