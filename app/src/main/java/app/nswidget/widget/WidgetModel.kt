package app.nswidget.widget

import app.nswidget.data.Departure
import app.nswidget.data.Snapshot
import app.nswidget.discount.DiscountLabel
import app.nswidget.discount.DiscountPhase
import app.nswidget.discount.DiscountText
import app.nswidget.discount.PeakRules
import app.nswidget.discount.discountStatus
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DepartureRow(
    val time: String,
    val delayMinutes: Int,
    val category: String,
    val direction: String,
    val track: String?,
    val trackChanged: Boolean,
    val cancelled: Boolean,
    /** Major cities along the route, in order; empty when unknown or switched off. */
    val via: List<String>,
)

/** Everything the widget needs to draw itself, already resolved into display strings. */
data class WidgetModel(
    val phase: DiscountPhase,
    val label: DiscountLabel,
    val stationName: String?,
    /** Where to open the map: coordinates when known, otherwise a name search. */
    val mapUri: String?,
    /** e.g. "450 m · 10:41" or "offline · 10:41". */
    val statusLine: String?,
    val offline: Boolean,
    val rows: List<DepartureRow>,
    /** Shown in place of [rows] when there is nothing to list. */
    val message: String?,
)

object WidgetModels {
    const val MAX_ROWS = 8
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun build(now: ZonedDateTime, hasKey: Boolean, snapshot: Snapshot?, showVia: Boolean = true): WidgetModel {
        val status = discountStatus(now)
        val nowMs = now.toInstant().toEpochMilli()

        // Drop trains that have already left (the data may be a few minutes old).
        val rows = snapshot?.departures.orEmpty()
            .filter { it.actualAt >= nowMs - 60_000 }
            .take(MAX_ROWS)
            .map { it.toRow(showVia) }

        val message = when {
            !hasKey -> "Add your NS API key in the app to see departures"
            snapshot == null -> "Loading departures…"
            snapshot.stationUic.isBlank() -> snapshot.error ?: "Allow location or pick a station in the app"
            rows.isEmpty() -> snapshot.error ?: "No upcoming departures"
            else -> null
        }

        val statusLine = snapshot?.takeIf { it.stationUic.isNotBlank() }?.let { s ->
            val time = formatTime(s.fetchedAt)
            listOfNotNull(
                s.distanceMeters?.let { formatDistance(it) },
                if (s.error != null) "offline $time" else time,
            ).joinToString(" · ")
        }

        return WidgetModel(
            phase = status.phase,
            label = DiscountText.label(status, now),
            stationName = snapshot?.stationName?.takeIf { it.isNotBlank() },
            mapUri = snapshot?.takeIf { it.stationName.isNotBlank() }
                ?.let { MapsLinks.stationUri(it.stationName, it.lat, it.lng) },
            statusLine = statusLine,
            offline = snapshot?.error != null,
            rows = rows,
            message = message,
        )
    }

    private fun Departure.toRow(showVia: Boolean) = DepartureRow(
        time = formatTime(actualAt),
        delayMinutes = delayMinutes,
        category = category,
        direction = direction,
        track = track,
        trackChanged = trackChanged,
        cancelled = cancelled,
        via = if (showVia && !cancelled) via else emptyList(),
    )

    private fun formatTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(PeakRules.ZONE).format(HHMM)

    /** The "via" line never shows more than this many cities; more than two reads as clutter. */
    const val MAX_VIA = 2

    /** Empty space kept between a destination and its "via" text. */
    const val VIA_GAP_DP = 16f

    /**
     * Rough width of a destination in the widget's 14sp text. Measured against real captures, with
     * caps-heavy names like "Den Haag Centraal" as the worst case, and scaled by the user's font size.
     */
    fun estimateDirectionWidth(direction: String, fontScale: Float = 1f): Float =
        direction.length * 9.1f * fontScale

    /**
     * "via Rotterdam, Delft" using as many of [cities] (at most [MAX_VIA]) as fit in [budgetDp]
     * at 10sp, or null if not even the first fits. Estimates err on the wide side so the text is
     * never clipped or squeezed against its neighbours.
     */
    fun viaText(cities: List<String>, budgetDp: Float, fontScale: Float = 1f): String? {
        for (count in minOf(cities.size, MAX_VIA) downTo 1) {
            val text = "via " + cities.take(count).joinToString(", ")
            if (text.length * 5.8f * fontScale <= budgetDp) return text
        }
        return null
    }

    internal fun formatDistance(meters: Int): String =
        if (meters < 1000) "$meters m" else String.format(Locale.ENGLISH, "%.1f km", meters / 1000.0)
}
