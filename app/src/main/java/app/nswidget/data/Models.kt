package app.nswidget.data

import org.json.JSONArray
import org.json.JSONObject

data class Station(
    val uic: String,
    val code: String,
    val name: String,
    val lat: Double,
    val lng: Double,
)

data class Departure(
    /** Epoch millis of the timetable time. */
    val plannedAt: Long,
    /** Epoch millis of the real-time estimate; equals [plannedAt] when there is none. */
    val actualAt: Long,
    val direction: String,
    /** Short train type such as "IC" or "SPR". */
    val category: String,
    val plannedTrack: String?,
    val actualTrack: String?,
    val cancelled: Boolean,
    /** Major cities the train calls at, in order (see [MajorCities]). */
    val via: List<String> = emptyList(),
    /** NS train number, e.g. "2150"; links a departure to a planned journey. */
    val trainNumber: String? = null,
    /** UIC codes of the stations the train calls at after this one. */
    val stops: List<String> = emptyList(),
    /** NS says the train ends early; [direction] is then where it really ends. */
    val shortened: Boolean = false,
    /** Where a [shortened] train was meant to go, if NS hadn't already changed [direction]. */
    val plannedDirection: String? = null,
    /** Stops NS says the train skips today (station names as NS wrote them). */
    val skippedStops: List<String> = emptyList(),
) {
    val track: String? get() = actualTrack ?: plannedTrack
    val trackChanged: Boolean
        get() = actualTrack != null && plannedTrack != null && actualTrack != plannedTrack
    val delayMinutes: Int get() = ((actualAt - plannedAt) / 60_000).toInt()

    fun toJson(): JSONObject = JSONObject().apply {
        put("p", plannedAt)
        put("a", actualAt)
        put("d", direction)
        put("c", category)
        plannedTrack?.let { put("pt", it) }
        actualTrack?.let { put("at", it) }
        put("x", cancelled)
        if (via.isNotEmpty()) put("v", JSONArray(via))
        trainNumber?.let { put("n", it) }
        if (stops.isNotEmpty()) put("s", JSONArray(stops))
        if (shortened) put("sh", true)
        plannedDirection?.let { put("pd", it) }
        if (skippedStops.isNotEmpty()) put("sk", JSONArray(skippedStops))
    }

    companion object {
        fun fromJson(o: JSONObject): Departure = Departure(
            plannedAt = o.getLong("p"),
            actualAt = o.getLong("a"),
            direction = o.optString("d"),
            category = o.optString("c"),
            plannedTrack = if (o.has("pt")) o.getString("pt") else null,
            actualTrack = if (o.has("at")) o.getString("at") else null,
            cancelled = o.optBoolean("x", false),
            via = o.optJSONArray("v").strings(),
            trainNumber = if (o.has("n")) o.getString("n") else null,
            stops = o.optJSONArray("s").strings(),
            shortened = o.optBoolean("sh", false),
            plannedDirection = if (o.has("pd")) o.getString("pd") else null,
            skippedStops = o.optJSONArray("sk").strings(),
        )
    }
}

/**
 * One option from the NS journey planner for getting to a favourite station: which train to board
 * here, when it leaves, when you arrive, and how many changes it takes.
 */
data class TripOption(
    val favouriteUic: String,
    /** Train number of the first train of the journey - the one you board here. */
    val trainNumber: String,
    val departAt: Long,
    val arriveAt: Long,
    val transfers: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("f", favouriteUic)
        .put("n", trainNumber)
        .put("d", departAt)
        .put("a", arriveAt)
        .put("t", transfers)

    companion object {
        fun fromJson(o: JSONObject) = TripOption(
            favouriteUic = o.getString("f"),
            trainNumber = o.getString("n"),
            departAt = o.getLong("d"),
            arriveAt = o.getLong("a"),
            transfers = o.optInt("t", 0),
        )
    }
}

/**
 * A disruption or engineering work NS says affects journeys to a favourite station, found in the
 * journey planner's results. [type] is NS's: "DISRUPTION", "MAINTENANCE" or "CALAMITY".
 */
