package com.tonotune.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TempleHindu
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonotune.app.audio.EqPreset
import com.tonotune.app.audio.PresetCategory
import com.tonotune.app.ui.theme.Tono

/** Base surface card with a hairline border. */
@Composable
fun TonoCard(
    modifier: Modifier = Modifier,
    borderBrush: Brush? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Tono.SurfaceHigh, Tono.Surface)))
            .border(BorderStroke(1.dp, borderBrush ?: Brush.linearGradient(listOf(Tono.Outline, Tono.OutlineSoft))), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Tono.TextPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted)
        }
        trailing?.invoke()
    }
}

@Composable
fun tonoSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = Tono.Cyan,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Tono.TextMuted,
    uncheckedTrackColor = Tono.SurfaceHigher,
    uncheckedBorderColor = Tono.Outline,
)

/** Icon + title + description + switch; the whole row is the touch target. */
@Composable
fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Tono.Cyan,
) {
    val iconBg by animateColorAsState(if (checked) tint.copy(alpha = 0.18f) else Tono.SurfaceHigher, label = "toggleBg")
    val iconTint by animateColorAsState(if (checked) tint else Tono.TextMuted, label = "toggleTint")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Tono.TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null, colors = tonoSwitchColors())
    }
}

/** Animated speaker "orb": concentric rings pulse outward while the Tono is connected. */
@Composable
fun SpeakerOrb(connected: Boolean, active: Boolean, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
    )
    val breathe by transition.animateFloat(
        0.94f, 1.04f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val color by animateColorAsState(
        when {
            connected && active -> Tono.Cyan
            connected -> Tono.Amber
            else -> Tono.TextMuted
        },
        label = "orbColor",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (connected) {
            Canvas(Modifier.matchParentSize()) {
                for (k in 0..1) {
                    val t = (pulse + k * 0.5f) % 1f
                    drawCircle(color.copy(alpha = (1f - t) * 0.35f), radius = this.size.minDimension / 2f * (0.55f + 0.45f * t))
                }
            }
        }
        Box(
            Modifier
                .size(size * 0.62f)
                .scale(if (connected) breathe else 1f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.10f))))
                .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Speaker, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.34f))
        }
    }
}

fun categoryIcon(category: PresetCategory?): ImageVector = when (category) {
    null -> Icons.Rounded.Equalizer
    PresetCategory.SIGNATURE -> Icons.Rounded.AutoAwesome
    PresetCategory.REGIONAL -> Icons.Rounded.Landscape
    PresetCategory.DJ_BASS -> Icons.Rounded.Album
    PresetCategory.DANCE -> Icons.Rounded.Nightlife
    PresetCategory.VOCAL -> Icons.Rounded.RecordVoiceOver
    PresetCategory.MOOD -> Icons.Rounded.Spa
    PresetCategory.UTILITY -> Icons.Rounded.Tune
}

fun presetIcon(preset: EqPreset): ImageVector = when (preset.id) {
    "tono_pro_signature" -> Icons.Rounded.Verified
    "garhwali_song" -> Icons.Rounded.Landscape
    "dj_songs" -> Icons.Rounded.Album
    "dance_songs" -> Icons.Rounded.Celebration
    "deep_sub_woofer" -> Icons.Rounded.Speaker
    "anti_muddy_clean" -> Icons.Rounded.Waves
    "bollywood_hindi_hits" -> Icons.Rounded.MusicNote
    "punjabi_bhangra" -> Icons.Rounded.Whatshot
    "edm_club_party" -> Icons.Rounded.GraphicEq
    "party_floor_boost" -> Icons.Rounded.Nightlife
    "lofi_chill" -> Icons.Rounded.Headphones
    "acoustic_unplugged" -> Icons.Rounded.LibraryMusic
    "ghazal_classics" -> Icons.Rounded.RecordVoiceOver
    "podcast_news" -> Icons.Rounded.Podcasts
    "night_mode" -> Icons.Rounded.NightsStay
    "cinema_ott" -> Icons.Rounded.Movie
    "cricket_sports" -> Icons.Rounded.SportsCricket
    "karaoke_mic" -> Icons.Rounded.Mic
    "rock_metal" -> Icons.Rounded.Whatshot
    "hiphop_rap" -> Icons.Rounded.Album
    "classical_instrumental" -> Icons.Rounded.Piano
    "devotional_bhajan" -> Icons.Rounded.TempleHindu
    "kumaoni_pahadi_folk" -> Icons.Rounded.Forest
    "kids_rhymes" -> Icons.Rounded.ChildCare
    "bass_safe_limiter" -> Icons.Rounded.Shield
    "treble_sparkle" -> Icons.Rounded.AutoAwesome
    "flat_reference" -> Icons.Rounded.Equalizer
    else -> categoryIcon(preset.category)
}

