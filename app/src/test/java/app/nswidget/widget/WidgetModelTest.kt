package app.nswidget.widget

import app.nswidget.data.Departure
import app.nswidget.data.Favourite
import app.nswidget.data.Snapshot
import app.nswidget.data.TripOption
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
        // Up to three cities when there is room for them.
        assertEquals("Rotterdam, Delft, Den Haag", WidgetModels.viaText(cities, 500f))
        // At 4.6 dp a character: all three = 26 chars -> ~120 dp, "Rotterdam, Delft" = 16 -> ~74 dp,
        // "Rotterdam" = 9 -> ~41 dp.
        assertEquals("Rotterdam, Delft", WidgetModels.viaText(cities, 100f))
        assertEquals("Rotterdam", WidgetModels.viaText(cities, 60f))
        assertNull("not even one city fits", WidgetModels.viaText(cities, 40f))
        assertNull(WidgetModels.viaText(emptyList(), 500f))
    }

    @Test
    fun viaTextCanBeCappedAndAllowsForSmallerText() {
        val cities = listOf("Rotterdam", "Delft", "Den Haag")
        assertEquals("Rotterdam, Delft", WidgetModels.viaText(cities, 500f, maxCities = 2))
        // 26 characters: ~120 dp at 10sp, but only ~109 dp at 9sp - so 9sp fits all three in 115 dp.
        assertEquals("Rotterdam, Delft, Den Haag", WidgetModels.viaText(cities, 115f, charDp = 4.2f))
        assertEquals("Rotterdam, Delft", WidgetModels.viaText(cities, 115f))
    }

    private fun viaRow(vararg cities: String) = DepartureRow(
        time = "10:00", delayMinutes = 0, category = "IC", direction = "Amsterdam Centraal",
        track = "5", trackChanged = false, cancelled = false, via = cities.toList(),
    )

    @Test
    fun twoLineRowsAreUsedWhenTheyCostNoDepartures() {
        val rows = List(8) { viaRow("Rotterdam") }
        // 124 dp: four 26 dp rows fit, and so do four 28 dp rows.
        val layout = WidgetModels.rowLayout(rows, 124f)
        assertTrue(layout.dual)
        assertEquals(4, layout.maxRows)
    }

    @Test
    fun fallsBackToOneLineWhenTwoLinesWouldShowFewerDepartures() {
        val rows = List(8) { viaRow("Rotterdam") }
        // 110 dp: four 26 dp rows fit (104) but only three 28 dp rows (84; four need 112).
        val layout = WidgetModels.rowLayout(rows, 110f)
        assertFalse(layout.dual)
        assertEquals(4, layout.maxRows)
    }

    @Test
    fun largeFontsNeedTallerTwoLineRowsSoTheCompactLayoutWins() {
        val rows = List(8) { viaRow("Rotterdam") }
        // 124 dp fits four 28 dp two-line rows at the default font, but at a 1.3x font each needs
        // ~36 dp, so only three would fit - fewer than the four one-line rows - and one line wins.
        assertTrue(WidgetModels.rowLayout(rows, 124f, fontScale = 1f).dual)
        val large = WidgetModels.rowLayout(rows, 124f, fontScale = 1.3f)
        assertFalse(large.dual)
        assertEquals(4, large.maxRows)
        // Given enough height, two lines are fine even with a large font.
        assertTrue(WidgetModels.rowLayout(rows, 300f, fontScale = 1.3f).dual)
    }

    @Test
    fun twoLineRowGrowsWithTheFontButNeverShrinks() {
        assertEquals(28f, WidgetModels.dualRowDp(1f), 0.001f)
        assertEquals(28f, WidgetModels.dualRowDp(0.85f), 0.001f)
        assertEquals(36.4f, WidgetModels.dualRowDp(1.3f), 0.001f)
    }

    @Test
    fun oneLineRowsWhenNoVisibleTrainHasCities() {
        val layout = WidgetModels.rowLayout(List(8) { viaRow() }, 200f)
        assertFalse(layout.dual)
        assertEquals(7, layout.maxRows)
    }

    @Test
    fun twoLineRowsWhenThereAreFewerDeparturesThanSlots() {
        val layout = WidgetModels.rowLayout(listOf(viaRow("Delft"), viaRow()), 200f)
        assertTrue(layout.dual)
    }

    @Test
    fun rowCountIsAlwaysBetweenOneAndTheMaximum() {
        assertEquals(1, WidgetModels.rowLayout(List(8) { viaRow("Delft") }, 5f).maxRows)
        assertEquals(WidgetModels.MAX_ROWS, WidgetModels.rowLayout(List(20) { viaRow("Delft") }, 1000f).maxRows)
    }

    @Test
    fun smallerTextIsEstimatedNarrower() {
        assertEquals(4.6f, WidgetModels.viaCharDp(10), 0.001f)
        assertEquals(4.14f, WidgetModels.viaCharDp(WidgetModels.VIA_INLINE_SP), 0.001f)
        assertEquals(3.68f, WidgetModels.viaCharDp(WidgetModels.VIA_DUAL_SP), 0.001f)
        // The 9sp text fits a budget that the old 10sp text did not: 26 chars = ~108 dp vs ~120 dp.
        val cities = listOf("Rotterdam", "Delft", "Den Haag")
        val budget = 112f
        assertEquals("Rotterdam, Delft", WidgetModels.viaText(cities, budget, charDp = WidgetModels.viaCharDp(10)))
        assertEquals(
            "Rotterdam, Delft, Den Haag",
            WidgetModels.viaText(cities, budget, charDp = WidgetModels.viaCharDp(WidgetModels.VIA_INLINE_SP)),
        )
    }

    @Test
    fun viaTextAllowsForLargeFonts() {
        val cities = listOf("Rotterdam", "Delft")
        // 74 dp of text becomes ~96 dp with a 1.3x font, which no longer fits in 85 dp...
        assertEquals("Rotterdam, Delft", WidgetModels.viaText(cities, 85f))
        assertEquals("Rotterdam", WidgetModels.viaText(cities, 85f, fontScale = 1.3f))
        // ...and neither does the destination: its estimate grows with the font too.
        assertEquals(
            WidgetModels.estimateDirectionWidth("Den Haag Centraal") * 1.3f,
            WidgetModels.estimateDirectionWidth("Den Haag Centraal", fontScale = 1.3f),
            0.001f,
        )
    }

    private val work = Favourite("8400621", "Utrecht Centraal", "Work")
    private val home = Favourite("8400180", "Dordrecht", "Home")

    private fun train(inMinutes: Int, number: String?, stops: List<String> = emptyList(),
                      direction: String = "Amsterdam Centraal", cancelled: Boolean = false) =
        dep(inMinutes, cancelled = cancelled).copy(trainNumber = number, stops = stops, direction = direction)

    private fun trip(number: String, departIn: Int, arriveIn: Int, transfers: Int = 0, to: Favourite = work) =
        TripOption(to.uic, number, min(departIn), min(arriveIn), transfers)

    @Test
    fun fastestWayToAFavouriteIsTaggedEvenWithAChange() {
        val departures = listOf(
            train(5, "100", stops = listOf(work.uic)),                 // stops at Work, slower
            train(10, "200", direction = "Utrecht Centraal"),          // terminates at Work
            train(12, "300"),                                          // fastest, with one change
            train(14, "400", stops = listOf(work.uic), cancelled = true),
            train(20, "500"),                                          // unrelated
        )
        val trips = listOf(trip("100", 5, 40), trip("300", 12, 35, transfers = 1))
        val tags = WidgetModels.favouriteTags(departures, listOf(work), trips, nowMs)

        assertEquals(listOf(RowTag("Work", fastest = false)), tags[0])
        assertEquals(listOf(RowTag("Work", fastest = false)), tags[1])
        assertEquals(listOf(RowTag("Work", fastest = true, transfers = 1)), tags[2])
        assertTrue("cancelled trains get nothing", tags[3].isEmpty())
        assertTrue(tags[4].isEmpty())
    }

    @Test
    fun aJourneyThatCanNoLongerBeCaughtIsNotTheFastest() {
        val departures = listOf(train(6, "700", stops = listOf(work.uic)))
        // "600" left two minutes ago and would have arrived first; "700" is the best one left.
        val trips = listOf(trip("600", -2, 20), trip("700", 6, 45))
        val tags = WidgetModels.favouriteTags(departures, listOf(work), trips, nowMs)
        assertEquals(listOf(RowTag("Work", fastest = true)), tags[0])
    }

    @Test
    fun aDirectPlannedJourneyCountsAsStoppingThere() {
        // No stop list for this train, but the planner says it goes straight to Work.
        val departures = listOf(train(8, "800"), train(3, "900"))
        val trips = listOf(trip("800", 8, 50), trip("900", 3, 30))
        val tags = WidgetModels.favouriteTags(departures, listOf(work), trips, nowMs)
        assertEquals(listOf(RowTag("Work", fastest = false)), tags[0])
        assertEquals(listOf(RowTag("Work", fastest = true)), tags[1])
    }

    @Test
    fun aTrainCanServeSeveralFavouritesWithTheFastestFirst() {
        val departures = listOf(train(5, "100", stops = listOf(home.uic, work.uic)))
        val trips = listOf(trip("100", 5, 25, to = work))
        val tags = WidgetModels.favouriteTags(departures, listOf(home, work), trips, nowMs)
        assertEquals(listOf(RowTag("Work", fastest = true), RowTag("Home", fastest = false)), tags[0])
    }

    @Test
    fun tagsReachTheWidgetRows() {
        val snapshot = snapshot(listOf(train(5, "100", stops = listOf(work.uic))))
            .copy(trips = listOf(trip("100", 5, 40)))
        val model = WidgetModels.build(now, hasKey = true, snapshot = snapshot, favourites = listOf(work))
        assertEquals(listOf(RowTag("Work", fastest = true)), model.rows.single().tags)
        assertTrue(WidgetModels.build(now, hasKey = true, snapshot = snapshot).rows.single().tags.isEmpty())
    }

    private fun rowWith(vararg tags: RowTag) = DepartureRow(
        time = "10:00", delayMinutes = 0, category = "IC", direction = "Utrecht Centraal",
        track = "5", trackChanged = false, cancelled = false, via = emptyList(), tags = tags.toList(),
    )

    @Test
    fun highlightIsColourOnlyAndTheFastestWins() {
        assertEquals(Highlight.NONE, rowWith().highlight)
        assertEquals(Highlight.STOPS, rowWith(RowTag("Work", fastest = false)).highlight)
        assertEquals(Highlight.FASTEST, rowWith(RowTag("Work", fastest = true, transfers = 1)).highlight)
        // Fastest to one favourite and merely stopping at another: the stronger highlight wins.
        assertEquals(
            Highlight.FASTEST,
            rowWith(RowTag("Home", fastest = false), RowTag("Work", fastest = true)).highlight,
        )
    }

    @Test
    fun formatsDistances() {
        assertEquals("450 m", WidgetModels.formatDistance(450))
        assertEquals("1.2 km", WidgetModels.formatDistance(1234))
    }
}
