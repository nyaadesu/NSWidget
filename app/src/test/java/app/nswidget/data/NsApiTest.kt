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
    fun keepsTrainNumberAndStopCodesForFavouriteMatching() {
        val (ic, spr) = NsApi.parseDepartures(departuresJson)
        assertEquals("2150", ic.trainNumber)
        assertEquals(listOf("8400530", "8400561", "8400058"), ic.stops)
        assertNull("no number in the product", spr.trainNumber)
        assertTrue(spr.stops.isEmpty())
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

    // During a Rotterdam - Delft disruption, seen from Dordrecht (messages as NS sent them on 2026-10-10).
    private val disruptedJson = """
        {"payload":{"departures":[
          {"direction":"Den Haag Centraal","plannedDateTime":"2026-10-10T13:00:00+0200",
           "product":{"number":"2250","categoryCode":"IC"},"cancelled":false,
           "messages":[{"message":"Rijdt niet verder dan Rotterdam C. door een sein- en wisselstoring","style":"WARNING"}],
           "routeStations":[{"uicCode":"8400530","mediumName":"Rotterdam C."},{"uicCode":"8400171","mediumName":"Delft Campus"},{"uicCode":"8400280","mediumName":"Den Haag C."}]},
          {"direction":"Den Haag Centraal","plannedDateTime":"2026-10-10T13:05:00+0200",
           "product":{"number":"5050","categoryCode":"SPR"},"cancelled":false,
           "messages":[{"message":"Rijdt niet","style":"WARNING"}]},
          {"direction":"Amsterdam Centraal","plannedDateTime":"2026-10-10T13:10:00+0200",
           "product":{"number":"2150","categoryCode":"IC"},"cancelled":false,
           "messages":[{"message":"Stopt niet in Delft Campus","style":"INFO"}],
           "routeStations":[{"uicCode":"8400530","mediumName":"Rotterdam C."},{"uicCode":"8400171","mediumName":"Delft Campus"},{"uicCode":"8400058","mediumName":"Amsterdam C."}]}
        ]}}
    """.trimIndent()

    @Test
    fun shortenedTrainEndsWhereNsSaysAndLosesTheStopsBeyond() {
        val ic = NsApi.parseDepartures(disruptedJson, originName = "Dordrecht")[0]
        assertTrue(ic.shortened)
        assertFalse(ic.cancelled)
        assertEquals("Rotterdam C.", ic.direction)
        assertEquals("Den Haag Centraal", ic.plannedDirection)
        assertEquals(listOf("8400530"), ic.stops)
        assertEquals("Rotterdam is now the destination, not a via city", emptyList<String>(), ic.via)
    }

    @Test
    fun cancellationNoteCancelsTheTrain() {
        assertTrue(NsApi.parseDepartures(disruptedJson)[1].cancelled)
    }

    @Test
    fun skippedStopsAreDroppedFromTheRoute() {
        val ic = NsApi.parseDepartures(disruptedJson)[2]
        assertEquals(listOf("Delft Campus"), ic.skippedStops)
        assertEquals(listOf("8400530", "8400058"), ic.stops)
        assertFalse(ic.shortened)
    }

    @Test
    fun disruptionDetailsSurviveStorage() {
        NsApi.parseDepartures(disruptedJson).forEach {
            assertEquals(it, Departure.fromJson(it.toJson()))
        }
    }

    // Journeys Dordrecht -> Delft on 2026-10-10, cut down to the parts that matter.
    private val disruptedTripsJson = """
        {"trips":[
          {"status":"CANCELLED",
           "primaryMessage":{"title":"Dit reisadvies vervalt","type":"TRIP_CANCELLED",
             "message":{"id":"6068184","externalId":"prio-6068184","type":"DISRUPTION","phase":"PHASE_3",
               "head":"Door een sein- en wisselstoring: tussen Delft Campus en Rotterdam Centraal rijden er geen treinen.",
               "text":"Door een sein- en wisselstoring: tussen Delft Campus en Rotterdam Centraal rijden er geen treinen."}},
           "legs":[{"name":"IC 3551","cancelled":true,"product":{"number":"3551"},
             "messages":[{"id":"6068184","type":"DISRUPTION","head":"Door een sein- en wisselstoring: tussen Delft Campus en Rotterdam Centraal rijden er geen treinen."}]}]},
          {"status":"NORMAL",
           "primaryMessage":{"title":"Kortere trein, extra druk","type":"SHORTENED_TRAIN"},
           "legs":[{"name":"IC 2345","product":{"number":"2345"},
             "messages":[{"type":"SHORTENED","text":"Kortere trein, extra druk"}]},
                   {"name":"IC 642","product":{"number":"642"},
             "messages":[{"id":"7006841","type":"MAINTENANCE","text":"Door werkzaamheden rijden er bussen."}]}]}
        ]}
    """.trimIndent()

    @Test
    fun findsEachDisruptionOnTheWayToAFavouriteOnce() {
        val found = NsApi.parseTripDisruptions(disruptedTripsJson, "8400170")
        assertEquals(listOf("6068184", "7006841"), found.map { it.id })
        assertEquals(listOf("DISRUPTION", "MAINTENANCE"), found.map { it.type })
        assertTrue(found.all { it.favouriteUic == "8400170" })
        assertTrue(found[0].head.contains("geen treinen"))
        assertEquals("falls back to the text when there's no head", "Door werkzaamheden rijden er bussen.", found[1].head)
        assertTrue(NsApi.parseTripDisruptions("""{"trips":[{"status":"NORMAL","legs":[]}]}""", "x").isEmpty())
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
