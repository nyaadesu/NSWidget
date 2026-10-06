package app.nswidget.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance in metres (haversine). */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** The closest station and its distance in metres. */
fun nearestStation(stations: List<Station>, lat: Double, lng: Double): Pair<Station, Int>? {
    val best = stations.minByOrNull { distanceMeters(lat, lng, it.lat, it.lng) } ?: return null
    return best to distanceMeters(lat, lng, best.lat, best.lng).roundToInt()
}
