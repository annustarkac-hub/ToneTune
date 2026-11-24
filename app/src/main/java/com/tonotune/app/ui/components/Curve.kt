package com.tonotune.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tonotune.app.audio.EqSettings
import com.tonotune.app.ui.theme.Tono

/** Builds a smooth Catmull-Rom path through evenly spaced band points. */
internal fun curvePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}

private fun DrawScope.bandPoints(values: List<Float>, rangeDb: Float, padX: Float, padY: Float): List<Offset> {
    val w = size.width - padX * 2
    val h = size.height - padY * 2
    // Extend flat to the edges so the curve spans the full width.
    val step = w / (values.size)
    return buildList {
        add(Offset(padX, padY + h / 2f - values.first() / rangeDb * h / 2f))
        values.forEachIndexed { i, v ->
            add(Offset(padX + step * (i + 0.5f), padY + h / 2f - v.coerceIn(-rangeDb, rangeDb) / rangeDb * h / 2f))
        }
        add(Offset(padX + w, padY + h / 2f - values.last() / rangeDb * h / 2f))
    }
}

@Composable
fun animatedBands(values: List<Float>): List<State<Float>> = values.map {
    animateFloatAsState(it, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "band")
}

/** Large glowing frequency-response readout used in the hero card. */
@Composable
fun FrequencyResponse(
    bands: List<Float>,
    modifier: Modifier = Modifier,
    rangeDb: Float = 12f,
    dimmed: Boolean = false,
) {
    val animated = animatedBands(bands)
    val alpha by lazyAlpha(dimmed)
    Canvas(
        modifier.semantics {
            contentDescription = "Frequency response: " + EqSettings.BAND_LABELS.zip(bands)
                .joinToString { (l, v) -> "$l ${"%+.1f".format(v)} dB" }
        },
    ) {
        val padY = size.height * 0.08f
        val values = animated.map { it.value }
        // Grid
        val gridColor = Tono.Outline.copy(alpha = 0.45f)
        for (db in listOf(-12f, -6f, 6f, 12f)) {
            val y = padY + (size.height - padY * 2) / 2f - db / rangeDb * (size.height - padY * 2) / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        }
        val zeroY = size.height / 2f
        drawLine(Tono.Outline, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)
        val step = size.width / values.size
        for (i in values.indices) {
            val x = step * (i + 0.5f)
            drawLine(gridColor, Offset(x, padY), Offset(x, size.height - padY), strokeWidth = 1f)
        }

        val pts = bandPoints(values, rangeDb, 0f, padY)
        val line = curvePath(pts)
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, zeroY)
            lineTo(0f, zeroY)
            close()
        }
        drawPath(fill, Brush.verticalGradient(
            listOf(Tono.Cyan.copy(alpha = 0.30f * alpha), Tono.Violet.copy(alpha = 0.05f * alpha), Tono.Violet.copy(alpha = 0.22f * alpha)),
        ))
        // Glow + line
        drawPath(line, Brush.horizontalGradient(listOf(Tono.Cyan, Tono.Violet)), alpha = 0.25f * alpha,
            style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, Brush.horizontalGradient(listOf(Tono.Cyan, Tono.Violet)), alpha = alpha,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Band nodes
        pts.subList(1, pts.size - 1).forEachIndexed { i, p ->
            val c = Tono.BandColors[i]
            drawCircle(c.copy(alpha = 0.25f * alpha), radius = 16f, center = p)
            drawCircle(c.copy(alpha = alpha), radius = 7f, center = p)
            drawCircle(Color.White.copy(alpha = alpha), radius = 3f, center = p)
        }
    }
}

@Composable
private fun lazyAlpha(dimmed: Boolean) =
    animateFloatAsState(if (dimmed) 0.35f else 1f, label = "curveAlpha")


/** Tiny curve preview for preset cards. */
@Composable
fun MiniCurve(bands: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pts = bandPoints(bands, 8f, 0f, size.height * 0.1f)
        val line = curvePath(pts)
        val zeroY = size.height / 2f
        val fill = Path().apply {
            addPath(line); lineTo(size.width, zeroY); lineTo(0f, zeroY); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(width = 2.2f * density, cap = StrokeCap.Round))
    }
}
