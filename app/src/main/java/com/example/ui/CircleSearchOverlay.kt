package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private val TrailPalette = listOf(
    Color(0xFF4F9DFF), // blue
    Color(0xFFFF6FB5), // pink
    Color(0xFFFFD84D), // yellow
    Color(0xFF4F9DFF)  // back to blue so the colour loop is seamless
)

private fun trailColor(f: Float): Color {
    val x = ((f % 1f) + 1f) % 1f * (TrailPalette.size - 1)
    val i = x.toInt().coerceAtMost(TrailPalette.size - 2)
    return lerp(TrailPalette[i], TrailPalette[i + 1], x - i)
}

/**
 * Circle to search: draw a ring (or any mark) over the preview with one finger. While drawing and while the
 * search runs, the stroke is drawn as a thin glowing trail that cycles blue, pink and yellow.
 * Nothing here recomposes while drawing: points live in a snapshot list that is only read in the draw block.
 */
@Composable
fun CircleSearchOverlay(
    enabled: Boolean,
    showTrail: Boolean,
    onGestureStart: () -> Unit,
    onGestureCancel: () -> Unit,
    onCircled: (bounds: Rect, areaSize: IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = remember { mutableStateListOf<Offset>() }
    var drawing by remember { mutableStateOf(false) }
    // set when the mark is finished: the stroke loses its glow and four corner brackets frame the area
    var finalBounds by remember { mutableStateOf<Rect?>(null) }
    val hasPoints by remember { derivedStateOf { points.isNotEmpty() } }
    val visible = drawing || showTrail
    val alphaState = animateFloatAsState(
        targetValue = if (visible && hasPoints) 1f else 0f,
        animationSpec = tween(280),
        label = "CircleTrailAlpha"
    )
    val bracketsIn = animateFloatAsState(
        targetValue = if (finalBounds != null && visible) 1f else 0f,
        animationSpec = tween(260),
        label = "CircleBracketsIn"
    )
    LaunchedEffect(visible) {
        if (!visible) {
            delay(320)
            points.clear()
            finalBounds = null
        }
    }
    val transition = rememberInfiniteTransition(label = "CircleTrailFlow")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "CircleTrailPhase"
    )

    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val areaSize = size
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                val minStep = 4.dp.toPx()
                var started = false
                var last = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val pos = change.position
                    if (!started) {
                        if ((pos - down.position).getDistance() > slop) {
                            started = true
                            finalBounds = null
                            points.clear()
                            points.add(down.position)
                            drawing = true
                            onGestureStart()
                        }
                    }
                    if (started) {
                        if ((pos - last).getDistance() >= minStep) {
                            points.add(pos)
                            last = pos
                        }
                        change.consume()
                    }
                }
                if (started) {
                    drawing = false
                    var l = Float.MAX_VALUE
                    var t = Float.MAX_VALUE
                    var r = -Float.MAX_VALUE
                    var b = -Float.MAX_VALUE
                    for (p in points) {
                        l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y)
                    }
                    val minSide = 40.dp.toPx()
                    if (points.size < 6 || (r - l) < minSide || (b - t) < minSide) {
                        onGestureCancel()
                        points.clear()
                        finalBounds = null
                    } else {
                        // close the ring when the finger stopped near where it started
                        val first = points.first()
                        if ((points.last() - first).getDistance() < max(r - l, b - t) * 0.6f) points.add(first)
                        finalBounds = Rect(l, t, r, b)
                        onCircled(Rect(l, t, r, b), areaSize)
                    }
                }
            }
        }
    ) {
        val a = alphaState.value
        val n = points.size
        if (a < 0.01f || n < 2) return@Canvas
        val ph = phase.value
        val fb = finalBounds
        val finished = fb != null
        val glow = 22.dp.toPx()
        val mid = 11.dp.toPx()
        val core = 6.dp.toPx()      // thicker border
        val thin = 1.5.dp.toPx()
        for (i in 1 until n) {
            val f = i / (n - 1).toFloat()
            // older part of the stroke is a little softer, the head is brightest: a trail
            val trailA = a * (0.35f + 0.65f * f)
            val c = trailColor(f - ph)
            val p0 = points[i - 1]
            val p1 = points[i]
            if (!finished) {
                // soft glow only while drawing; it is gone once the mark is finished
                drawLine(c.copy(alpha = 0.16f * trailA), p0, p1, glow, StrokeCap.Round)
                drawLine(c.copy(alpha = 0.38f * trailA), p0, p1, mid, StrokeCap.Round)
            }
            drawLine(c.copy(alpha = 0.95f * trailA), p0, p1, core, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.85f * trailA), p0, p1, thin, StrokeCap.Round)
        }

        // Four corner brackets around the marked area, easing in from slightly outside
        val bIn = bracketsIn.value
        if (fb != null && bIn > 0.01f) {
            val pad = 14.dp.toPx() + (1f - bIn) * 16.dp.toPx()
            val l = fb.left - pad
            val t = fb.top - pad
            val r = fb.right + pad
            val b = fb.bottom + pad
            val len = min(30.dp.toPx(), min(r - l, b - t) / 3f)
            val rad = min(10.dp.toPx(), len / 2f)
            val sw = 4.5.dp.toPx()
            val col = Color.White.copy(alpha = (a * bIn).coerceIn(0f, 1f))
            fun corner(cx: Float, cy: Float, dx: Float, dy: Float) {
                val path = Path()
                path.moveTo(cx, cy + dy * len)
                path.lineTo(cx, cy + dy * rad)
                path.quadraticTo(cx, cy, cx + dx * rad, cy)
                path.lineTo(cx + dx * len, cy)
                drawPath(path, col, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            corner(l, t, 1f, 1f)
            corner(r, t, -1f, 1f)
            corner(l, b, 1f, -1f)
            corner(r, b, -1f, -1f)
        }
    }
}

/**
 * Crops the frozen camera frame to the circled area (with a little breathing room).
 * [bounds] are in overlay pixels; the frame is mapped with the same centre-crop the preview uses.
 */
fun cropForCircle(frame: Bitmap, bounds: Rect, area: IntSize): Bitmap? {
    if (area.width <= 0 || area.height <= 0 || frame.width <= 0 || frame.height <= 0) return null
    val scale = max(area.width.toFloat() / frame.width, area.height.toFloat() / frame.height)
    val dx = (area.width - frame.width * scale) / 2f
    val dy = (area.height - frame.height * scale) / 2f
    var l = (bounds.left - dx) / scale
    var t = (bounds.top - dy) / scale
    var r = (bounds.right - dx) / scale
    var b = (bounds.bottom - dy) / scale
    val padX = (r - l) * 0.12f
    val padY = (b - t) * 0.12f
    l -= padX; r += padX; t -= padY; b += padY
    val minSide = 96f
    if (r - l < minSide) { val c = (l + r) / 2f; l = c - minSide / 2f; r = c + minSide / 2f }
    if (b - t < minSide) { val c = (t + b) / 2f; t = c - minSide / 2f; b = c + minSide / 2f }
    val x = l.coerceIn(0f, frame.width - 1f).toInt()
    val y = t.coerceIn(0f, frame.height - 1f).toInt()
    val w = (r.coerceIn(0f, frame.width.toFloat()) - x).toInt().coerceAtLeast(1).coerceAtMost(frame.width - x)
    val h = (b.coerceIn(0f, frame.height.toFloat()) - y).toInt().coerceAtLeast(1).coerceAtMost(frame.height - y)
    return try {
        Bitmap.createBitmap(frame, x, y, w, h)
    } catch (e: Exception) {
        null
    }
}
