package app.nswidget.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.nswidget.MainActivity
import app.nswidget.R
import app.nswidget.StatusPalette
import app.nswidget.data.Settings
import app.nswidget.data.SnapshotStore
import app.nswidget.discount.PeakRules
import app.nswidget.refresh.Refresher
import java.time.ZonedDateTime

// The platform sign keeps NS yellow whatever the wallpaper; everything else follows dynamic colour.
private val ChipInk = ColorProvider(Color(0xFF14233F))
private val ChipWhite = ColorProvider(Color.White)

private val ROW_HEIGHT = 26.dp

/** Outer padding, header, banner, the gaps between them and the list card's own padding. */
private const val FIXED_HEIGHT_DP = 120f

/** Outer widget padding (2 x 10) plus the list card's horizontal padding (2 x 12). */
private const val LIST_SIDE_PADDING_DP = 44f

class NsWidget : GlanceAppWidget() {
    // Exact sizes let the number of departure rows follow the size the user resizes the widget to.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = Settings(context)
        val model = WidgetModels.build(
            now = ZonedDateTime.now(PeakRules.ZONE),
            hasKey = settings.apiKey.isNotBlank(),
            snapshot = SnapshotStore(context).load(),
            showVia = settings.showVia,
        )
        provideContent {
            GlanceTheme {
                WidgetContent(model)
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Refresher.requestFetch(context)
        Refresher.start(context)
        NsWidget().updateAll(context) // repaint the discount banner straight away
    }
}

@Composable
private fun WidgetContent(model: WidgetModel) {
    val size = LocalSize.current
    val context = LocalContext.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
    val openMap = model.mapUri?.let { actionStartActivity(mapsIntent(context, it)) }
    val compact = size.width < 200.dp
    val maxRows = ((size.height.value - FIXED_HEIGHT_DP) / ROW_HEIGHT.value)
        .toInt()
        .coerceIn(1, WidgetModels.MAX_ROWS)

    val rows = if (model.message == null) model.rows.take(maxRows) else emptyList()
    // Only make room for a delay ("+3") when a visible train is actually late, and only as wide
    // as its digits need - so on-time boards have no gap at all.
    val delayDigits = rows
        .filter { it.delayMinutes > 0 && !it.cancelled }
        .maxOfOrNull { it.delayMinutes.toString().length } ?: 0
    val delaySlot = when (delayDigits) {
        0 -> 0.dp
        1 -> 20.dp
        else -> 28.dp
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .roundedBySystem()
            .padding(10.dp)
            .clickable(openApp),
    ) {
        Header(model, compact, openMap)
        Spacer(GlanceModifier.height(6.dp))
        DiscountBanner(model, compact)
        Spacer(GlanceModifier.height(6.dp))
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surfaceVariant)
                .rounded(22.dp)
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            if (model.message != null) {
                Text(
                    text = model.message,
                    style = text(GlanceTheme.colors.onSurfaceVariant, 13),
                    maxLines = 3,
                    modifier = GlanceModifier.padding(vertical = 8.dp),
                )
            } else {
                rows.forEach { DepartureLine(it, compact, delaySlot, size.width.value - LIST_SIDE_PADDING_DP) }
            }
        }
    }
}

