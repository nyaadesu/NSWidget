package app.nswidget.data

import org.json.JSONArray
import org.json.JSONObject

/** A station the user cares about, e.g. Home or Work. [label] is what the widget shows. */
data class Favourite(val uic: String, val name: String, val label: String)

object Favourites {
    /** Each favourite costs one journey-planner call per refresh, so keep the list short. */
    const val MAX = 3

    /** Longest label that still fits a small tag on the widget. */
    const val MAX_LABEL = 10

    fun encode(favourites: List<Favourite>): String = JSONArray().apply {
        favourites.forEach { put(JSONObject().put("u", it.uic).put("n", it.name).put("l", it.label)) }
    }.toString()

    fun decode(json: String?): List<Favourite> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Favourite(o.getString("u"), o.getString("n"), o.getString("l"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Identifies a set of planned journeys: from this station to these favourites. */
    fun key(originUic: String, favourites: List<Favourite>): String =
        originUic + "|" + favourites.map { it.uic }.sorted().joinToString(",")

    /** The label as it fits on a widget tag. */
    fun shortLabel(label: String): String =
        if (label.length <= MAX_LABEL) label else label.take(MAX_LABEL - 1) + "…"
}
