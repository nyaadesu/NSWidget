package app.nswidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisruptionsTest {
    @Test
    fun readsWhereAShortenedTrainEnds() {
        assertEquals("Rotterdam Centraal", Disruptions.endsAt("Rijdt niet verder dan Rotterdam Centraal"))
        assertEquals("Rotterdam Centraal", Disruptions.endsAt("Let op: rijdt vandaag niet verder dan Rotterdam Centraal."))
        assertEquals("Schiedam Centrum", Disruptions.endsAt("Rijdt niet verder dan Schiedam Centrum. Reis via Den Haag HS."))
        assertEquals("Rotterdam C.", Disruptions.endsAt("Rijdt niet verder dan Rotterdam C."))
        assertEquals("Rotterdam C.", Disruptions.endsAt("Rijdt niet verder dan Rotterdam C. door een sein- en wisselstoring"))
        assertEquals("Rotterdam C.", Disruptions.endsAt("Ends at Rotterdam C. due to signal - switch failure"))
        assertEquals("Rotterdam Centraal", Disruptions.endsAt("Terminates at Rotterdam Centraal"))
        assertEquals("'s-Hertogenbosch", Disruptions.endsAt("Does not run further than 's-Hertogenbosch"))
        assertNull(Disruptions.endsAt("Extra trein"))
        assertNull(Disruptions.endsAt("Rijdt niet"))
    }

    @Test
    fun readsSkippedStops() {
        assertEquals(listOf("Delft Campus"), Disruptions.skippedStops("Stopt niet in Delft Campus"))
        assertEquals(listOf("Hardinxveld-G."), Disruptions.skippedStops("Does not stop at Hardinxveld-G."))
        assertEquals(
            listOf("Delft Campus", "Delft", "Schiedam Centrum"),
            Disruptions.skippedStops("Stopt vandaag niet in Delft Campus, Delft en Schiedam Centrum."),
        )
        assertTrue(Disruptions.skippedStops("Rijdt niet verder dan Delft").isEmpty())
    }

    @Test
    fun recognisesCancellationNotes() {
        assertTrue(Disruptions.isCancelled("Rijdt niet"))
        assertTrue(Disruptions.isCancelled("Let op, rijdt vandaag niet."))
        assertTrue(Disruptions.isCancelled("Valt uit"))
        assertTrue(Disruptions.isCancelled("Cancelled"))
        assertFalse(Disruptions.isCancelled("Rijdt niet verder dan Rotterdam Centraal"))
        assertFalse(Disruptions.isCancelled("Stopt niet in Delft"))
    }

    @Test
    fun matchesShortAndLongStationNames() {
        assertTrue(Disruptions.sameStation("Rotterdam C.", "Rotterdam Centraal"))
        assertTrue(Disruptions.sameStation("Den Haag HS", "Den Haag Hollands Spoor"))
        assertTrue(Disruptions.sameStation("Delft Campus", "delft campus"))
        assertFalse(Disruptions.sameStation("Delft", "Delft Campus"))
        assertFalse(Disruptions.sameStation("Rotterdam C.", "Rotterdam Blaak"))
        assertFalse(Disruptions.sameStation(null, "Delft"))
    }
}
