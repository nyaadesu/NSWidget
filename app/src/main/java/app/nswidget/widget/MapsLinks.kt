package app.nswidget.widget

import java.net.URLEncoder

object MapsLinks {
    /**
     * A `geo:` URI that drops a labelled pin on the station (or, without coordinates, searches
     * for "<name> station"). Any map app can handle it; Google Maps is preferred by the caller.
     */
    fun stationUri(name: String, lat: Double?, lng: Double?): String {
        val label = URLEncoder.encode("$name station", "UTF-8").replace("+", "%20")
        return if (lat != null && lng != null) {
            "geo:$lat,$lng?q=$lat,$lng($label)"
        } else {
            "geo:0,0?q=$label"
        }
    }
}
