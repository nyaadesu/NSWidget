package app.nswidget.discount

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * NS peak-hour rules (Dal Voordeel / Dal Vrij style subscriptions).
 *
 * Peak ("spits"): Monday-Friday 06:30-09:00 and 16:00-18:30, except on public holidays.
 * Everything else - evenings, nights, the 09:00-16:00 gap, weekends and holidays - is off-peak
 * ("dal"), when the discount applies. The discount is decided at check-in time.
 */
object PeakRules {
    val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

    /** Weekday peak windows: start inclusive, end exclusive. */
    val PEAK_WINDOWS: List<Pair<LocalTime, LocalTime>> = listOf(
        LocalTime.of(6, 30) to LocalTime.of(9, 0),
        LocalTime.of(16, 0) to LocalTime.of(18, 30),
    )

    /** How far ahead we warn that the discount is about to start or stop. */
    const val WARN_MINUTES = 60L

    fun isPeakDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY &&
            date.dayOfWeek != DayOfWeek.SUNDAY &&
            !Holidays.isHoliday(date)

    fun isPeak(at: ZonedDateTime): Boolean {
        val local = at.withZoneSameInstant(ZONE)
        if (!isPeakDay(local.toLocalDate())) return false
        val t = local.toLocalTime()
        return PEAK_WINDOWS.any { (start, end) -> !t.isBefore(start) && t.isBefore(end) }
    }

    /** The first instant strictly after [from] at which peak turns into off-peak or the reverse. */
    fun nextChange(from: ZonedDateTime): ZonedDateTime {
        val local = from.withZoneSameInstant(ZONE)
        for (offset in 0L..14L) {
            val day = local.toLocalDate().plusDays(offset)
            if (!isPeakDay(day)) continue
            for ((start, end) in PEAK_WINDOWS) {
                for (time in listOf(start, end)) {
                    val candidate = ZonedDateTime.of(day, time, ZONE)
                    if (candidate.isAfter(local)) return candidate
                }
            }
        }
        return local.plusDays(1) // unreachable in practice: a peak day always exists within two weeks
    }
}

enum class DiscountPhase {
    /** Discount active, and it stays active for more than an hour. */
    OFF_PEAK,

    /** Discount active but peak hours start within the hour. */
    OFF_PEAK_ENDING,

    /** Peak hours, and the discount is more than an hour away. */
    PEAK,

    /** Peak hours, but the discount starts within the hour: "wait x minutes". */
    PEAK_ENDING,
}

data class DiscountStatus(
    val phase: DiscountPhase,
    val changesAt: ZonedDateTime,
    /** Minutes until [changesAt], rounded up. */
    val minutesUntilChange: Long,
) {
    val discountActive: Boolean
        get() = phase == DiscountPhase.OFF_PEAK || phase == DiscountPhase.OFF_PEAK_ENDING
}

fun discountStatus(now: ZonedDateTime): DiscountStatus {
    val peak = PeakRules.isPeak(now)
    val change = PeakRules.nextChange(now)
    val minutes = ceilMinutes(Duration.between(now, change))
    val soon = minutes <= PeakRules.WARN_MINUTES
    val phase = when {
        peak && soon -> DiscountPhase.PEAK_ENDING
        peak -> DiscountPhase.PEAK
        soon -> DiscountPhase.OFF_PEAK_ENDING
        else -> DiscountPhase.OFF_PEAK
    }
    return DiscountStatus(phase, change.withZoneSameInstant(PeakRules.ZONE), minutes)
}

internal fun ceilMinutes(d: Duration): Long {
    val seconds = d.seconds + if (d.nano > 0) 1 else 0
    return (seconds + 59) / 60
}
