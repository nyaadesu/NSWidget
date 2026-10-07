package app.nswidget.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The NS station list, cached on disk in a compact form (it rarely changes). */
class StationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "stations.json")

    /** Blocking: call from a background thread. */
    fun load(apiKey: String): List<Station> {
        cached(maxAgeMs = REFRESH_AFTER_MS)?.let { return it }
        return try {
            ApiUsage(appContext).record()
            val stations = NsApi.parseStations(NsApi.fetchStationsJson(apiKey))
            if (stations.isNotEmpty()) file.writeText(serialize(stations))
            stations
        } catch (e: Exception) {
            // An out-of-date list is far better than none.
            cached(maxAgeMs = Long.MAX_VALUE) ?: throw e
        }
    }

    private fun cached(maxAgeMs: Long): List<Station>? {
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > maxAgeMs) return null
        return try {
            deserialize(file.readText()).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun serialize(stations: List<Station>): String = JSONArray().apply {
        stations.forEach {
            put(
                JSONObject()
                    .put("u", it.uic)
                    .put("c", it.code)
                    .put("n", it.name)
                    .put("a", it.lat)
                    .put("o", it.lng),
            )
        }
    }.toString()

    private fun deserialize(json: String): List<Station> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Station(o.getString("u"), o.getString("c"), o.getString("n"), o.getDouble("a"), o.getDouble("o"))
        }
    }

    private companion object {
        const val REFRESH_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
