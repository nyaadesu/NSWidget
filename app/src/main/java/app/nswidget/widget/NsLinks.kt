package app.nswidget.widget

import java.net.URLEncoder

object NsLinks {
    private const val SITUATION = "https://www.ns.nl/reisinformatie/actuele-situatie-op-het-spoor/"

    /**
     * NS's own explanation of a disruption: the detail page of "Actuele situatie op het spoor"
     * (the NS app opens it if it handles ns.nl links). Unknown types get the overview page.
     */
    fun disruptionUrl(id: String, type: String): String {
        val page = when (type) {
            "DISRUPTION" -> "disruption"
            "MAINTENANCE" -> "maintenance"
            "CALAMITY" -> "calamity"
            else -> return SITUATION
        }
        return "$SITUATION$page?id=${URLEncoder.encode(id, "UTF-8")}"
    }
}
