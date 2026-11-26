package com.tonotune.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonotune.app.ui.theme.Numeric
import com.tonotune.app.ui.theme.Tono
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Float.snap(step: Float) = (this / step).roundToInt() * step

/**
 * Vertical EQ fader. Touch anywhere on the track to jump there, drag to slide, haptic tick on
 * every whole dB. Adjustable with TalkBack.
 */
@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -12f..12f,
    step: Float = 0.5f,
    trackHeight: Dp = 168.dp,
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    var active by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(value, label = "fader")
    val capScale by animateFloatAsState(if (active) 1.15f else 1f, label = "cap")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            (if (value > 0) "+" else "") + if (value % 1f == 0f) "%.0f".format(value) else "%.1f".format(value),
            style = MaterialTheme.typography.labelMedium.merge(Numeric),
            color = if (abs(value) < 0.01f) Tono.TextMuted else Tono.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(44.dp)
                .height(trackHeight)
                .semantics {
                    contentDescription = "$label band"
                    stateDescription = "%+.1f decibels".format(current)
                    progressBarRangeInfo = ProgressBarRangeInfo(current, range, ((range.endInclusive - range.start) / step).toInt() - 1)
                    setProgress { t -> onChange(t.coerceIn(range).snap(step)); true }
                }
                .pointerInput(range, step) {
                    val pad = 10.dp.toPx()
                    fun update(y: Float) {
                        val frac = 1f - ((y - pad) / (size.height - pad * 2)).coerceIn(0f, 1f)
                        val v = (range.start + frac * (range.endInclusive - range.start)).snap(step).coerceIn(range)
                        if (v != current) {
                            if (v.roundToInt() != current.roundToInt() || v == 0f) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onChange(v)
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        active = true
                        update(down.position.y)
                        drag(down.id) { change -> change.consume(); update(change.position.y) }
                        active = false
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val pad = 10.dp.toPx()
                val cx = size.width / 2f
                val trackW = 4.dp.toPx()
                val usable = size.height - pad * 2
                fun yFor(v: Float) = pad + (1f - (v - range.start) / (range.endInclusive - range.start)) * usable
                drawRoundRect(Tono.SurfaceHigh, Offset(cx - trackW / 2, pad), Size(trackW, usable), CornerRadius(trackW))
                val zeroY = yFor(0f)
                drawLine(Tono.Outline, Offset(cx - 10.dp.toPx(), zeroY), Offset(cx + 10.dp.toPx(), zeroY), 1.dp.toPx())
                val valY = yFor(shown)
                drawRoundRect(
                    Tono.Accent, Offset(cx - trackW / 2, minOf(zeroY, valY)), Size(trackW, abs(zeroY - valY)), CornerRadius(trackW),
                )
                val r = 9.dp.toPx() * capScale
                if (active) drawCircle(Tono.AccentSoft, r * 2f, Offset(cx, valY))
                drawCircle(Tono.TextPrimary, r, Offset(cx, valY))
                drawCircle(Tono.Accent, r * 0.45f, Offset(cx, valY))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tono.TextSecondary)
    }
}

/** Compact labelled 0–1000 slider shown as a percentage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrengthSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 1000,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Tono.TextPrimary, modifier = Modifier.weight(1f))
            Text("${(value * 100f / max).roundToInt()}%", style = MaterialTheme.typography.labelLarge.merge(Numeric), color = Tono.TextSecondary)
        }
        val colors = SliderDefaults.colors(
            thumbColor = Tono.TextPrimary,
            activeTrackColor = Tono.Accent,
            inactiveTrackColor = Tono.SurfaceHigh,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange((it / 10f).roundToInt() * 10) },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = title },
            colors = colors,
            thumb = {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Tono.TextPrimary))
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = colors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    modifier = Modifier.height(4.dp),
                )
            },
        )
    }
}
