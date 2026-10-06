package app.nswidget.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Fallbacks for Android 11 and below, where there is no wallpaper-based dynamic colour.
private val LightColors = lightColorScheme(
    primary = Color(0xFF003082),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF261A00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC917),
    onPrimary = Color(0xFF241A00),
    primaryContainer = Color(0xFF0B2A6B),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondaryContainer = Color(0xFF4A3900),
    onSecondaryContainer = Color(0xFFFFE08A),
)

/** Big, soft corners - the Material 3 Expressive look. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/** Heavier weights on headings, as the expressive type scale does. */
private val ExpressiveTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** A nine-lobed "cookie", like the scalloped app icons on recent Android launchers. */
val CookieShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = minOf(cx, cy)
    val lobes = 9
    val depth = 0.07f
    val steps = 180
    for (i in 0..steps) {
        val t = i.toFloat() / steps * 2f * Math.PI.toFloat()
        val r = radius * (1f - depth + depth * cos(lobes * t))
        val x = cx + r * cos(t)
        val y = cy + r * sin(t)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
fun NsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
