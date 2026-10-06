package app.nswidget.widget

import app.nswidget.data.Departure
import app.nswidget.data.Snapshot
import app.nswidget.discount.DiscountPhase
import app.nswidget.discount.PeakRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

class WidgetModelTest {
    private val now: ZonedDateTime = LocalDateTime.parse("2026-10-06T08:20:00").atZone(PeakRules.ZONE)
    private val nowMs = now.toInstant().toEpochMilli()
    private fun min(m: Int) = nowMs + m * 60_000L

    private fun dep(
        inMinutes: Int,
        delay: Int = 0,
        track: String? = "5",
        actualTrack: String? = null,
        cancelled: Boolean = false,
        via: List<String> = emptyList(),
    ) = Departure(
        plannedAt = min(inMinutes),
        actualAt = min(inMinutes + delay),
        direction = "Amsterdam Centraal",
        category = "IC",
        plannedTrack = track,
        actualTrack = actualTrack,
        cancelled = cancelled,
        via = via,
    )

    private fun snapshot(departures: List<Departure>, error: String? = null, uic: String = "8400621") = Snapshot(
        stationUic = uic,
        stationName = "Utrecht Centraal",
        distanceMeters = 450,
        fetchedAt = nowMs - 60_000,
        departures = departures,
        error = error,
    )

    @Test
    fun combinesDiscountBannerAndDepartures() {
        val model = WidgetModels.build(now, hasKey = true, snapshot = snapshot(listOf(dep(4, delay = 2))))
        assertEquals(DiscountPhase.PEAK_ENDING, model.phase)
        assertEquals("Wait 40 min for discount", model.label.title)
        assertEquals("Utrecht Centraal", model.stationName)
        assertEquals("450 m · 08:19", model.statusLine)
        assertNull(model.message)
        assertFalse(model.offline)

        val row = model.rows.single()
        assertEquals("08:26", row.time) // planned 08:24 + 2 min delay
        assertEquals(2, row.delayMinutes)
        assertEquals("5", row.track)
    }

    @Test
    fun hidesTrainsThatAlreadyLeft() {
        val model = WidgetModels.build(
            now,
            hasKey = true,
            snapshot = snapshot(listOf(dep(-5), dep(-1, delay = 3), dep(6))),
        )
        // The first left five minutes ago; the second was due at 08:19 but is 3 min late, so it's
        // still at the platform and shows its estimated departure of 08:22.
        assertEquals(2, model.rows.size)
        assertEquals("08:22", model.rows[0].time)
        assertEquals("08:26", model.rows[1].time)
    }

    @Test
    fun flagsPlatformChangeAndCancellation() {
        val model = WidgetModels.build(
            now,
            hasKey = true,
            snapshot = snapshot(listOf(dep(5, track = "5", actualTrack = "7"), dep(9, cancelled = true))),
        )
        assertTrue(model.rows[0].trackChanged)
        assertEquals("7", model.rows[0].track)
        assertTrue(model.rows[1].cancelled)
    }

    @Test
    fun keepsOldDeparturesButFlagsOfflineOnFailure() {
        val model = WidgetModels.build(
            now,
            hasKey = true,
            snapshot = snapshot(listOf(dep(5)), error = "No connection to NS"),
        )
        assertTrue(model.offline)
        assertEquals(1, model.rows.size)
        assertNull(model.message)
        assertEquals("450 m · offline 08:19", model.statusLine)
    }

    @Test
    fun explainsWhyThereAreNoDepartures() {
        assertEquals(
            "Add your NS API key in the app to see departures",
            WidgetModels.build(now, hasKey = false, snapshot = null).message,
        )
        assertEquals(
            "Loading departures…",
            WidgetModels.build(now, hasKey = true, snapshot = null).message,
        )
        assertEquals(
            "Allow location or pick a station in the app",
            WidgetModels.build(now, hasKey = true, snapshot = snapshot(emptyList(), uic = "")).message,
        )
        assertEquals(
            "No upcoming departures",
            WidgetModels.build(now, hasKey = true, snapshot = snapshot(emptyList())).message,
        )
        assertEquals(
            "No connection to NS",
            WidgetModels.build(now, hasKey = true, snapshot = snapshot(emptyList(), error = "No connection to NS")).message,
        )
    }

