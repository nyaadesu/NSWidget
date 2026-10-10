package app.nswidget.refresh

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.nswidget.data.ApiUsage
import app.nswidget.data.Favourites
import app.nswidget.data.NsApi
import app.nswidget.data.Settings
import app.nswidget.data.Snapshot
import app.nswidget.data.SnapshotStore
import app.nswidget.data.StationRepository
import app.nswidget.data.friendlyMessage
import app.nswidget.data.isConnectionError
import app.nswidget.data.nearestStation
import app.nswidget.location.LocationHelper
import app.nswidget.widget.NsWidget
import app.nswidget.widget.NsWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Two cooperating pieces keep the widget fresh:
 *  - a self-rescheduling alarm ([RefreshScheduler] -> [TickReceiver]) repaints the widget every
 *    minute without touching the network, which keeps countdowns and the departure list current, and
 *  - a WorkManager job ([RefreshWorker]) fetches departures (with delays and platform changes)
 *    when they are due, which [FetchPolicy] decides: every 2 minutes while a train is about to
 *    leave, less often otherwise. Ticks only run while the screen is on, so neither do fetches.
 */
object Refresher {
    /** A saved location fix older than this is renewed on the next fetch (background location only). */
    const val LOCATION_STALE_MS = 10 * 60_000L

    internal const val KEY_FRESH_LOCATION = "fresh_location"

