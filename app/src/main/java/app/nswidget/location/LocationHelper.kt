package app.nswidget.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import app.nswidget.data.SavedLocation
import app.nswidget.data.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {
    fun hasForeground(ctx: Context): Boolean =
        granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Needed for the widget to read the location while the app isn't on screen (Android 10+). */
    fun hasBackground(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasForeground(ctx)
        }

    /**
     * Stores the best location we can get and returns the saved location.
     * - By default it uses Android's last known location (cheap, but can be stale).
     * - [allowFresh] asks for a new fix only when nothing is cached at all.
     * - [preferFresh] asks for a new fix first (refresh button), falling back to the last known one.
     * A new fix while the app is closed needs "Allow all the time"; without it Android refuses
     * and the last known location is used.
     */
    suspend fun refreshSaved(
        ctx: Context,
        allowFresh: Boolean = false,
        preferFresh: Boolean = false,
    ): SavedLocation? {
        val settings = Settings(ctx)
        var loc = if (preferFresh) fresh(ctx, timeoutMs = 8_000) else null
        if (loc == null) loc = lastKnown(ctx)
        if (loc == null && allowFresh) loc = fresh(ctx)
        val saved = settings.location
        if (loc != null && (saved == null || loc.time > saved.atMillis)) {
            settings.location = SavedLocation(loc.latitude, loc.longitude, loc.time)
        }
        return settings.location
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(ctx: Context): Location? {
        if (!hasForeground(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        var best: Location? = null
        for (provider in lm.getProviders(true)) {
            val candidate = try {
                lm.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }
            if (candidate != null && (best == null || candidate.time > best.time)) best = candidate
        }
        return best
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun fresh(ctx: Context, timeoutMs: Long = 10_000): Location? {
        if (!hasForeground(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) } ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(ctx)) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } else {
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (cont.isActive) cont.resume(location)
                            }

                            // Must be implemented explicitly: they have no default body before API 30.
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }
                        cont.invokeOnCancellation { lm.removeUpdates(listener) }
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun granted(ctx: Context, permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
}
