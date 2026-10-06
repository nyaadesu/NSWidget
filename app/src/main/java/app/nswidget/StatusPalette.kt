package app.nswidget

import androidx.compose.ui.graphics.Color
import app.nswidget.discount.DiscountPhase

/**
 * Tonal containers for the discount status, shared by the widget and the app so they match.
 * Everything else follows the system's dynamic colour; these stay semantic (green / amber / red)
 * because "do I get the discount" should read at a glance whatever the wallpaper is.
 */
object StatusPalette {
    class Tone(
        val containerDay: Color,
        val containerNight: Color,
        val contentDay: Color,
        val contentNight: Color,
        val icon: Int,
    )

    private val Green = Tone(
        containerDay = Color(0xFFC9EFD3),
        containerNight = Color(0xFF1D4A2F),
        contentDay = Color(0xFF0B3B1E),
        contentNight = Color(0xFFC9EFD3),
        icon = R.drawable.ic_status_check,
    )
    private val Amber = Tone(
        containerDay = Color(0xFFFFE08A),
        containerNight = Color(0xFF5A4500),
        contentDay = Color(0xFF3A2B00),
        contentNight = Color(0xFFFFE9A8),
        icon = R.drawable.ic_status_schedule,
    )
    private val Red = Tone(
        containerDay = Color(0xFFFFD9DD),
        containerNight = Color(0xFF5C2230),
        contentDay = Color(0xFF5C0F1F),
        contentNight = Color(0xFFFFD9DD),
        icon = R.drawable.ic_status_block,
    )

    fun of(phase: DiscountPhase): Tone = when (phase) {
        DiscountPhase.OFF_PEAK -> Green
        DiscountPhase.OFF_PEAK_ENDING, DiscountPhase.PEAK_ENDING -> Amber
        DiscountPhase.PEAK -> Red
    }
}
