package app.nswidget.data

import android.content.Context
import androidx.core.content.edit
import app.nswidget.discount.PeakRules
import java.time.LocalDate

/** Counts NS API calls per day, so the widget can slow down near the limit and show the usage. */
class ApiUsage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("api_usage", Context.MODE_PRIVATE)

    fun callsToday(today: LocalDate = LocalDate.now(PeakRules.ZONE)): Int =
        countFor(prefs.getString(K_DAY, null), prefs.getInt(K_COUNT, 0), today)

    fun record(today: LocalDate = LocalDate.now(PeakRules.ZONE)) {
        val count = callsToday(today) + 1
        prefs.edit {
            putString(K_DAY, today.toString())
            putInt(K_COUNT, count)
        }
    }

    companion object {
        private const val K_DAY = "day"
        private const val K_COUNT = "count"

        /** The stored count only applies to the day it was stored on; a new day starts at zero. */
        internal fun countFor(storedDay: String?, storedCount: Int, today: LocalDate): Int =
            if (storedDay == today.toString()) storedCount else 0
    }
}
