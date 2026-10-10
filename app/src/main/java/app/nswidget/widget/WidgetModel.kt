package app.nswidget.widget

import app.nswidget.data.Departure
import app.nswidget.data.Favourite
import app.nswidget.data.Favourites
import app.nswidget.data.RouteDisruption
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
    /** The train ends before its usual destination; [direction] is where it now ends. */
    val shortened: Boolean = false,
    /**
     * Disruption warnings, longest first, e.g. "not to Den Haag Centraal", then "ends early":
     * the widget shows the first that fits, in place of the "via" cities.
     */
    val notices: List<String> = emptyList(),
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

/**
 * The red warning square beside the discount banner: something disrupts the way to a favourite.
 * [url] is NS's explanation of the most serious one. [symbol] goes inside the warning triangle:
 * the first affected favourite's label when it's a single emoji or character, otherwise "!".
 */
data class DisruptionAlert(val url: String, val symbol: String, val description: String)

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
    val disruption: DisruptionAlert? = null,
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
            disruption = snapshot?.takeIf { it.stationUic.isNotBlank() }
                ?.let { disruptionAlert(it.disruptions, favourites) },
        )
    }

    /**
     * One visible character, e.g. "💼", "🇳🇱", "👩‍💻" or "W" - something that fits in the triangle.
     * Counted by hand so it behaves the same everywhere: joiners, variation selectors, skin tones
     * and keycaps belong to the character before them, and two regional letters make one flag.
     */
    internal fun isSingleSymbol(label: String): Boolean {
        val points = label.codePoints().toArray()
        if (points.isEmpty()) return false
        var symbols = 0
        var i = 0
        while (i < points.size) {
            val c = points[i]
            when {
                c == ZWJ -> i++ // joins the next character to this one
                c == VARIATION_SELECTOR || c == KEYCAP || c in SKIN_TONES -> Unit
                c in REGIONAL_LETTERS && i + 1 < points.size && points[i + 1] in REGIONAL_LETTERS -> {
                    symbols++
                    i++
                }
                else -> symbols++
            }
            i++
        }
        return symbols == 1
    }

    private const val ZWJ = 0x200D
    private const val VARIATION_SELECTOR = 0xFE0F
    private const val KEYCAP = 0x20E3
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
    private val REGIONAL_LETTERS = 0x1F1E6..0x1F1FF

    /** Most serious first: an emergency, then a disruption, then planned engineering works. */
    private val SEVERITY = listOf("CALAMITY", "DISRUPTION", "MAINTENANCE")

    /** The warning for disruptions on the way to the (current) favourites, or null if there are none. */
    fun disruptionAlert(disruptions: List<RouteDisruption>, favourites: List<Favourite>): DisruptionAlert? {
        val relevant = disruptions.filter { d -> favourites.any { it.uic == d.favouriteUic } }
        val worst = relevant.minByOrNull { SEVERITY.indexOf(it.type).let { i -> if (i < 0) SEVERITY.size else i } }
            ?: return null
        val affected = favourites.filter { f -> relevant.any { it.favouriteUic == f.uic } }
        return DisruptionAlert(
            url = NsLinks.disruptionUrl(worst.id, worst.type),
            symbol = affected.firstOrNull()?.label?.trim()?.takeIf { isSingleSymbol(it) } ?: "!",
            description = "Disruption to ${affected.joinToString(", ") { it.name }}: ${worst.head}",
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
        shortened = shortened && !cancelled,
        notices = if (cancelled) emptyList() else notices(),
    )

    private fun Departure.notices(): List<String> = when {
        shortened -> listOfNotNull(plannedDirection?.let { "not to $it" }, "ends early")
        skippedStops.isNotEmpty() -> listOfNotNull(
            "skips ${skippedStops.joinToString(", ")}",
            skippedStops.takeIf { it.size > 1 }?.let { "skips ${it.first()} +${it.size - 1}" },
            "skips stops",
        )
        else -> emptyList()
    }

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

    /**
     * Height of the discount banner: its two lines of text (15sp + 12sp, or 13sp + 11sp when
     * compact) with their line spacing, scaled by the font size, plus 2 x 5dp padding - but never
     * less than its icon badge needs. On a phone at a 1.3x font scale the banner measured 64 dp
     * with 2 x 7dp padding, so each sp of text takes ~1.42 dp at 1.0x.
     */
    fun bannerHeightDp(fontScale: Float, compact: Boolean): Float {
        val textSp = if (compact) 13f + 11f else 15f + 12f
        val icon = if (compact) 28f else 34f
        return maxOf(textSp * 1.42f * fontScale, icon) + 10f
    }

    /**
     * Height of a departure row with one line of text (tuned on a phone running a 1.3x font:
     * 26 dp had room to spare, 25 still fits the 15sp time and the platform sign).
     */
    const val SINGLE_ROW_DP = 25f

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
        val useDual = rows.take(dual).any { it.via.isNotEmpty() || it.notices.isNotEmpty() } &&
            minOf(dual, rows.size) >= minOf(single, rows.size)
        return RowLayout(dual = useDual, maxRows = if (useDual) dual else single)
    }

    /**
     * Rough width of a destination in the widget's 14sp text at the default font size, scaled by the
     * user's font scale. Measured on a real phone (caps-heavy "Den Haag Centraal" was 155 dp at a
     * 1.3x font scale, i.e. 7 dp per character at 1.0x). [bold] text (the fastest-train pill) is
     * about an eighth wider: "Rotterdam C." in bold ran straight into the text beside it.
     */
    fun estimateDirectionWidth(direction: String, fontScale: Float = 1f, bold: Boolean = false): Float =
        direction.length * 7.0f * fontScale * (if (bold) BOLD_WIDTH else 1f)

    private const val BOLD_WIDTH = 1.12f

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
