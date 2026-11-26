package com.tonotune.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** TonoTune design tokens: quiet dark surfaces, one cyan accent, status colours only where needed. */
object Tono {
    val Bg = Color(0xFF0B0C10)
    val Surface = Color(0xFF14161C)
    val SurfaceHigh = Color(0xFF1C1F27)
    val Outline = Color(0xFF262A34)

    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFF9CA3B4)
    val TextMuted = Color(0xFF656C7D)

    val Accent = Color(0xFF3DD6F5)
    val AccentSoft = Color(0x263DD6F5)
    val Violet = Color(0xFF8B7CF6)
    val Warn = Color(0xFFF5B547)
    val Error = Color(0xFFFF6370)
    val Ok = Color(0xFF45D98C)
}

private val Colors = darkColorScheme(
    primary = Tono.Accent,
    onPrimary = Color(0xFF00222B),
    secondary = Tono.Violet,
    background = Tono.Bg,
    onBackground = Tono.TextPrimary,
    surface = Tono.Surface,
    onSurface = Tono.TextPrimary,
    surfaceVariant = Tono.SurfaceHigh,
    onSurfaceVariant = Tono.TextSecondary,
    surfaceContainer = Tono.Surface,
    surfaceContainerHigh = Tono.SurfaceHigh,
    surfaceContainerHighest = Tono.SurfaceHigh,
    outline = Tono.Outline,
    outlineVariant = Tono.Outline,
    error = Tono.Error,
)

private val Sans = FontFamily.SansSerif

private val Type = Typography(
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

private val Shape = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** Tabular figures so numeric readouts don't jitter while sliding. */
val Numeric = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun TonoTuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = Shape, content = content)
}
