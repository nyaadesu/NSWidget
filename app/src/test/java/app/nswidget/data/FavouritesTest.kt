package app.nswidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class FavouritesTest {
    private val home = Favourite("8400180", "Dordrecht", "Home")
    private val work = Favourite("8400621", "Utrecht Centraal", "Work")

    @Test
    fun survivesBeingStored() {
        val stored = Favourites.encode(listOf(home, work))
        assertEquals(listOf(home, work), Favourites.decode(stored))
    }

    @Test
    fun brokenOrMissingDataMeansNoFavourites() {
        assertTrue(Favourites.decode(null).isEmpty())
        assertTrue(Favourites.decode("").isEmpty())
        assertTrue(Favourites.decode("not json").isEmpty())
    }

    @Test
    fun keyIgnoresOrderButNotTheStation() {
        assertEquals(Favourites.key("A", listOf(home, work)), Favourites.key("A", listOf(work, home)))
        assertTrue(Favourites.key("A", listOf(home)) != Favourites.key("B", listOf(home)))
        assertTrue(Favourites.key("A", listOf(home)) != Favourites.key("A", listOf(home, work)))
    }

    @Test
    fun longLabelsAreShortenedForTheWidget() {
        assertEquals("Work", Favourites.shortLabel("Work"))
        assertEquals("Amsterdam…", Favourites.shortLabel("Amsterdam Centraal"))
    }

    private fun ms(s: String) = OffsetDateTime.parse(s).toInstant().toEpochMilli()

    // Shaped like the NS reisinformatie-api v3 trips response (no "payload" wrapper).
    private val tripsJson = """
        {"source":"HARP","trips":[
          {"idx":0,"transfers":0,"status":"NORMAL","legs":[
            {"idx":"0","name":"IC 2150","cancelled":false,"product":{"number":"2150","categoryCode":"IC"},
             "origin":{"name":"Dordrecht","plannedDateTime":"2026-10-06T10:04:00+0200","actualDateTime":"2026-10-06T10:06:00+0200"},
             "destination":{"name":"Utrecht Centraal","plannedDateTime":"2026-10-06T10:45:00+0200","actualDateTime":"2026-10-06T10:47:00+0200"}}]},
          {"idx":1,"transfers":1,"status":"NORMAL","legs":[
            {"idx":"0","name":"SPR 5851","cancelled":false,"product":{"number":"5851"},
             "origin":{"name":"Dordrecht","plannedDateTime":"2026-10-06T10:11:00+0200"},
             "destination":{"name":"Rotterdam Centraal","plannedDateTime":"2026-10-06T10:28:00+0200"}},
            {"idx":"1","travelType":"WALK","cancelled":false,
             "origin":{"name":"Rotterdam Centraal"},"destination":{"name":"Rotterdam Centraal"}},
            {"idx":"2","name":"IC 2850","cancelled":false,"product":{"number":"2850"},
             "origin":{"name":"Rotterdam Centraal","plannedDateTime":"2026-10-06T10:33:00+0200"},
             "destination":{"name":"Utrecht Centraal","plannedDateTime":"2026-10-06T11:10:00+0200"}}]},
          {"idx":2,"transfers":0,"status":"CANCELLED","legs":[
            {"idx":"0","cancelled":true,"product":{"number":"2154"},
             "origin":{"plannedDateTime":"2026-10-06T10:34:00+0200"},
             "destination":{"plannedDateTime":"2026-10-06T11:15:00+0200"}}]},
          {"idx":3,"transfers":0,"status":"NORMAL","legs":[
            {"idx":"0","cancelled":true,"product":{"number":"2158"},
             "origin":{"plannedDateTime":"2026-10-06T11:04:00+0200"},
             "destination":{"plannedDateTime":"2026-10-06T11:45:00+0200"}}]}
        ]}
    """.trimIndent()

    @Test
    fun parsesJourneyPlannerOptions() {
        val trips = NsApi.parseTrips(tripsJson, favouriteUic = "8400621")
        assertEquals("cancelled journeys and journeys with a cancelled leg are dropped", 2, trips.size)

        val direct = trips[0]
        assertEquals("8400621", direct.favouriteUic)
        assertEquals("2150", direct.trainNumber)
        assertEquals(0, direct.transfers)
        assertEquals("real-time when known", ms("2026-10-06T10:06:00+02:00"), direct.departAt)
        assertEquals(ms("2026-10-06T10:47:00+02:00"), direct.arriveAt)

        val withChange = trips[1]
        assertEquals("the train you board here", "5851", withChange.trainNumber)
        assertEquals("the walk between platforms is not a change of train", 1, withChange.transfers)
        assertEquals("arrival of the last train", ms("2026-10-06T11:10:00+02:00"), withChange.arriveAt)
    }

    @Test
    fun acceptsAPayloadWrapperAndEmptyResponses() {
        val wrapped = """{"payload":{"trips":[{"transfers":0,"legs":[{"product":{"number":"1"},
            "origin":{"plannedDateTime":"2026-10-06T10:00:00+0200"},
            "destination":{"plannedDateTime":"2026-10-06T10:30:00+0200"}}]}]}}"""
        assertEquals(1, NsApi.parseTrips(wrapped, "x").size)
        assertTrue(NsApi.parseTrips("""{"trips":[]}""", "x").isEmpty())
        assertTrue(NsApi.parseTrips("{}", "x").isEmpty())
    }

    @Test
    fun snapshotKeepsPlannedJourneys() {
        val snapshot = Snapshot(
            stationUic = "8400180", stationName = "Dordrecht", distanceMeters = null,
            fetchedAt = 1_700_000_000_000, departures = emptyList(), error = null,
            trips = NsApi.parseTrips(tripsJson, "8400621"),
            tripsFetchedAt = 1_700_000_000_000, tripsKey = "8400180|8400621",
        )
        assertEquals(snapshot, Snapshot.fromJson(snapshot.toJson()))
    }
}