@Composable
private fun Header(model: WidgetModel, compact: Boolean, openMap: Action?) {
    val button = if (compact) 28.dp else 32.dp
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (openMap != null) {
            CookieIcon(
                icon = R.drawable.ic_place,
                description = "Open station in maps",
                size = button,
                iconSize = 18.dp,
                shape = GlanceTheme.colors.secondaryContainer,
                tint = GlanceTheme.colors.onSecondaryContainer,
                action = openMap,
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            text = model.stationName ?: "NS departures",
            style = text(GlanceTheme.colors.onSurface, if (compact) 14 else 16, bold = true),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (!compact && model.statusLine != null) {
            Text(
                text = model.statusLine,
                style = text(
                    if (model.offline) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                    11,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        CookieIcon(
            icon = R.drawable.ic_refresh,
            description = "Refresh",
            size = button,
            iconSize = 18.dp,
            shape = GlanceTheme.colors.secondaryContainer,
            tint = GlanceTheme.colors.onSecondaryContainer,
            action = actionRunCallback<RefreshAction>(),
        )
    }
}

/**
 * An icon on a scalloped "cookie" badge - the shape used for icons throughout recent Android.
 * The badge is a white mask image tinted to [shape]; the icon on top is tinted to [tint].
 */
@Composable
private fun CookieIcon(
    icon: Int,
    description: String?,
    size: Dp,
    iconSize: Dp,
    shape: ColorProvider,
    tint: ColorProvider,
    action: Action? = null,
) {
    val base = GlanceModifier.size(size)
    Box(
        modifier = if (action != null) base.clickable(action) else base,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.cookie_mask),
            contentDescription = null,
            modifier = GlanceModifier.size(size),
            colorFilter = ColorFilter.tint(shape),
        )
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun DiscountBanner(model: WidgetModel, compact: Boolean) {
    val tone = StatusPalette.of(model.phase)
    val container = dayNightColor(day = tone.containerDay, night = tone.containerNight)
    val content = dayNightColor(day = tone.contentDay, night = tone.contentNight)
    // A soft tonal cookie behind the icon; the icon itself is the full-strength colour, so it
    // stays readable even if the badge shape were to fail to draw.
    val badge = dayNightColor(
        day = lerp(tone.containerDay, tone.contentDay, 0.22f),
        night = lerp(tone.containerNight, tone.contentNight, 0.22f),
    )
    val label = model.label

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(container)
            .rounded(22.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CookieIcon(
            icon = tone.icon,
            description = null,
            size = if (compact) 28.dp else 34.dp,
            iconSize = if (compact) 16.dp else 20.dp,
            shape = badge,
            tint = content,
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = if (compact) label.compactTitle else label.title,
                style = text(content, if (compact) 13 else 15, bold = true),
                maxLines = 1,
            )
            Text(
                text = if (compact) label.compactSubtitle else label.subtitle,
                style = text(content, if (compact) 11 else 12),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DepartureLine(row: DepartureRow, compact: Boolean, delaySlot: Dp, contentWidthDp: Float) {
    val primary = if (row.cancelled) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface

    // The "via" cities go in the spare space to the right of the destination, and only as many as
    // actually fit (with a clear gap) - so showing them never costs a departure row or squeezes
    // the platform sign.
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val taken = 60f + delaySlot.value + (if (compact) 0f else 34f) + 34f + 8f
    val viaBudget = contentWidthDp - taken -
        WidgetModels.estimateDirectionWidth(row.direction, fontScale) - WidgetModels.VIA_GAP_DP
    val via = if (compact) null else WidgetModels.viaText(row.via, viaBudget, fontScale)

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.time,
            style = text(primary, 15, bold = true, strike = row.cancelled),
            modifier = GlanceModifier.width(60.dp),
            maxLines = 1,
        )
        if (delaySlot > 0.dp) {
            if (row.delayMinutes > 0 && !row.cancelled) {
                Text(
                    text = "+${row.delayMinutes}",
                    style = text(GlanceTheme.colors.error, 11, bold = true),
                    modifier = GlanceModifier.width(delaySlot),
                    maxLines = 1,
                )
            } else {
                Spacer(GlanceModifier.width(delaySlot))
            }
        }
        if (!compact) {
            Text(
                text = row.category,
                style = text(GlanceTheme.colors.onSurfaceVariant, 11),
                modifier = GlanceModifier.width(34.dp),
                maxLines = 1,
            )
        }
        if (via != null) {
            Text(
                text = row.direction,
                style = text(primary, 14, strike = row.cancelled),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = via,
                style = text(GlanceTheme.colors.onSurfaceVariant, 10),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
        } else {
            Text(
                text = row.direction,
                style = text(primary, 14, strike = row.cancelled),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
        }
        PlatformChip(row)
    }
}

/** NS-style platform sign: yellow; orange when the platform changed; red when cancelled. */
@Composable
private fun PlatformChip(row: DepartureRow) {
    if (row.track == null && !row.cancelled) {
        Text("–", style = text(GlanceTheme.colors.onSurfaceVariant, 13, bold = true))
        return
    }
    val chip = when {
        row.cancelled -> R.drawable.chip_red
        row.trackChanged -> R.drawable.chip_orange
        else -> R.drawable.chip_yellow
    }
    Box(
        modifier = GlanceModifier.width(34.dp).height(22.dp).background(ImageProvider(chip)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (row.cancelled) "×" else row.track ?: "",
            style = text(if (row.cancelled) ChipWhite else ChipInk, 13, bold = true),
            maxLines = 1,
        )
    }
}

private const val GOOGLE_MAPS = "com.google.android.apps.maps"

/** Prefers Google Maps; falls back to whichever map app handles `geo:` links. */
private fun mapsIntent(context: Context, uri: String): Intent {
    val generic = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    val googleMaps = Intent(generic).setPackage(GOOGLE_MAPS)
    return if (googleMaps.resolveActivity(context.packageManager) != null) googleMaps else generic
}

/** Rounded corners need Android 12+; older versions simply get square corners. */
private fun GlanceModifier.rounded(radius: Dp): GlanceModifier =
    if (Build.VERSION.SDK_INT >= 31) cornerRadius(radius) else this

/** The launcher's own widget corner radius, so the widget matches its neighbours. */
private fun GlanceModifier.roundedBySystem(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= 31) cornerRadius(android.R.dimen.system_app_widget_background_radius) else this

private fun text(color: ColorProvider, size: Int, bold: Boolean = false, strike: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
)
