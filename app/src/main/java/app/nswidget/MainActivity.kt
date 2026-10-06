package app.nswidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.nswidget.refresh.Refresher
import app.nswidget.ui.NsTheme
import app.nswidget.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NsTheme {
                SettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-arms the tick alarm if the system dropped it (e.g. after a force-stop).
        if (Refresher.hasWidgets(this)) Refresher.start(this)
    }
}
