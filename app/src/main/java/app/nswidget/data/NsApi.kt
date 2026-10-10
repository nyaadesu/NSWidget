package app.nswidget.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class NsApiException(message: String, val httpCode: Int? = null) : IOException(message)

/**
 * Minimal client for the NS "Reisinformatie" API (https://apiportal.ns.nl).
 * Needs a free subscription key, sent in the Ocp-Apim-Subscription-Key header.
 */
object NsApi {
    private const val BASE = "https://gateway.apiportal.ns.nl"

    fun fetchStationsJson(apiKey: String): String =
        get("$BASE/reisinformatie-api/api/v2/stations", apiKey)

    /** [originName] is the station being departed from; it keeps that city out of the "via" list. */
    fun fetchDepartures(apiKey: String, uicCode: String, originName: String?, maxJourneys: Int = 20): List<Departure> {
        val uic = URLEncoder.encode(uicCode, "UTF-8")
        val url = "$BASE/reisinformatie-api/api/v2/departures?uicCode=$uic&maxJourneys=$maxJourneys"
        return parseDepartures(get(url, apiKey), originName)
    }

    /** Journey-planner options from one station to another, starting now, and what disrupts them. */
    fun fetchTrips(apiKey: String, fromUic: String, toUic: String): TripPlan {
        val from = URLEncoder.encode(fromUic, "UTF-8")
        val to = URLEncoder.encode(toUic, "UTF-8")
        val url = "$BASE/reisinformatie-api/api/v3/trips?originUicCode=$from&destinationUicCode=$to"
        val json = get(url, apiKey)
        return TripPlan(parseTrips(json, toUic), parseTripDisruptions(json, toUic))
    }