/** Selectable preset tile with icon, name, tagline and a mini response curve. */
@Composable
fun PresetCard(
    preset: EqPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Tono.categoryColor(preset.category)
    val isSelected = selected
    val shape = RoundedCornerShape(20.dp)
    val borderAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "presetBorder")
    val pressedScale by animateFloatAsState(if (selected) 1f else 0.985f, label = "presetScale")
    val border = if (selected) Brush.linearGradient(listOf(color, Tono.Violet))
    else if (preset.featured) Brush.linearGradient(listOf(color.copy(alpha = 0.45f), Tono.Outline))
    else Brush.linearGradient(listOf(Tono.Outline, Tono.OutlineSoft))

    Column(
        modifier
            .scale(pressedScale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerpColor(Tono.SurfaceHigh, color, 0.10f * borderAlpha + if (preset.featured) 0.04f else 0f),
                        Tono.Surface,
                    ),
                ),
            )
            .border(BorderStroke(if (selected) 2.dp else 1.dp, border), shape)
            .semantics { this.selected = isSelected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(presetIcon(preset), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            when {
                selected -> Icon(Icons.Rounded.CheckCircle, "Selected", tint = color, modifier = Modifier.size(22.dp))
                preset.featured -> Icon(Icons.Rounded.Star, "Featured", tint = Tono.Amber, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            preset.name, style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            preset.tagline, style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        MiniCurve(preset.bandsDb.toList(), color, Modifier.fillMaxWidth().height(28.dp))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(preset.category.label, color)
            if (preset.bassBoostStrength >= 800) Pill("Bass+", Tono.Red)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * Volume bar split into Auto-Tweak zones (Loudness < 35 %, Neutral, Protect > 80 %)
 * with an animated marker at the current media volume.
 */
@Composable
fun VolumeZoneMeter(ratio: Float, enabled: Boolean, modifier: Modifier = Modifier) {
    val pos by animateFloatAsState(ratio.coerceIn(0f, 1f), label = "vol")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, label = "volAlpha")
    Canvas(modifier) {
        val h = size.height
        val barH = h * 0.36f
        val y = (h - barH) / 2f
        val r = androidx.compose.ui.geometry.CornerRadius(barH / 2)
        val low = size.width * 0.35f
        val high = size.width * 0.80f
        drawRoundRect(Tono.SurfaceHigher, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, barH), r)
        drawRoundRect(Tono.Violet.copy(alpha = 0.55f * alpha), Offset(0f, y), androidx.compose.ui.geometry.Size(low, barH), r)
        drawRect(Tono.Green.copy(alpha = 0.35f * alpha), Offset(low + 2f, y), androidx.compose.ui.geometry.Size(high - low - 4f, barH))
        drawRoundRect(Tono.Red.copy(alpha = 0.55f * alpha), Offset(high, y), androidx.compose.ui.geometry.Size(size.width - high, barH), r)
        val x = pos * size.width
        drawCircle(Color.White.copy(alpha = 0.25f), radius = h * 0.5f, center = Offset(x, h / 2))
        drawCircle(Color.White, radius = h * 0.30f, center = Offset(x, h / 2))
    }
}
