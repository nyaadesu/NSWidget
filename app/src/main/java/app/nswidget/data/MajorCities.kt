package app.nswidget.data

/**
 * Picks the major cities a train calls at, for the small "via ..." line on the widget.
 *
 * NS lists a departure's stops by short name ("Rotterdam C.", "Den Haag HS", "Schiphol Airport"),
 * so a stop belongs to a city when its name starts with the city's name. Edit [CITIES] to change
 * which places count as major; the first entry is what's matched, the second is what's shown.
 */
object MajorCities {
    private val CITIES: List<Pair<String, String>> = listOf(
        "Amsterdam" to "Amsterdam",
        "Schiphol" to "Schiphol",
        "Haarlem" to "Haarlem",
        "Alkmaar" to "Alkmaar",
        "Leiden" to "Leiden",
        "Den Haag" to "Den Haag",
        "Delft" to "Delft",
        "Rotterdam" to "Rotterdam",
        "Dordrecht" to "Dordrecht",
        "Gouda" to "Gouda",
        "Utrecht" to "Utrecht",
        "Amersfoort" to "Amersfoort",
        "Almere" to "Almere",
        "Lelystad" to "Lelystad",
        "Zwolle" to "Zwolle",
        "Deventer" to "Deventer",
        "Apeldoorn" to "Apeldoorn",
        "Arnhem" to "Arnhem",
        "Nijmegen" to "Nijmegen",
        "Eindhoven" to "Eindhoven",
        "Tilburg" to "Tilburg",
        "Breda" to "Breda",
        "'s-Hertogenbosch" to "Den Bosch",
        "Roosendaal" to "Roosendaal",
        "Venlo" to "Venlo",
        "Roermond" to "Roermond",
        "Sittard" to "Sittard",
        "Heerlen" to "Heerlen",
        "Maastricht" to "Maastricht",
        "Enschede" to "Enschede",
        "Hengelo" to "Hengelo",
        "Groningen" to "Groningen",
        "Leeuwarden" to "Leeuwarden",
        "Brussel" to "Brussels",
        "Antwerpen" to "Antwerp",
        "Köln" to "Cologne",
    )

    /** The display name of the major city a station belongs to, or null. */
    fun cityOf(stationName: String?): String? {
        if (stationName == null) return null
        val name = stationName.trim().replace('’', '\'')
        for ((prefix, display) in CITIES) {
            if (name.startsWith(prefix, ignoreCase = true) &&
                (name.length == prefix.length || !name[prefix.length].isLetter())
            ) {
                return display
            }
        }
        return null
    }

    /**
     * Major cities along the route, in travel order, without repeats. The city of the station
     * you're standing at and the destination's city are left out - you know those already.
     */
    fun via(routeStations: List<String>, origin: String?, direction: String?, max: Int = 3): List<String> {
        val skip = setOfNotNull(cityOf(origin), cityOf(direction))
        val result = LinkedHashSet<String>()
        for (station in routeStations) {
            val city = cityOf(station) ?: continue
            if (city !in skip) result += city
            if (result.size == max) break
        }
        return result.toList()
    }
}