data class RouteDisruption(
    val favouriteUic: String,
    val id: String,
    val type: String,
    /** NS's one-line summary, e.g. "Door een seinstoring: tussen Delft en Rotterdam rijden er geen treinen." */
    val head: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("f", favouriteUic)
        .put("i", id)
        .put("t", type)
        .put("h", head)

    companion object {
        fun fromJson(o: JSONObject) = RouteDisruption(
            favouriteUic = o.getString("f"),
            id = o.getString("i"),
            type = o.optString("t"),
            head = o.optString("h"),
        )
    }
}

/** What the journey planner said about getting to one favourite station. */
data class TripPlan(val options: List<TripOption>, val disruptions: List<RouteDisruption>)

/** What the widget shows: the last successful (or failed) refresh. */
data class Snapshot(
    /** Blank when no station could be chosen. */
    val stationUic: String,
    val stationName: String,
    /** Distance to the station in "nearest" mode, otherwise null. */
    val distanceMeters: Int?,
    val fetchedAt: Long,
    val departures: List<Departure>,
    /** Set when the latest refresh failed; [departures] then hold the previous data. */
    val error: String?,
    /** Station coordinates, for the "open in maps" button. Null if unknown. */
    val lat: Double? = null,
    val lng: Double? = null,
    /** When we last tried to fetch; differs from [fetchedAt] (last success) after a failure. */
    val attemptedAt: Long = fetchedAt,
    /** Journey-planner results towards the favourite stations (see [TripOption]). */
    val trips: List<TripOption> = emptyList(),
    /** When [trips] were last requested. */
    val tripsFetchedAt: Long = 0L,
    /** Which station and favourites [trips] were planned for; see [Favourites.key]. */
    val tripsKey: String = "",
    /** The latest refresh failed because NS couldn't be reached (not because NS said no). */
    val connectionError: Boolean = false,
    /** Disruptions on the way to the favourites, planned along with [trips]. */
    val disruptions: List<RouteDisruption> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("uic", stationUic)
        put("name", stationName)
        distanceMeters?.let { put("dist", it) }
        lat?.let { put("lat", it) }
        lng?.let { put("lng", it) }
        put("try", attemptedAt)
        put("at", fetchedAt)
        error?.let { put("err", it) }
        put("deps", JSONArray().apply { departures.forEach { put(it.toJson()) } })
        if (trips.isNotEmpty()) put("trips", JSONArray().apply { trips.forEach { put(it.toJson()) } })
        put("tat", tripsFetchedAt)
        put("tkey", tripsKey)
        if (connectionError) put("net", true)
        if (disruptions.isNotEmpty()) put("dis", JSONArray().apply { disruptions.forEach { put(it.toJson()) } })
    }.toString()

    companion object {
        fun fromJson(json: String): Snapshot? = try {
            val o = JSONObject(json)
            val arr = o.getJSONArray("deps")
            val fetchedAt = o.getLong("at")
            val trips = o.optJSONArray("trips")
            val disruptions = o.optJSONArray("dis")
            Snapshot(
                stationUic = o.optString("uic"),
                stationName = o.optString("name"),
                distanceMeters = if (o.has("dist")) o.getInt("dist") else null,
                fetchedAt = fetchedAt,
                departures = (0 until arr.length()).map { Departure.fromJson(arr.getJSONObject(it)) },
                error = if (o.has("err")) o.getString("err") else null,
                lat = if (o.has("lat")) o.getDouble("lat") else null,
                lng = if (o.has("lng")) o.getDouble("lng") else null,
                attemptedAt = if (o.has("try")) o.getLong("try") else fetchedAt,
                trips = trips?.let { t -> (0 until t.length()).map { TripOption.fromJson(t.getJSONObject(it)) } }.orEmpty(),
                tripsFetchedAt = o.optLong("tat", 0L),
                tripsKey = o.optString("tkey"),
                connectionError = o.optBoolean("net", false),
                disruptions = disruptions
                    ?.let { d -> (0 until d.length()).map { RouteDisruption.fromJson(d.getJSONObject(it)) } }
                    .orEmpty(),
            )
        } catch (e: Exception) {
            null
        }
    }
}

private fun JSONArray?.strings(): List<String> =
    this?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty()
