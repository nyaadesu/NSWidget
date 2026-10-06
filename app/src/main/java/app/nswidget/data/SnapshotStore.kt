package app.nswidget.data

import android.content.Context
import androidx.core.content.edit

/** Persists the latest [Snapshot] so the widget can render without touching the network. */
class SnapshotStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("snapshot", Context.MODE_PRIVATE)

    fun load(): Snapshot? = prefs.getString(KEY, null)?.let { Snapshot.fromJson(it) }

    fun save(snapshot: Snapshot) = prefs.edit { putString(KEY, snapshot.toJson()) }

    private companion object {
        const val KEY = "json"
    }
}
