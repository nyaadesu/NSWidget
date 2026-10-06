package app.nswidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MajorCitiesTest {
    @Test
    fun matchesNsShortNamesToCities() {
        assertEquals("Rotterdam", MajorCities.cityOf("Rotterdam C."))
        assertEquals("Rotterdam", MajorCities.cityOf("Rotterdam Blaak"))
        assertEquals("Den Haag", MajorCities.cityOf("Den Haag HS"))
        assertEquals("Amsterdam", MajorCities.cityOf("Amsterdam Bijlmer ArenA"))
        assertEquals("Schiphol", MajorCities.cityOf("Schiphol Airport"))
        assertEquals("Breda", MajorCities.cityOf("Breda-Prinsenbeek"))
        assertEquals("Utrecht", MajorCities.cityOf("utrecht c."))
    }

    @Test
    fun usesFriendlyDisplayNames() {
        assertEquals("Den Bosch", MajorCities.cityOf("'s-Hertogenbosch"))
        assertEquals("Den Bosch", MajorCities.cityOf("’s-Hertogenbosch Oost")) // typographic apostrophe
        assertEquals("Brussels", MajorCities.cityOf("Brussel-Zuid/Midi"))
    }

    @Test
    fun ignoresPlacesThatAreNotMajorOrOnlyLookSimilar() {
        assertNull(MajorCities.cityOf("Laan v NOI"))   // a Den Haag station, but the name doesn't say so
        assertNull(MajorCities.cityOf("Delfzijl"))     // starts with "Delf", not "Delft"
        assertNull(MajorCities.cityOf("Dordrechtse Kil")) // longer word, not a boundary
        assertNull(MajorCities.cityOf("Vlissingen"))
        assertNull(MajorCities.cityOf(null))
    }

    @Test
    fun viaListsCitiesInOrderWithoutRepeats() {
        val route = listOf("Rotterdam Blaak", "Rotterdam C.", "Delft", "Den Haag HS", "Den Haag C.")
        assertEquals(listOf("Rotterdam", "Delft", "Den Haag"), MajorCities.via(route, "Dordrecht", "Leiden C."))
    }

    @Test
    fun viaLeavesOutTheOriginAndDestinationCities() {
        val route = listOf("Rotterdam Alexander", "Gouda", "Utrecht C.", "Amsterdam Amstel", "Amsterdam C.")
        // Standing in Rotterdam, heading for Amsterdam: only the cities in between are interesting.
        assertEquals(listOf("Gouda", "Utrecht"), MajorCities.via(route, "Rotterdam Centraal", "Amsterdam Centraal"))
    }

    @Test
    fun viaIsCappedAndHandlesNoMatches() {
        val route = listOf("Rotterdam C.", "Delft", "Den Haag C.", "Leiden C.", "Haarlem")
        assertEquals(3, MajorCities.via(route, "Dordrecht", "Alkmaar").size)
        assertEquals(emptyList<String>(), MajorCities.via(listOf("Laan v NOI", "Vlissingen"), "Dordrecht", "Vlissingen"))
        assertEquals(emptyList<String>(), MajorCities.via(emptyList(), "Dordrecht", "Utrecht Centraal"))
    }
}
