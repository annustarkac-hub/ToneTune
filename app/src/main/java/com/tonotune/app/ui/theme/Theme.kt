package com.tonotune.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonotune.app.audio.PresetCategory

/** TonoTune design tokens: a deep "studio at night" dark theme with neon cyan/violet accents. */
object Tono {
    val Bg = Color(0xFF07080D)
    val Surface = Color(0xFF10121A)
    val SurfaceHigh = Color(0xFF171A25)
    val SurfaceHigher = Color(0xFF1F2331)
    val Outline = Color(0xFF2A2F40)
    val OutlineSoft = Color(0xFF1E2230)

    val TextPrimary = Color(0xFFF4F6FB)
    val TextSecondary = Color(0xFFA3A9BC)
    val TextMuted = Color(0xFF6D7488)

    val Cyan = Color(0xFF35D7FF)
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFFF4FA3)
    val Amber = Color(0xFFFFB547)
    val Green = Color(0xFF3EE58F)
    val Red = Color(0xFFFF5C6C)

    val AccentBrush = Brush.linearGradient(listOf(Cyan, Violet))
    val WarmBrush = Brush.linearGradient(listOf(Amber, Pink))

    /** One hue per band, low (warm) to high (cool). */
    val BandColors = listOf(
        Color(0xFFFF6B6B), // 60 Hz sub
        Color(0xFFFFB547), // 230 Hz body
        Color(0xFF3EE58F), // 910 Hz mids
        Color(0xFF35D7FF), // 3.6 kHz presence
        Color(0xFFB18CFF), // 14 kHz air
    )

    fun categoryColor(category: PresetCategory): Color = when (category) {
        PresetCategory.SIGNATURE -> Cyan
        PresetCategory.REGIONAL -> Color(0xFF3EE58F)
        PresetCategory.DJ_BASS -> Color(0xFFFF6B6B)
        PresetCategory.DANCE -> Pink
        PresetCategory.VOCAL -> Amber
        PresetCategory.MOOD -> Color(0xFFB18CFF)
        PresetCategory.UTILITY -> Color(0xFF7FA7FF)
    }
}

private val TonoColors = darkColorScheme(
    primary = Tono.Cyan,
    onPrimary = Color(0xFF00222C),
    primaryContainer = Color(0xFF0B3442),
    onPrimaryContainer = Color(0xFFBDEFFF),
    secondary = Tono.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A1E4D),
    onSecondaryContainer = Color(0xFFE3D9FF),
    tertiary = Tono.Amber,
    background = Tono.Bg,
    onBackground = Tono.TextPrimary,
    surface = Tono.Surface,
    onSurface = Tono.TextPrimary,
    surfaceVariant = Tono.SurfaceHigh,
    onSurfaceVariant = Tono.TextSecondary,
    surfaceContainer = Tono.Surface,
    surfaceContainerHigh = Tono.SurfaceHigh,
    surfaceContainerHighest = Tono.SurfaceHigher,
    outline = Tono.Outline,
    outlineVariant = Tono.OutlineSoft,
    error = Tono.Red,
)

private val Base = FontFamily.SansSerif

private val TonoTypography = Typography(
    displaySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Base, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Base, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

private val TonoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Tabular figures so numeric readouts don't jitter while sliding. */
val Numeric = TextStyle(fontFeatureSettings = "tnum", fontFamily = FontFamily.Monospace)

@Composable
fun TonoTuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TonoColors, typography = TonoTypography, shapes = TonoShapes, content = content)
}
