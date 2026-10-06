package app.nswidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.nswidget.refresh.RefreshScheduler
import app.nswidget.refresh.Refresher

class NsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NsWidget()

    /** The first widget was placed: fetch departures and start the tick alarm. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Refresher.start(context)
    }

    /** The last widget was removed: stop waking up. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshScheduler.cancel(context)
    }
}
