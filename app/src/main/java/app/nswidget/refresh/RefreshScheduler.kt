package app.nswidget.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.nswidget.discount.PeakRules
import app.nswidget.discount.discountStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Arms one inexact alarm at a time. Each tick reschedules the next one, so the interval can adapt:
 * every minute in the last 10 minutes before the discount starts/ends (so "wait x minutes" is right),
 * every 5 minutes in the hour before, and every 15 minutes otherwise. The final tick lands just after
 * the boundary so the banner flips on time.
 */
object RefreshScheduler {
    private const val ACTION_TICK = "app.nswidget.TICK"

    fun scheduleNext(ctx: Context, now: ZonedDateTime = ZonedDateTime.now(PeakRules.ZONE)) {
        val nowMs = now.toInstant().toEpochMilli()
        val triggerAt = nowMs + nextDelayMs(now)
        val alarms = ctx.getSystemService(AlarmManager::class.java) ?: return
        // Inexact on purpose: exact alarms need a special permission, and a few minutes of
        // slack while the phone sleeps doesn't matter (the screen is off, nobody is looking).
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent(ctx))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(ctx))
    }

    internal fun nextDelayMs(now: ZonedDateTime): Long {
        val status = discountStatus(now)
        val untilChangeMs = status.changesAt.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
        val interval = when {
            status.minutesUntilChange <= 10 -> 60_000L
            status.minutesUntilChange <= PeakRules.WARN_MINUTES -> 5 * 60_000L
            else -> 15 * 60_000L
        }
        return minOf(interval, untilChangeMs + 1_000L).coerceAtLeast(30_000L)
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