    private fun get(url: String, apiKey: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                throw NsApiException(
                    when (code) {
                        401, 403 -> "NS rejected the API key (HTTP $code)"
                        429 -> "NS API rate limit reached"
                        else -> "NS API error (HTTP $code)"
                    },
                    code,
                )
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---- Parsing (pure functions, unit-tested) ----

    fun parseDepartures(json: String, originName: String? = null): List<Departure> {
        val list = JSONObject(json).optJSONObject("payload")?.optJSONArray("departures")
            ?: return emptyList()
        val result = ArrayList<Departure>(list.length())
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val planned = parseTime(o.str("plannedDateTime")) ?: continue
            val actual = parseTime(o.str("actualDateTime")) ?: planned
            val product = o.optJSONObject("product")
            val category = product?.str("categoryCode")
                ?: o.str("trainCategory")
                ?: product?.str("shortCategoryName")
                ?: ""
            val notes = o.optJSONArray("messages")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.str("message") }
            }.orEmpty()
            val cancelled = o.optBoolean("cancelled", false) || notes.any { Disruptions.isCancelled(it) }
            var direction = o.str("direction") ?: ""
            var route = o.optJSONArray("routeStations")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            }.orEmpty()
            // A shortened train: show where it really ends, and forget the stops it no longer reaches.
            val endsAt = if (cancelled) null else notes.firstNotNullOfOrNull { Disruptions.endsAt(it) }
            var plannedDirection: String? = null
            if (endsAt != null) {
                val cut = route.indexOfFirst { Disruptions.sameStation(it.str("mediumName"), endsAt) }
                if (cut >= 0) route = route.take(cut + 1)
                if (!Disruptions.sameStation(direction, endsAt)) {
                    plannedDirection = direction.takeIf { it.isNotBlank() }
                    direction = endsAt
                }
            }
            val skipped = notes.flatMap { Disruptions.skippedStops(it) }
            if (skipped.isNotEmpty()) {
                route = route.filterNot { r -> skipped.any { Disruptions.sameStation(r.str("mediumName"), it) } }
            }
            val stopNames = route.mapNotNull { it.str("mediumName") }
            result += Departure(
                plannedAt = planned,
                actualAt = actual,
                direction = direction,
                category = category.take(4),
                plannedTrack = o.str("plannedTrack"),
                actualTrack = o.str("actualTrack"),
                cancelled = cancelled,
                via = MajorCities.via(stopNames, originName, direction),
                trainNumber = product?.str("number"),
                stops = route.mapNotNull { it.str("uicCode") },
                shortened = endsAt != null,
                plannedDirection = plannedDirection,
                skippedStops = skipped,
            )
        }
        return result
    }

    fun parseStations(json: String): List<Station> {
        val list = JSONObject(json).optJSONArray("payload") ?: return emptyList()
        val result = ArrayList<Station>(list.length())
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val uic = o.str("UICCode") ?: continue
            val name = o.optJSONObject("namen")?.str("lang") ?: continue
            if (o.isNull("lat") || o.isNull("lng")) continue
            val country = o.str("land")
            if (country != null && country != "NL") continue
            if (!o.optBoolean("heeftVertrektijden", true)) continue
            result += Station(
                uic = uic,
                code = o.str("code") ?: "",
                name = name,
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
            )
        }
        return result
    }

    /**
     * Turns a journey-planner response into one [TripOption] per usable journey. Walking legs are
     * skipped; journeys that are cancelled or impossible, or have a cancelled leg, are dropped.
     */
    fun parseTrips(json: String, favouriteUic: String): List<TripOption> {
        val root = JSONObject(json)
        val trips = root.optJSONArray("trips")
            ?: root.optJSONObject("payload")?.optJSONArray("trips")
            ?: return emptyList()
        val result = ArrayList<TripOption>(trips.length())
        for (i in 0 until trips.length()) {
            val trip = trips.optJSONObject(i) ?: continue
            val status = trip.str("status")
            if (status == "CANCELLED" || status == "NOT_POSSIBLE") continue
            val legs = trip.optJSONArray("legs") ?: continue
            val rides = (0 until legs.length())
                .mapNotNull { legs.optJSONObject(it) }
                .filter { it.optJSONObject("product")?.str("number") != null }
            if (rides.isEmpty() || rides.any { it.optBoolean("cancelled", false) }) continue
            val trainNumber = rides.first().optJSONObject("product")?.str("number") ?: continue
            val departAt = stopTime(rides.first().optJSONObject("origin")) ?: continue
            val arriveAt = stopTime(rides.last().optJSONObject("destination")) ?: continue
            result += TripOption(favouriteUic, trainNumber, departAt, arriveAt, transfers = rides.size - 1)
        }
        return result
    }

    private val DISRUPTION_TYPES = setOf("CALAMITY", "DISRUPTION", "MAINTENANCE")

    /**
     * The disruptions and engineering works NS attaches to the planned journeys (each journey's
     * main message and its rides' messages), once each. Other notes, like "shorter train, extra
     * busy", are left out.
     */
    fun parseTripDisruptions(json: String, favouriteUic: String): List<RouteDisruption> {
        val root = JSONObject(json)
        val trips = root.optJSONArray("trips")
            ?: root.optJSONObject("payload")?.optJSONArray("trips")
            ?: return emptyList()
        val found = LinkedHashMap<String, RouteDisruption>()
        fun add(message: JSONObject?) {
            val id = message?.str("id") ?: return
            val type = message.str("type") ?: return
            if (type !in DISRUPTION_TYPES || id in found) return
            val head = message.str("head") ?: message.str("text") ?: return
            found[id] = RouteDisruption(favouriteUic, id, type, head)
        }
        for (i in 0 until trips.length()) {
            val trip = trips.optJSONObject(i) ?: continue
            add(trip.optJSONObject("primaryMessage")?.optJSONObject("message"))
            val legs = trip.optJSONArray("legs") ?: continue
            for (l in 0 until legs.length()) {
                val messages = legs.optJSONObject(l)?.optJSONArray("messages") ?: continue
                for (m in 0 until messages.length()) add(messages.optJSONObject(m))
            }
        }
        return found.values.toList()
    }

    /** Real-time if known, otherwise the timetable. */
    private fun stopTime(stop: JSONObject?): Long? =
        stop?.let { parseTime(it.str("actualDateTime")) ?: parseTime(it.str("plannedDateTime")) }

    // NS timestamps look like "2026-10-06T10:04:00+0200" (no colon in the offset).
    private val NS_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    internal fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(s, NS_TIME).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                OffsetDateTime.parse(s).toInstant().toEpochMilli()
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    /** org.json returns the string "null" for JSON nulls; this returns a real null instead. */
    private fun JSONObject.str(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}

/** NS couldn't be reached at all (no network, network switching, timeout), as opposed to NS refusing. */
fun Exception.isConnectionError(): Boolean = this is IOException && this !is NsApiException

fun Exception.friendlyMessage(): String = when (this) {
    is NsApiException -> message ?: "NS API error"
    is IOException -> "No connection to NS"
    else -> "Couldn't load departures"
}
