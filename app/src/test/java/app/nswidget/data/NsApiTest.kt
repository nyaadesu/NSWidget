package app.nswidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class NsApiTest {
    // Shaped like the NS reisinformatie-api v2 departures response.
    private val departuresJson = """
        {"payload":{"source":"PPV","departures":[
          {"direction":"Amsterdam Centraal","name":"NS 2150",
           "plannedDateTime":"2026-10-06T10:04:00+0200","plannedTimeZoneOffset":120,
           "actualDateTime":"2026-10-06T10:07:00+0200","actualTimeZoneOffset":120,
           "plannedTrack":"5","actualTrack":"6",
           "product":{"number":"2150","categoryCode":"IC","shortCategoryName":"NS Intercity","longCategoryName":"Intercity","operatorCode":"NS","operatorName":"NS","type":"TRAIN"},
           "trainCategory":"IC","cancelled":false,"departureStatus":"INCOMING",
           "routeStations":[{"uicCode":"8400530","mediumName":"Rotterdam C."},{"uicCode":"8400561","mediumName":"Schiphol Airport"},{"uicCode":"8400058","mediumName":"Amsterdam C."}]},
          {"direction":"Woerden","name":"NS 5851",
           "plannedDateTime":"2026-10-06T10:11:00+0200",
           "plannedTrack":"12a","actualTrack":null,
           "product":{"categoryCode":"SPR"},
           "cancelled":true},
          {"direction":"No time given","plannedTrack":"1"}
        ]}}
    """.trimIndent()

    private val stationsJson = """
        {"payload":[
          {"UICCode":"8400621","code":"UT","namen":{"lang":"Utrecht Centraal"},"land":"NL","lat":52.0894,"lng":5.1103,"heeftVertrektijden":true},
          {"UICCode":"8400058","code":"ASD","namen":{"lang":"Amsterdam Centraal"},"land":"NL","lat":52.3789,"lng":4.90028},
          {"UICCode":"8814001","code":"FBMZ","namen":{"lang":"Brussel-Zuid"},"land":"B","lat":50.8353,"lng":4.3363,"heeftVertrektijden":true},
          {"UICCode":"8400999","code":"XX","namen":{"lang":"Closed Halt"},"land":"NL","lat":52.0,"lng":5.0,"heeftVertrektijden":false},
          {"UICCode":"8400998","code":"YY","namen":{"lang":"No Coordinates"},"land":"NL"}
        ]}
    """.trimIndent()

    @Test
    fun parsesDepartures() {
        val deps = NsApi.parseDepartures(departuresJson)
        assertEquals("the entry without a planned time is skipped", 2, deps.size)

        val ic = deps[0]
        assertEquals("Amsterdam Centraal", ic.direction)
        assertEquals("IC", ic.category)
        assertEquals("5", ic.plannedTrack)
        assertEquals("6", ic.actualTrack)
        assertEquals("6", ic.track)
        assertTrue(ic.trackChanged)
        assertEquals(3, ic.delayMinutes)
        assertFalse(ic.cancelled)
        assertEquals(
            OffsetDateTime.parse("2026-10-06T10:04:00+02:00").toInstant().toEpochMilli(),
            ic.plannedAt,
        )
    }

    @Test
    fun picksMajorCitiesFromTheRouteButNotTheDestinationOrOrigin() {
        val ic = NsApi.parseDepartures(departuresJson, originName = "Dordrecht")[0]
        // Amsterdam is where the train is going, so it isn't "via".
        assertEquals(listOf("Rotterdam", "Schiphol"), ic.via)

        // Standing in Rotterdam instead, that city drops out too.
        assertEquals(listOf("Schiphol"), NsApi.parseDepartures(departuresJson, originName = "Rotterdam Centraal")[0].via)

        // A departure with no route information simply has no cities.
        assertEquals(emptyList<String>(), NsApi.parseDepartures(departuresJson)[1].via)
    }

    @Test
    fun parsesCancelledDepartureWithNullFields() {
        val spr = NsApi.parseDepartures(departuresJson)[1]
        assertTrue(spr.cancelled)
        assertEquals("SPR", spr.category)
        assertEquals("12a", spr.track)
        assertNull(spr.actualTrack)
        assertFalse(spr.trackChanged)
        assertEquals("no realtime estimate means no delay", 0, spr.delayMinutes)
    }

    @Test
    fun emptyOrUnexpectedPayloadGivesNoDepartures() {
        assertTrue(NsApi.parseDepartures("""{"payload":{"departures":[]}}""").isEmpty())
        assertTrue(NsApi.parseDepartures("""{"payload":{}}""").isEmpty())
        assertTrue(NsApi.parseDepartures("""{}""").isEmpty())
    }

    @Test
    fun parsesBothTimestampStyles() {
        val expected = OffsetDateTime.parse("2026-10-06T10:04:00+02:00").toInstant().toEpochMilli()
        assertEquals(expected, NsApi.parseTime("2026-10-06T10:04:00+0200"))
        assertEquals(expected, NsApi.parseTime("2026-10-06T10:04:00+02:00"))
        assertNull(NsApi.parseTime(""))
        assertNull(NsApi.parseTime(null))
        assertNull(NsApi.parseTime("not a date"))
    }

    @Test
    fun parsesStationsAndDropsUnusableOnes() {
        val stations = NsApi.parseStations(stationsJson)
        assertEquals(listOf("Utrecht Centraal", "Amsterdam Centraal"), stations.map { it.name })
        assertEquals("8400621", stations[0].uic)
        assertEquals("UT", stations[0].code)
        assertEquals(52.0894, stations[0].lat, 1e-9)
    }

    @Test
    fun snapshotSurvivesAJsonRoundTrip() {
        val snapshot = Snapshot(
            stationUic = "8400621",
            stationName = "Utrecht Centraal",
            distanceMeters = 450,
            fetchedAt = 1_700_000_000_000,
            departures = NsApi.parseDepartures(departuresJson),
            error = "No connection to NS",
            lat = 52.0894,
            lng = 5.1103,
        )
        val restored = Snapshot.fromJson(snapshot.toJson())
        assertNotNull(restored)
        assertEquals(snapshot, restored)
        assertNull(Snapshot.fromJson("garbage"))
    }

    @Test
    fun nearestStationPicksTheClosest() {
        val stations = NsApi.parseStations(stationsJson)
        // Standing a few hundred metres from Utrecht Centraal.
        val (station, meters) = nearestStation(stations, 52.0900, 5.1150)!!
        assertEquals("Utrecht Centraal", station.name)
        assertTrue("expected a few hundred metres, got $meters", meters in 200..600)
    }

    @Test
    fun distanceBetweenUtrechtAndAmsterdam() {
        val d = distanceMeters(52.0894, 5.1103, 52.3789, 4.90028)
        assertEquals(35_200.0, d, 1_000.0) // ~35 km as the crow flies
    }
}
