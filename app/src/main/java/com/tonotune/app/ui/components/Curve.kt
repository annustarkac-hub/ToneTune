package com.tonotune.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.ui.theme.Tono

/** Smooth Catmull-Rom path through the points. */
internal fun curvePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    return path
}

/** Animated frequency-response curve of what the engine outputs. */
@Composable
fun FrequencyResponse(
    bands: List<Float>,
    modifier: Modifier = Modifier,
    rangeDb: Float = 12f,
    dimmed: Boolean = false,
) {
    val animated = bands.map {
        animateFloatAsState(it, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "band")
    }
    val alpha by animateFloatAsState(if (dimmed) 0.3f else 1f, label = "curveAlpha")
    Canvas(
        modifier.semantics {
            contentDescription = "Response curve: " + EqSettings.BAND_LABELS.zip(bands)
                .joinToString { (l, v) -> "$l ${"%+.1f".format(v)} dB" }
        },
    ) {
        val padY = size.height * 0.1f
        val h = size.height - padY * 2
        val zeroY = size.height / 2f
        val step = size.width / bands.size
        // Zero line + band guides
        drawLine(Tono.Outline, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
        for (i in bands.indices) {
            val x = step * (i + 0.5f)
            drawLine(Tono.Outline.copy(alpha = 0.5f), Offset(x, padY), Offset(x, size.height - padY), strokeWidth = 1f)
        }
        val values = animated.map { it.value.coerceIn(-rangeDb, rangeDb) }
        fun y(v: Float) = padY + h / 2f - v / rangeDb * h / 2f
        val w = size.width
        val pts = buildList {
            add(Offset(0f, y(values.first())))
            values.forEachIndexed { i, v -> add(Offset(step * (i + 0.5f), y(v))) }
            add(Offset(w, y(values.last())))
        }
        val line = curvePath(pts)
        val fill = Path().apply { addPath(line); lineTo(size.width, zeroY); lineTo(0f, zeroY); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Tono.Accent.copy(alpha = 0.18f * alpha), Tono.Accent.copy(alpha = 0f))))
        drawPath(
            line, Brush.horizontalGradient(listOf(Tono.Accent, Tono.Violet)), alpha = alpha,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        pts.subList(1, pts.size - 1).forEach { p ->
            drawCircle(Tono.Bg, radius = 4.5.dp.toPx(), center = p)
            drawCircle(Tono.Accent.copy(alpha = alpha), radius = 3.dp.toPx(), center = p)
        }
    }
}