    fun hasWidgets(ctx: Context): Boolean =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, NsWidgetReceiver::class.java))
            .isNotEmpty()

    /** Kick everything off: fetch now and (re)arm the tick alarm. */
    fun start(ctx: Context) {
        enqueueFetch(ctx, replace = false)
        RefreshScheduler.scheduleNext(ctx)
    }

    /**
     * Called when the user changed something that affects what the widget shows, or pressed
     * refresh. [freshLocation] asks for a new location fix first instead of Android's last known one.
     */
    fun requestFetch(ctx: Context, freshLocation: Boolean = false) =
        enqueueFetch(ctx, replace = true, freshLocation = freshLocation)

    fun enqueueFetch(ctx: Context, replace: Boolean, freshLocation: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_FRESH_LOCATION to freshLocation))
        // Expedited: run right away even when Android has put the (rarely opened) app in a low
        // standby bucket, which otherwise delays the job and can cut it off from the network just
        // after the screen comes on. Before Android 12 this would need a foreground notification.
        if (Build.VERSION.SDK_INT >= 31) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        val request = builder.build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "fetch",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Alarm tick: repaint (cheap, no network), fetch if due, schedule the next tick. */
    suspend fun tick(ctx: Context) {
        try {
            NsWidget().updateAll(ctx)
            val snapshot = SnapshotStore(ctx).load()
            val nowMs = System.currentTimeMillis()
            val due = snapshot == null || nowMs - snapshot.attemptedAt >= FetchPolicy.intervalMs(
                departures = snapshot.departures,
                nowMs = nowMs,
                callsToday = ApiUsage(ctx).callsToday(),
                failing = snapshot.error != null,
                connectionError = snapshot.connectionError,
            )
            if (due && Settings(ctx).apiKey.isNotBlank()) enqueueFetch(ctx, replace = false)
        } finally {
            if (hasWidgets(ctx)) RefreshScheduler.scheduleNext(ctx)
        }
    }

    /** Picks the station, downloads its departures, stores them and repaints the widget. */
    suspend fun fetch(ctx: Context, freshLocation: Boolean = false) {
        val settings = Settings(ctx)
        if (settings.apiKey.isNotBlank()) {
            val store = SnapshotStore(ctx)
            val snapshot = buildSnapshot(ctx, settings, store.load(), wantsFreshFix(ctx, settings, freshLocation))
            currentCoroutineContext().ensureActive() // a cancelled fetch must not overwrite newer data
            store.save(snapshot)
        }
        NsWidget().updateAll(ctx)
    }

    private data class Target(
        val uic: String,
        val name: String,
        val distance: Int?,
        val lat: Double?,
        val lng: Double?,
    )

    /**
     * A new location fix is worth asking for when the user pressed refresh, or when the saved one
     * is old and the app is allowed to look while closed. Pointless with a fixed station.
     */
    private fun wantsFreshFix(ctx: Context, settings: Settings, requested: Boolean): Boolean {
        if (!settings.useNearest) return false
        if (requested) return true
        val age = System.currentTimeMillis() - (settings.location?.atMillis ?: 0L)
        return age > LOCATION_STALE_MS && LocationHelper.hasBackground(ctx)
    }

    private suspend fun buildSnapshot(
        ctx: Context,
        settings: Settings,
        previous: Snapshot?,
        freshLocation: Boolean,
    ): Snapshot {
        val target: Target? = try {
            LocationHelper.refreshSaved(ctx, preferFresh = freshLocation)
            withContext(Dispatchers.IO) { resolveStation(ctx, settings) }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return failed(previous, null, e)
        }
        if (target == null) {
            return Snapshot("", "", null, now(), emptyList(), "Allow location or pick a station in the app")
        }
        val fresh = try {
            val departures = retryingConnection {
                ApiUsage(ctx).record()
                withContext(Dispatchers.IO) { NsApi.fetchDepartures(settings.apiKey, target.uic, target.name) }
            }
            Snapshot(target.uic, target.name, target.distance, now(), departures, null, target.lat, target.lng)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return failed(previous, target, e)
        }
        return withTrips(ctx, settings, fresh, previous)
    }

    /**
     * Adds journey-planner results towards the favourite stations (one call per favourite), which
     * tell the widget which trains reach them and which is the fastest way there. They are
     * re-planned at most every few minutes; otherwise, or if planning fails, the previous results
     * for the same station and favourites are kept.
     */
    private suspend fun withTrips(ctx: Context, settings: Settings, fresh: Snapshot, previous: Snapshot?): Snapshot {
        val favourites = settings.favourites.filter { it.uic != fresh.stationUic }
        if (favourites.isEmpty()) return fresh
        val key = Favourites.key(fresh.stationUic, favourites)
        val earlier = previous?.takeIf { it.tripsKey == key }
        val usage = ApiUsage(ctx)
        val due = earlier == null || now() - earlier.tripsFetchedAt >= FetchPolicy.tripsIntervalMs(usage.callsToday())
        if (!due) {
            return fresh.copy(trips = earlier!!.trips, tripsFetchedAt = earlier.tripsFetchedAt, tripsKey = key)
        }
        return try {
            val trips = favourites.flatMap { favourite ->
                usage.record()
                withContext(Dispatchers.IO) { NsApi.fetchTrips(settings.apiKey, fresh.stationUic, favourite.uic) }
            }
            fresh.copy(trips = trips, tripsFetchedAt = now(), tripsKey = key)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            // Keep what we had, and wait a full interval before trying again.
            fresh.copy(trips = earlier?.trips.orEmpty(), tripsFetchedAt = now(), tripsKey = key)
        }
    }

    /**
     * Runs [block], trying again a couple of times if NS can't be reached. Right after the screen
     * comes on, or while the phone hands over from Wi-Fi to mobile data, the network often
     * reports "connected" a few seconds before requests actually get through.
     */
    private suspend fun <T> retryingConnection(block: suspend () -> T): T {
        for (pause in CONNECTION_RETRY_PAUSES_MS) {
            try {
                return block()
            } catch (e: Exception) {
                if (!e.isConnectionError()) throw e
                currentCoroutineContext().ensureActive()
            }
            delay(pause)
        }
        return block()
    }

    private val CONNECTION_RETRY_PAUSES_MS = listOf(2_000L, 5_000L)

    /** Keep showing the last good departures (if they're for the same station), but flag the failure. */
    private fun failed(previous: Snapshot?, target: Target?, e: Exception): Snapshot {
        val keep = previous?.takeIf { target == null || it.stationUic == target.uic }
        return Snapshot(
            stationUic = target?.uic ?: previous?.stationUic.orEmpty(),
            stationName = target?.name ?: previous?.stationName.orEmpty(),
            distanceMeters = target?.distance ?: keep?.distanceMeters,
            fetchedAt = keep?.fetchedAt ?: now(),
            departures = keep?.departures.orEmpty(),
            error = e.friendlyMessage(),
            lat = target?.lat ?: keep?.lat,
            lng = target?.lng ?: keep?.lng,
            attemptedAt = now(), // so the next try waits (FetchPolicy backs off after failures)
            trips = keep?.trips.orEmpty(),
            tripsFetchedAt = keep?.tripsFetchedAt ?: 0L,
            tripsKey = keep?.tripsKey.orEmpty(),
            connectionError = e.isConnectionError(),
        )
    }

    private fun resolveStation(ctx: Context, settings: Settings): Target? {
        val location = settings.location
        if (settings.useNearest && location != null) {
            val stations = StationRepository(ctx).load(settings.apiKey)
            nearestStation(stations, location.lat, location.lng)?.let { (station, meters) ->
                return Target(station.uic, station.name, meters, station.lat, station.lng)
            }
        }
        val uic = settings.fixedStationUic ?: return null
        val latLng = settings.fixedStationLatLng
        return Target(uic, settings.fixedStationName ?: uic, null, latLng?.first, latLng?.second)
    }

    private fun now() = System.currentTimeMillis()
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val freshLocation = inputData.getBoolean(Refresher.KEY_FRESH_LOCATION, false)
        Refresher.fetch(applicationContext, freshLocation)
        return Result.success()
    }
}