    @Test
    fun discountBannerWorksWithoutAnyData() {
        val model = WidgetModels.build(now, hasKey = false, snapshot = null)
        assertEquals("Wait 40 min for discount", model.label.title)
        assertNull(model.stationName)
        assertNull(model.statusLine)
    }

    @Test
    fun capsTheNumberOfRows() {
        val many = (1..20).map { dep(it) }
        val model = WidgetModels.build(now, hasKey = true, snapshot = snapshot(many))
        assertEquals(WidgetModels.MAX_ROWS, model.rows.size)
    }

    @Test
    fun mapButtonPointsAtTheStationWhenCoordinatesAreKnown() {
        val withCoords = snapshot(listOf(dep(5))).copy(lat = 51.8, lng = 4.67)
        assertEquals(
            "geo:51.8,4.67?q=51.8,4.67(Utrecht%20Centraal%20station)",
            WidgetModels.build(now, hasKey = true, snapshot = withCoords).mapUri,
        )
    }

    @Test
    fun mapButtonFallsBackToANameSearchAndHidesWithoutAStation() {
        assertEquals(
            "geo:0,0?q=Utrecht%20Centraal%20station",
            WidgetModels.build(now, hasKey = true, snapshot = snapshot(listOf(dep(5)))).mapUri,
        )
        assertNull(WidgetModels.build(now, hasKey = true, snapshot = null).mapUri)
        assertNull(WidgetModels.build(now, hasKey = true, snapshot = snapshot(emptyList(), uic = "").copy(stationName = "")).mapUri)
    }

    @Test
    fun mapLinkEncodesSpecialCharacters() {
        assertEquals(
            "geo:0,0?q=%27s-Hertogenbosch%20station",
            MapsLinks.stationUri("'s-Hertogenbosch", null, null),
        )
        assertEquals(
            "geo:0,0?q=Amsterdam%20Bijlmer%20ArenA%20station",
            MapsLinks.stationUri("Amsterdam Bijlmer ArenA", null, null),
        )
    }

    @Test
    fun routeCitiesPassThroughUnlessSwitchedOffOrCancelled() {
        val departures = listOf(
            dep(5, via = listOf("Rotterdam", "Delft")),
            dep(9, via = listOf("Utrecht"), cancelled = true),
        )
        val on = WidgetModels.build(now, hasKey = true, snapshot = snapshot(departures))
        assertEquals(listOf("Rotterdam", "Delft"), on.rows[0].via)
        assertEquals("a cancelled train isn't going anywhere", emptyList<String>(), on.rows[1].via)

        val off = WidgetModels.build(now, hasKey = true, snapshot = snapshot(departures), showVia = false)
        assertEquals(emptyList<String>(), off.rows[0].via)
    }

    @Test
    fun viaTextUsesAsManyCitiesAsFit() {
        val cities = listOf("Rotterdam", "Delft", "Den Haag")
        // Never more than two cities, however much room there is.
        assertEquals("via Rotterdam, Delft", WidgetModels.viaText(cities, 500f))
        // "via Rotterdam, Delft" is 20 chars -> ~116 dp; "via Rotterdam" is 13 chars -> ~75 dp.
        assertEquals("via Rotterdam, Delft", WidgetModels.viaText(cities, 120f))
        assertEquals("via Rotterdam", WidgetModels.viaText(cities, 80f))
        assertNull("not even one city fits", WidgetModels.viaText(cities, 40f))
        assertNull(WidgetModels.viaText(emptyList(), 500f))
    }

    @Test
    fun viaTextAllowsForLargeFonts() {
        val cities = listOf("Rotterdam", "Delft")
        // 116 dp of text becomes ~151 dp with a 1.3x font, which no longer fits in 120 dp...
        assertEquals("via Rotterdam", WidgetModels.viaText(cities, 120f, fontScale = 1.3f))
        // ...and neither does the destination: its estimate grows with the font too.
        assertEquals(
            WidgetModels.estimateDirectionWidth("Den Haag Centraal") * 1.3f,
            WidgetModels.estimateDirectionWidth("Den Haag Centraal", fontScale = 1.3f),
            0.001f,
        )
    }

    @Test
    fun formatsDistances() {
        assertEquals("450 m", WidgetModels.formatDistance(450))
        assertEquals("1.2 km", WidgetModels.formatDistance(1234))
    }
}
