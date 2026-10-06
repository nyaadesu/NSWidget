package app.nswidget.discount

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Texts for the discount banner; [compact] variants are for narrow (2-cell) widgets. */
data class DiscountLabel(
    val title: String,
    val compactTitle: String,
    val subtitle: String,
    val compactSubtitle: String,
)

object DiscountText {
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val DAY_HHMM = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)

    /** "09:00", "tomorrow 06:30" or "Mon 06:30", relative to [now]. */
    fun whenLabel(at: ZonedDateTime, now: ZonedDateTime): String {
        val atLocal = at.withZoneSameInstant(PeakRules.ZONE)
        val today = now.withZoneSameInstant(PeakRules.ZONE).toLocalDate()
        return when (atLocal.toLocalDate()) {
            today -> atLocal.format(HHMM)
            today.plusDays(1) -> "tomorrow ${atLocal.format(HHMM)}"
            else -> atLocal.format(DAY_HHMM)
        }
    }

    fun label(status: DiscountStatus, now: ZonedDateTime): DiscountLabel {
        val at = whenLabel(status.changesAt, now)
        val m = status.minutesUntilChange
        // When the discount is on, the next change is the start of a peak; when that peak is over
        // is the change after it. (A peak window never spans midnight, so a clock time is enough.)
        val peakEnd = PeakRules.nextChange(status.changesAt)
            .withZoneSameInstant(PeakRules.ZONE).format(HHMM)
        return when (status.phase) {
            DiscountPhase.PEAK_ENDING -> DiscountLabel(
                title = "Wait $m min for discount",
                compactTitle = "Wait $m min",
                subtitle = "Off-peak from $at",
                compactSubtitle = "for discount",
            )
            DiscountPhase.OFF_PEAK_ENDING -> DiscountLabel(
                title = "Discount ends in $m min",
                compactTitle = "Ends in $m min",
                subtitle = "Check in before $at · peak ends $peakEnd",
                compactSubtitle = "before $at",
            )
            DiscountPhase.PEAK -> DiscountLabel(
                title = "Peak hours · no discount",
                compactTitle = "Peak hours",
                subtitle = "Discount from $at",
                compactSubtitle = "from $at",
            )
            DiscountPhase.OFF_PEAK -> DiscountLabel(
                title = "Off-peak · discount active",
                compactTitle = "Discount on",
                subtitle = "Peak starts $at · ends $peakEnd",
                compactSubtitle = "peak $at–$peakEnd",
            )
        }
    }
}
