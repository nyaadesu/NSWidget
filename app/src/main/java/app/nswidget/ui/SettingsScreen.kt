package app.nswidget.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.nswidget.StatusPalette
import app.nswidget.data.ApiUsage
import app.nswidget.data.Favourite
import app.nswidget.data.Favourites
import app.nswidget.data.SavedLocation
import app.nswidget.data.Settings
import app.nswidget.data.Station
import app.nswidget.data.StationRepository
import app.nswidget.data.friendlyMessage
import app.nswidget.data.nearestStation
import app.nswidget.discount.DiscountPhase
import app.nswidget.discount.DiscountText
import app.nswidget.discount.PeakRules
import app.nswidget.discount.discountStatus
import app.nswidget.location.LocationHelper
import app.nswidget.refresh.FetchPolicy
import app.nswidget.refresh.Refresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(ctx) }

    var apiKeyInput by remember { mutableStateOf(settings.apiKey) }
    var savedKey by remember { mutableStateOf(settings.apiKey) }
    var useNearest by remember { mutableStateOf(settings.useNearest) }
    var showVia by remember { mutableStateOf(settings.showVia) }
    var fixedName by remember { mutableStateOf(settings.fixedStationName) }
    var favourites by remember { mutableStateOf(settings.favourites) }
    var newLabel by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf(emptyList<Station>()) }
    var stationError by remember { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf(settings.location) }
    var hasForeground by remember { mutableStateOf(LocationHelper.hasForeground(ctx)) }
    var hasBackground by remember { mutableStateOf(LocationHelper.hasBackground(ctx)) }
    var now by remember { mutableStateOf(ZonedDateTime.now(PeakRules.ZONE)) }
    val apiUsage = remember { ApiUsage(ctx) }
    var apiCallsToday by remember { mutableStateOf(apiUsage.callsToday()) }

    // Keeps the live discount card and the permission state current (permissions can be changed
    // from system settings while this screen is open).
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(PeakRules.ZONE)
            hasForeground = LocationHelper.hasForeground(ctx)
            hasBackground = LocationHelper.hasBackground(ctx)
            apiCallsToday = apiUsage.callsToday()
            delay(3_000)
        }
    }

    // (Re)load the station list whenever the saved key changes.
    LaunchedEffect(savedKey) {
        if (savedKey.isBlank()) return@LaunchedEffect
        stationError = null
        try {
            stations = withContext(Dispatchers.IO) { StationRepository(ctx).load(savedKey) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stationError = e.friendlyMessage()
        }
    }

    fun updateLocation() {
        scope.launch {
            // The app is on screen, so a new fix works even without background location access.
            location = LocationHelper.refreshSaved(ctx, preferFresh = true)
            Refresher.requestFetch(ctx)
        }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasForeground = LocationHelper.hasForeground(ctx)
        if (hasForeground) updateLocation()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        hasBackground = LocationHelper.hasBackground(ctx)
    }

    val status = discountStatus(now)
    val label = DiscountText.label(status, now)
    val nearest = remember(stations, location) {
        location?.let { nearestStation(stations, it.lat, it.lng) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text("NS Widget", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Off-peak discount and departures",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatusHero(status.phase, label.title, label.subtitle)

            Section("NS API key") {
                Text(
                    "Departures come from the NS API. Create a free account at apiportal.ns.nl, " +
                        "subscribe to the “Ns-App” product and paste your key here. " +
                        "The discount banner works without it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Subscription key") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = apiKeyInput.trim() != savedKey,
                        onClick = {
                            settings.apiKey = apiKeyInput
                            savedKey = settings.apiKey
                            Refresher.requestFetch(ctx)
                        },
                    ) { Text("Save key") }
                    FilledTonalButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apiportal.ns.nl")))
                    }) { Text("Open NS portal") }
                }
            }

            Section("Station") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use nearest station", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !useNearest -> "Off – always showing the fixed station"
                                nearest != null -> "Currently ${nearest.first.name}"
                                else -> "Needs location permission"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = useNearest, onCheckedChange = {
                        useNearest = it
                        settings.useNearest = it
                        Refresher.requestFetch(ctx)
                    })
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Fixed station", style = MaterialTheme.typography.titleMedium)
                Text(
                    (fixedName ?: "None chosen") +
                        if (useNearest) " · used when no location is known" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StationPicker(stations, "Search stations") { station ->
                    settings.fixedStationUic = station.uic
                    settings.fixedStationName = station.name
                    settings.fixedStationLatLng = station.lat to station.lng
                    fixedName = station.name
                    Refresher.requestFetch(ctx)
                }
                when {
                    savedKey.isBlank() -> Hint("Save your API key first to load the station list.")
                    stationError != null -> Hint(stationError ?: "", error = true)
                    stations.isEmpty() -> Hint("Loading stations…")
                }
            }

            Section("Favourite stations") {
                Text(
                    "On the widget, trains that stop at these are shown in the accent colour, and the " +
                        "fastest way to each one (possibly with a change) gets a filled highlight and a bold destination.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                favourites.forEach { favourite ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(favourite.label, style = MaterialTheme.typography.titleMedium)
                            if (favourite.label != favourite.name) {
                                Text(
                                    favourite.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = {
                            favourites = favourites - favourite
                            settings.favourites = favourites
                            Refresher.requestFetch(ctx)
                        }) { Text("Remove") }
                    }
                }
                if (favourites.size < Favourites.MAX) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it.take(Favourites.MAX_LABEL) },
                        label = { Text("Label, e.g. Home or Work") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StationPicker(stations, "Station to add") { station ->
                        if (favourites.none { it.uic == station.uic }) {
                            val label = newLabel.trim().ifEmpty { station.name }
                            favourites = favourites + Favourite(station.uic, station.name, label)
                            settings.favourites = favourites
                            newLabel = ""
                            Refresher.requestFetch(ctx)
                        }
                    }
                } else {
                    Hint("Up to ${Favourites.MAX} stations: each one adds a journey-planner call per refresh.")
                }
            }

            Section("Departures") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show cities along the route", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Small, dimmed text such as “Rotterdam, Delft” next to the destination, when there is room.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showVia, onCheckedChange = {
                        showVia = it
                        settings.showVia = it
                        Refresher.requestFetch(ctx)
                    })
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("NS API calls today: %,d of 5,000".format(apiCallsToday), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Delays and platform changes refresh every 2 min while a train leaves within 15 min, " +
                        "every 5–10 min otherwise, and only while the screen is on. " +
                        "Above ${FetchPolicy.SOFT_DAILY_LIMIT} calls a day it slows to every 15 min.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Location") {
                Text(
                    "Used only to pick the nearest station. It never leaves your phone " +
                        "– only the station code is sent to NS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    locationSummary(hasForeground, hasBackground, location),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!hasForeground) {
                    Button(onClick = {
                        foregroundLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }) { Text("Allow location") }
                } else {
                    FilledTonalButton(onClick = { updateLocation() }) { Text("Update location") }
                }
                if (hasForeground && !hasBackground && Build.VERSION.SDK_INT >= 29) {
                    Text(
                        "To let the widget follow you while the app is closed, choose “Allow all the time”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = {
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }) { Text("Allow in background") }
                }
            }

            Button(
                onClick = {
                    if (useNearest && hasForeground) updateLocation() else Refresher.requestFetch(ctx)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh widget now") }

            Text(
                "Add the widget from your launcher's widget picker (search for “NS”).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            val version = remember {
                try {
                    @Suppress("DEPRECATION")
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                } catch (e: Exception) {
                    null
                }
            }
            if (version != null) {
                Text(
                    "Version $version",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** The live discount status: a big tonal card with a cookie-shaped icon badge. */
@Composable
private fun StatusHero(phase: DiscountPhase, title: String, subtitle: String) {
    val tone = StatusPalette.of(phase)
    val dark = isSystemInDarkTheme()
    val container = if (dark) tone.containerNight else tone.containerDay
    val content = if (dark) tone.contentNight else tone.contentDay
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CookieShape)
                .background(content.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tone.icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = content)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = content)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** A station search box with up to six matches; [onPick] runs when one is tapped. */
@Composable
private fun StationPicker(stations: List<Station>, label: String, onPick: (Station) -> Unit) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(label) },
        singleLine = true,
        enabled = stations.isNotEmpty(),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    )
    if (query.length >= 2) {
        val matches = stations
            .filter { it.name.contains(query, ignoreCase = true) || it.code.equals(query, ignoreCase = true) }
            .take(6)
        if (matches.isEmpty()) {
            Hint("No station found")
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                matches.forEachIndexed { index, station ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        station.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(station)
                                query = ""
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun locationSummary(foreground: Boolean, background: Boolean, location: SavedLocation?): String {
    if (!foreground) return "Location permission not granted."
    val age = location?.let { (System.currentTimeMillis() - it.atMillis) / 60_000 }
    val fix = when {
        age == null -> "No location fix yet."
        age < 2 -> "Last fix: just now."
        age < 120 -> "Last fix: $age min ago."
        else -> "Last fix: ${age / 60} h ago."
    }
    val bg = if (background) "Background access on." else "Background access off – the widget uses your last fix from when the app was open."
    return "$fix $bg"
}
