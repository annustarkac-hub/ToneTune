package com.tonotune.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonotune.app.ui.theme.Numeric
import com.tonotune.app.ui.theme.Tono
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Float.snap(step: Float) = (this / step).roundToInt() * step

/**
 * Tactile vertical mixing-desk fader. Touching anywhere on the track jumps the cap there,
 * dragging slides it, crossing each whole dB gives a haptic tick. Fully TalkBack adjustable.
 */
@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -12f..12f,
    step: Float = 0.5f,
    trackHeight: Dp = 196.dp,
    onDragEnd: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val currentValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val onEnd by rememberUpdatedState(onDragEnd)
    var active by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (active) 1f else 0f, label = "faderGlow")
    val shown by animateFloatAsState(value, label = "faderValue")

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (if (value > 0) "+" else "") + "%.1f".format(value),
            style = MaterialTheme.typography.labelLarge.merge(Numeric),
            color = if (abs(value) < 0.01f) Tono.TextMuted else color,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(52.dp)
                .height(trackHeight)
                .semantics {
                    contentDescription = "$label equalizer band"
                    stateDescription = "%+.1f decibels".format(currentValue)
                    progressBarRangeInfo = ProgressBarRangeInfo(currentValue, range, ((range.endInclusive - range.start) / step).toInt() - 1)
                    setProgress { target ->
                        onChange(target.coerceIn(range).snap(step)); onEnd(); true
                    }
                }
                .pointerInput(range, step) {
                    val pad = 14.dp.toPx()
                    fun valueAt(y: Float): Float {
                        val usable = size.height - pad * 2
                        val frac = 1f - ((y - pad) / usable).coerceIn(0f, 1f)
                        return (range.start + frac * (range.endInclusive - range.start)).snap(step).coerceIn(range)
                    }
                    fun update(y: Float) {
                        val v = valueAt(y)
                        if (v != currentValue) {
                            if (v.roundToInt() != currentValue.roundToInt() || v == 0f) {
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
                        drag(down.id) { change ->
                            change.consume()
                            update(change.position.y)
                        }
                        active = false
                        onEnd()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val pad = 14.dp.toPx()
                val cx = size.width / 2f
                val trackW = 6.dp.toPx()
                val usable = size.height - pad * 2
                fun yFor(v: Float) = pad + (1f - (v - range.start) / (range.endInclusive - range.start)) * usable
                // Tick marks
                for (db in listOf(-12, -6, 0, 6, 12)) {
                    val y = yFor(db.toFloat())
                    val len = if (db == 0) 14.dp.toPx() else 8.dp.toPx()
                    drawLine(Tono.Outline, Offset(cx - trackW - len, y), Offset(cx - trackW - 3.dp.toPx(), y), 1.5f)
                    drawLine(Tono.Outline, Offset(cx + trackW + 3.dp.toPx(), y), Offset(cx + trackW + len, y), 1.5f)
                }
                // Track
                drawRoundRect(
                    Tono.SurfaceHigher, Offset(cx - trackW / 2, pad), Size(trackW, usable), CornerRadius(trackW),
                )
                // Active fill from 0 dB
                val zeroY = yFor(0f)
                val valY = yFor(shown)
                val top = minOf(zeroY, valY)
                drawRoundRect(
                    Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)), startY = top, endY = top + abs(zeroY - valY) + 1f),
                    Offset(cx - trackW / 2, top), Size(trackW, abs(zeroY - valY)), CornerRadius(trackW),
                )
                // Cap (fader knob)
                val capW = 40.dp.toPx()
                val capH = 22.dp.toPx()
                drawRoundRect(
                    color.copy(alpha = 0.18f + 0.22f * glow),
                    Offset(cx - capW / 2 - 6.dp.toPx() * glow, valY - capH / 2 - 6.dp.toPx() * glow),
                    Size(capW + 12.dp.toPx() * glow, capH + 12.dp.toPx() * glow), CornerRadius(14.dp.toPx()),
                )
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFF3A3F52), Color(0xFF232736)), startY = valY - capH / 2, endY = valY + capH / 2),
                    Offset(cx - capW / 2, valY - capH / 2), Size(capW, capH), CornerRadius(9.dp.toPx()),
                )
                drawLine(color, Offset(cx - capW / 2 + 8.dp.toPx(), valY), Offset(cx + capW / 2 - 8.dp.toPx(), valY),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Tono.TextPrimary)
        Text(sublabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tono.TextMuted)
    }
}

/**
 * Rotary knob (0..max) with a 270° arc. Drag up/right to increase, down/left to decrease.
 */
@Composable
fun RotaryKnob(
    value: Int,
    onValueChange: (Int) -> Unit,
    title: String,
    caption: String,
    brush: Brush,
    accent: Color,
    modifier: Modifier = Modifier,
    max: Int = 1000,
    knobSize: Dp = 112.dp,
    onDragEnd: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val onEnd by rememberUpdatedState(onDragEnd)
    var active by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(value.toFloat(), label = "knob")
    val glow by animateFloatAsState(if (active) 1f else 0f, label = "knobGlow")
    val percent = (value * 100f / max).roundToInt()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(knobSize)
                .semantics {
                    contentDescription = title
                    stateDescription = "$percent percent"
                    progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..max.toFloat(), 19)
                    setProgress { t -> onChange(t.roundToInt().coerceIn(0, max)); onEnd(); true }
                }
                .pointerInput(max) {
                    val sensitivity = max / (180.dp.toPx())
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        active = true
                        var acc = current.toFloat()
                        drag(down.id) { change ->
                            val d = change.position - change.previousPosition
                            change.consume()
                            acc = (acc + (d.x - d.y) * sensitivity).coerceIn(0f, max.toFloat())
                            val snapped = ((acc / 10f).roundToInt() * 10).coerceIn(0, max)
                            if (snapped != current) {
                                if (snapped / 100 != current / 100 || snapped == 0 || snapped == max) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onChange(snapped)
                            }
                        }
                        active = false
                        onEnd()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2 + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(Tono.SurfaceHigher, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = 270f * (shown / max)
                if (sweep > 0.5f) {
                    drawArc(brush, 135f, sweep, false, topLeft, arcSize, alpha = 0.25f + 0.2f * glow,
                        style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                    drawArc(brush, 135f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                // Knob body
                val r = size.minDimension / 2 - inset - stroke - 4.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color(0xFF2E3344), Color(0xFF161924)), center = center - Offset(r * 0.3f, r * 0.3f), radius = r * 1.6f), r)
                drawCircle(Tono.Outline, r, style = Stroke(1.dp.toPx()))
                // Indicator
                val angle = Math.toRadians((135.0 + 270.0 * (shown / max)))
                val inner = Offset(center.x + (r * 0.45f) * kotlin.math.cos(angle).toFloat(), center.y + (r * 0.45f) * kotlin.math.sin(angle).toFloat())
                val outer = Offset(center.x + (r * 0.82f) * kotlin.math.cos(angle).toFloat(), center.y + (r * 0.82f) * kotlin.math.sin(angle).toFloat())
                drawLine(accent, inner, outer, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
            Text(
                "$percent%",
                style = MaterialTheme.typography.titleMedium.merge(Numeric),
                color = Tono.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Tono.TextPrimary, textAlign = TextAlign.Center)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Tono.TextMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
    }
}

