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
            via = o.optJSONArray("v")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty(),
        )
    }
}

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
) {
    fun toJson(): String = JSONObject().apply {
        put("uic", stationUic)
        put("name", stationName)
        distanceMeters?.let { put("dist", it) }
        lat?.let { put("lat", it) }
        lng?.let { put("lng", it) }
        put("at", fetchedAt)
        error?.let { put("err", it) }
        put("deps", JSONArray().apply { departures.forEach { put(it.toJson()) } })
    }.toString()

    companion object {
        fun fromJson(json: String): Snapshot? = try {
            val o = JSONObject(json)
            val arr = o.getJSONArray("deps")
            Snapshot(
                stationUic = o.optString("uic"),
                stationName = o.optString("name"),
                distanceMeters = if (o.has("dist")) o.getInt("dist") else null,
                fetchedAt = o.getLong("at"),
                departures = (0 until arr.length()).map { Departure.fromJson(arr.getJSONObject(it)) },
                error = if (o.has("err")) o.getString("err") else null,
                lat = if (o.has("lat")) o.getDouble("lat") else null,
                lng = if (o.has("lng")) o.getDouble("lng") else null,
            )
        } catch (e: Exception) {
            null
        }
    }
}
