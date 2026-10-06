package app.nswidget.discount

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Dutch public holidays on which NS has no peak hours (treated like a Sunday).
 *
 * This is the one place to edit if NS's holiday list differs from what's below.
 * Sundays (Easter Sunday, Whit Sunday) are off-peak anyway, so they aren't listed.
 */
object Holidays {
    fun isHoliday(date: LocalDate): Boolean = of(date.year).contains(date)

    fun of(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        // King's Day moves to the 26th when the 27th is a Sunday.
        val kingsDay = LocalDate.of(year, 4, 27).let {
            if (it.dayOfWeek == DayOfWeek.SUNDAY) it.minusDays(1) else it
        }
        return buildSet {
            add(LocalDate.of(year, 1, 1))     // New Year's Day
            add(easter.minusDays(2))          // Good Friday
            add(easter.plusDays(1))           // Easter Monday
            add(kingsDay)                     // King's Day
            if (year % 5 == 0) add(LocalDate.of(year, 5, 5)) // Liberation Day: only every 5th year
            add(easter.plusDays(39))          // Ascension Day
            add(easter.plusDays(50))          // Whit Monday
            add(LocalDate.of(year, 12, 25))   // Christmas Day
            add(LocalDate.of(year, 12, 26))   // Boxing Day
        }
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    internal fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
