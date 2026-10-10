package app.nswidget.widget

import app.nswidget.data.Favourite
import app.nswidget.data.RouteDisruption
import app.nswidget.data.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisruptionAlertTest {
    private val home = Favourite("8400180", "Dordrecht", "🏠")
    private val work = Favourite("8400170", "Delft", "💼")

    private val storing = RouteDisruption(work.uic, "6068184", "DISRUPTION", "Tussen Delft Campus en Rotterdam rijden er geen treinen.")
    private val works = RouteDisruption(home.uic, "7006841", "MAINTENANCE", "Door werkzaamheden rijden er bussen.")

    @Test
    fun noDisruptionsNoSquare() {
        assertNull(WidgetModels.disruptionAlert(emptyList(), listOf(home, work)))
    }

    @Test
    fun linksToTheMostSeriousDisruptionAndNamesEveryAffectedFavourite() {
        val alert = WidgetModels.disruptionAlert(listOf(works, storing), listOf(home, work))!!
        assertEquals("https://www.ns.nl/reisinformatie/actuele-situatie-op-het-spoor/disruption?id=6068184", alert.url)
        assertEquals("the first affected favourite goes in the triangle", "🏠", alert.symbol)
        assertTrue(alert.description.contains("Dordrecht, Delft"))
        assertTrue(alert.description.contains("geen treinen"))
    }

    @Test
    fun wordLabelsDontFitTheTriangle() {
        val words = Favourite(work.uic, "Delft", "Work")
        assertEquals("!", WidgetModels.disruptionAlert(listOf(storing), listOf(words))!!.symbol)
        assertTrue(WidgetModels.isSingleSymbol("🇳🇱"))
        assertTrue(WidgetModels.isSingleSymbol("👩‍💻"))
        assertTrue(WidgetModels.isSingleSymbol("👍🏽"))
        assertTrue(WidgetModels.isSingleSymbol("W"))
        assertFalse(WidgetModels.isSingleSymbol("🏠💼"))
        assertFalse(WidgetModels.isSingleSymbol(""))
    }

    @Test
    fun ignoresDisruptionsForStationsThatAreNoLongerFavourites() {
        assertNull(WidgetModels.disruptionAlert(listOf(storing), listOf(home)))
    }

    @Test
    fun linksPerType() {
        val base = "https://www.ns.nl/reisinformatie/actuele-situatie-op-het-spoor/"
        assertEquals("${base}maintenance?id=7006841", NsLinks.disruptionUrl("7006841", "MAINTENANCE"))
        assertEquals("${base}calamity?id=de32a4a9-cb20", NsLinks.disruptionUrl("de32a4a9-cb20", "CALAMITY"))
        assertEquals(base, NsLinks.disruptionUrl("1", "SOMETHING_NEW"))
    }

    @Test
    fun disruptionsSurviveStorage() {
        val snapshot = Snapshot("8400180", "Dordrecht", null, 1L, emptyList(), null, disruptions = listOf(storing, works))
        assertEquals(snapshot.disruptions, Snapshot.fromJson(snapshot.toJson())!!.disruptions)
    }
}
