package app.nswidget.refresh

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.nswidget.data.NsApi
import app.nswidget.data.Settings
import app.nswidget.data.Snapshot
import app.nswidget.data.SnapshotStore
import app.nswidget.data.StationRepository
import app.nswidget.data.friendlyMessage
import app.nswidget.data.nearestStation
import app.nswidget.location.LocationHelper
import app.nswidget.widget.NsWidget
import app.nswidget.widget.NsWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Two cooperating pieces keep the widget fresh:
 *  - a self-rescheduling alarm ([RefreshScheduler] -> [TickReceiver]) repaints the widget, which is
 *    what keeps the discount countdown right, and
 *  - a WorkManager job ([RefreshWorker]) fetches departures when the data is getting stale.
 */
object Refresher {
    /** Departures older than this are re-fetched on the next tick. */
    const val FETCH_STALE_MS = 9 * 60_000L

    fun hasWidgets(ctx: Context): Boolean =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, NsWidgetReceiver::class.java))
            .isNotEmpty()

    /** Kick everything off: fetch now and (re)arm the tick alarm. */
    fun start(ctx: Context) {
        enqueueFetch(ctx, replace = false)
        RefreshScheduler.scheduleNext(ctx)
    }

    /** Called when the user changed something that affects what the widget shows. */
    fun requestFetch(ctx: Context) = enqueueFetch(ctx, replace = true)

    fun enqueueFetch(ctx: Context, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "fetch",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Alarm tick: repaint (cheap, no network), fetch if stale, schedule the next tick. */
    suspend fun tick(ctx: Context) {
        try {
            NsWidget().updateAll(ctx)
            val snapshot = SnapshotStore(ctx).load()
            val stale = snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > FETCH_STALE_MS
            if (stale && Settings(ctx).apiKey.isNotBlank()) enqueueFetch(ctx, replace = false)
        } finally {
            if (hasWidgets(ctx)) RefreshScheduler.scheduleNext(ctx)
        }
    }

    /** Picks the station, downloads its departures, stores them and repaints the widget. */
    suspend fun fetch(ctx: Context) {
        val settings = Settings(ctx)
        if (settings.apiKey.isNotBlank()) {
            val store = SnapshotStore(ctx)
            val snapshot = buildSnapshot(ctx, settings, store.load())
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

    private suspend fun buildSnapshot(ctx: Context, settings: Settings, previous: Snapshot?): Snapshot {
        val target: Target? = try {
            LocationHelper.refreshSaved(ctx)
            withContext(Dispatchers.IO) { resolveStation(ctx, settings) }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return failed(previous, null, e)
        }
        if (target == null) {
            return Snapshot("", "", null, now(), emptyList(), "Allow location or pick a station in the app")
        }
        return try {
            val departures = withContext(Dispatchers.IO) { NsApi.fetchDepartures(settings.apiKey, target.uic, target.name) }
            Snapshot(target.uic, target.name, target.distance, now(), departures, null, target.lat, target.lng)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            failed(previous, target, e)
        }
    }

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
        Refresher.fetch(applicationContext)
        return Result.success()
    }
}
