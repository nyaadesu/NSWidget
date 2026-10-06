package app.nswidget.data

import android.content.Context
import androidx.core.content.edit

/** A location fix; [atMillis] is the time of the fix itself, not of when we saved it. */
data class SavedLocation(val lat: Double, val lng: Double, val atMillis: Long)

class Settings(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(K_API_KEY, "").orEmpty()
        set(value) = prefs.edit { putString(K_API_KEY, value.trim()) }

    /** Follow the user's location; falls back to the fixed station when no location is known. */
    var useNearest: Boolean
        get() = prefs.getBoolean(K_NEAREST, true)
        set(value) = prefs.edit { putBoolean(K_NEAREST, value) }

    /** Show the small "via Rotterdam, Delft" line under each destination. */
    var showVia: Boolean
        get() = prefs.getBoolean(K_SHOW_VIA, true)
        set(value) = prefs.edit { putBoolean(K_SHOW_VIA, value) }

    var fixedStationUic: String?
        get() = prefs.getString(K_FIXED_UIC, null)
        set(value) = prefs.edit { putString(K_FIXED_UIC, value) }

    var fixedStationName: String?
        get() = prefs.getString(K_FIXED_NAME, null)
        set(value) = prefs.edit { putString(K_FIXED_NAME, value) }

    /** Coordinates of the fixed station (lat, lng); null for stations picked before this was stored. */
    var fixedStationLatLng: Pair<Double, Double>?
        get() {
            if (!prefs.contains(K_FIXED_LAT) || !prefs.contains(K_FIXED_LNG)) return null
            return Double.fromBits(prefs.getLong(K_FIXED_LAT, 0L)) to
                Double.fromBits(prefs.getLong(K_FIXED_LNG, 0L))
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(K_FIXED_LAT)
                remove(K_FIXED_LNG)
            } else {
                putLong(K_FIXED_LAT, value.first.toRawBits())
                putLong(K_FIXED_LNG, value.second.toRawBits())
            }
        }

    var location: SavedLocation?
        get() {
            if (!prefs.contains(K_LAT) || !prefs.contains(K_LNG)) return null
            return SavedLocation(
                lat = Double.fromBits(prefs.getLong(K_LAT, 0L)),
                lng = Double.fromBits(prefs.getLong(K_LNG, 0L)),
                atMillis = prefs.getLong(K_LOC_AT, 0L),
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(K_LAT)
                remove(K_LNG)
                remove(K_LOC_AT)
            } else {
                putLong(K_LAT, value.lat.toRawBits())
                putLong(K_LNG, value.lng.toRawBits())
                putLong(K_LOC_AT, value.atMillis)
            }
        }

    private companion object {
        const val K_API_KEY = "api_key"
        const val K_NEAREST = "use_nearest"
        const val K_SHOW_VIA = "show_via"
        const val K_FIXED_UIC = "fixed_uic"
        const val K_FIXED_NAME = "fixed_name"
        const val K_FIXED_LAT = "fixed_lat"
        const val K_FIXED_LNG = "fixed_lng"
        const val K_LAT = "lat"
        const val K_LNG = "lng"
        const val K_LOC_AT = "loc_at"
    }
}
