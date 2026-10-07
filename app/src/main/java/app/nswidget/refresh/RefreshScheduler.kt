package app.nswidget.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.nswidget.discount.PeakRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Repaints the widget once a minute, just after the clock minute changes, so countdowns and the
 * list of departures are always current. A repaint only re-reads saved data; the NS API is called
 * separately and less often (see [FetchPolicy]).
 *
 * The alarm is non-waking: while the screen is off it simply waits, costing no battery and no API
 * calls, and it fires as soon as the phone wakes up - so the widget is fresh when you look at it.
 */
object RefreshScheduler {
    private const val ACTION_TICK = "app.nswidget.TICK"

    fun scheduleNext(ctx: Context, now: ZonedDateTime = ZonedDateTime.now(PeakRules.ZONE)) {
        val triggerAt = now.toInstant().toEpochMilli() + nextDelayMs(now)
        val alarms = ctx.getSystemService(AlarmManager::class.java) ?: return
        // RTC (not RTC_WAKEUP) never wakes the phone, and inexact alarms need no special
        // permission; Android may deliver a tick a little late, which is fine for a repaint.
        alarms.set(AlarmManager.RTC, triggerAt, pendingIntent(ctx))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(ctx))
    }

    /**
     * One second after the start of the next clock minute. Peak boundaries fall on whole minutes,
     * so this also flips the discount banner on time. Never less than 5 s, so ticks can't pile up.
     */
    internal fun nextDelayMs(now: ZonedDateTime): Long {
        val msIntoMinute = now.second * 1_000L + now.nano / 1_000_000L
        val delay = 60_000L - msIntoMinute + 1_000L
        return if (delay < 5_000L) delay + 60_000L else delay
    }

    private fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        0,
        Intent(ctx, TickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Refresher.tick(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms don't survive a reboot or an app update, so re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        if (Refresher.hasWidgets(appContext)) Refresher.start(appContext)
    }
}
