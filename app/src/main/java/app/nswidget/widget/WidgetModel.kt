package app.nswidget.widget

import app.nswidget.data.Departure
import app.nswidget.data.Favourite
import app.nswidget.data.Favourites
import app.nswidget.data.Snapshot
import app.nswidget.data.TripOption
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
    /** Favourite stations this train gets you to, fastest-way-there first. */
    val tags: List<RowTag> = emptyList(),
) {
    /** How the row is highlighted: only by colour, no extra text or icons. */
    val highlight: Highlight
        get() = when {
            tags.any { it.fastest } -> Highlight.FASTEST
            tags.isNotEmpty() -> Highlight.STOPS
            else -> Highlight.NONE
        }
}

enum class Highlight {
    NONE,

    /** The train stops at a favourite station: a soft tint. */
    STOPS,

    /** Boarding this train is the fastest way to a favourite (maybe with a change): a strong tint. */
    FASTEST,
}

/**
 * A favourite station a departure gets you to. [fastest]: boarding this train is the quickest way
 * there (possibly with [transfers] changes); otherwise the train simply stops there.
 */
data class RowTag(val label: String, val fastest: Boolean, val transfers: Int = 0)

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

    fun build(
        now: ZonedDateTime,
        hasKey: Boolean,
        snapshot: Snapshot?,
        showVia: Boolean = true,
        favourites: List<Favourite> = emptyList(),
    ): WidgetModel {
        val status = discountStatus(now)
        val nowMs = now.toInstant().toEpochMilli()

        // Drop trains that have already left (the data may be a few minutes old).
        val departures = snapshot?.departures.orEmpty()
            .filter { it.actualAt >= nowMs - 60_000 }
            .take(MAX_ROWS)
        val tags = favouriteTags(departures, favourites, snapshot?.trips.orEmpty(), nowMs)
        val rows = departures.mapIndexed { i, departure -> departure.toRow(showVia, tags[i]) }

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

    private fun Departure.toRow(showVia: Boolean, tags: List<RowTag>) = DepartureRow(
        time = formatTime(actualAt),
        delayMinutes = delayMinutes,
        category = category,
        direction = direction,
        track = track,
        trackChanged = trackChanged,
        cancelled = cancelled,
        via = if (showVia && !cancelled) via else emptyList(),
        tags = tags,
    )

    /**
     * For each departure, the favourite stations it gets you to (aligned with [departures]).
     *
     * - Fastest: per favourite, the planned journey that arrives earliest and can still be caught;
     *   the departure it starts with gets a "fastest" tag, even if the journey involves changes.
     * - Stops there: the favourite is one of the train's stops, is where it terminates, or the
     *   planner has a direct journey starting with this train.
     * Cancelled trains get no tags.
     */
    fun favouriteTags(
        departures: List<Departure>,
        favourites: List<Favourite>,
        trips: List<TripOption>,
        nowMs: Long,
    ): List<List<RowTag>> {
        val fastest = favourites.associate { favourite ->
            favourite.uic to trips
                .filter { it.favouriteUic == favourite.uic && it.departAt >= nowMs - 60_000 }
                .minByOrNull { it.arriveAt }
        }
        return departures.map { departure ->
            if (departure.cancelled) return@map emptyList()
            val number = departure.trainNumber
            val tags = favourites.mapNotNull { favourite ->
                val best = fastest[favourite.uic]
                val label = Favourites.shortLabel(favourite.label)
                when {
                    best != null && number != null && best.trainNumber == number ->
                        RowTag(label, fastest = true, transfers = best.transfers)
                    favourite.uic in departure.stops ||
                        departure.direction.trim().equals(favourite.name.trim(), ignoreCase = true) ||
                        (number != null && trips.any {
                            it.favouriteUic == favourite.uic && it.transfers == 0 && it.trainNumber == number
                        }) ->
                        RowTag(label, fastest = false)
                    else -> null
                }
            }
            tags.sortedByDescending { it.fastest }
        }
    }

    private fun formatTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(PeakRules.ZONE).format(HHMM)

    /** The "via" line never shows more than this many cities. */
    const val MAX_VIA = 3

    /** Cities shown when "via" has to share a line with the destination (fewer if they don't fit). */
    const val MAX_VIA_INLINE = 3

    /** Empty space kept between a destination and its "via" text when they share a line. */
    const val VIA_GAP_DP = 12f

    /** Text size of the cities list: beside the destination, and on its own line under it. */
    const val VIA_INLINE_SP = 9
    const val VIA_DUAL_SP = 8

    /** Estimated width of one character of the cities text at the default font size. */
    fun viaCharDp(sp: Int): Float = sp * 0.46f

    /** Height of a departure row with one line of text (tuned on a phone running a 1.3x font). */
    const val SINGLE_ROW_DP = 26f

    /** Height of a row with a destination plus a "via" line at the default font size. */
    const val DUAL_ROW_DP = 28f

    /** Two lines of text grow with the user's font size, so the row has to as well. */
    fun dualRowDp(fontScale: Float): Float = DUAL_ROW_DP * maxOf(1f, fontScale)

    /** [dual]: each row is a destination with its "via" cities underneath. */
    data class RowLayout(val dual: Boolean, val maxRows: Int)

    /**
     * Chooses between roomy two-line rows (destination + cities underneath) and compact one-line rows.
     * Two lines are only used when they show at least as many departures as one line would, so
     * adding the cities never costs a departure; short widgets keep the compact layout.
     */
    fun rowLayout(rows: List<DepartureRow>, availableDp: Float, fontScale: Float = 1f): RowLayout {
        val single = (availableDp / SINGLE_ROW_DP).toInt().coerceIn(1, MAX_ROWS)
        val dual = (availableDp / dualRowDp(fontScale)).toInt().coerceIn(1, MAX_ROWS)
        val useDual = rows.take(dual).any { it.via.isNotEmpty() } &&
            minOf(dual, rows.size) >= minOf(single, rows.size)
        return RowLayout(dual = useDual, maxRows = if (useDual) dual else single)
    }

    /**
     * Rough width of a destination in the widget's 14sp text at the default font size, scaled by the
     * user's font scale. Measured on a real phone (caps-heavy "Den Haag Centraal" was 155 dp at a
     * 1.3x font scale, i.e. 7 dp per character at 1.0x).
     */
    fun estimateDirectionWidth(direction: String, fontScale: Float = 1f): Float =
        direction.length * 7.0f * fontScale

    /**
     * "Rotterdam, Delft" using as many of [cities] (at most [maxCities]) as fit in [budgetDp],
     * or null if not even the first fits. [charDp] is the estimated width of one character at the
     * default font size (4.6 for 10sp text, 4.2 for 9sp; measured at 4.2 and 3.8, plus a safety
     * margin) and is scaled by [fontScale], so text is never clipped or squeezed against its
     * neighbours.
     */
    fun viaText(
        cities: List<String>,
        budgetDp: Float,
        fontScale: Float = 1f,
        charDp: Float = 4.6f,
        maxCities: Int = MAX_VIA,
    ): String? {
        for (count in minOf(cities.size, maxCities) downTo 1) {
            val text = cities.take(count).joinToString(", ")
            if (text.length * charDp * fontScale <= budgetDp) return text
        }
        return null
    }

    internal fun formatDistance(meters: Int): String =
        if (meters < 1000) "$meters m" else String.format(Locale.ENGLISH, "%.1f km", meters / 1000.0)
}
