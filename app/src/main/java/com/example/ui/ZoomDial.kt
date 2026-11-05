package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private val DialYellow = Color(0xFFFFC83D)
private const val DIAL_RAD_PER_LN = 0.46f       // how far the dial turns per ln unit: bigger = ruler marks and labels further apart
private const val DIAL_MAX_ANGLE = 0.86f        // ticks further than this from the top are not drawn
private const val DIAL_RADIUS_FACTOR = 0.72f    // dial radius as a fraction of the screen width
private const val BASE_MM = 25.7f               // 35 mm equivalent focal length of the 1x camera
private const val DIAL_DRAG_GAIN = 8f          // zoom sensitivity of dragging the dial: higher = a short swipe zooms further (1 = the dial follows the finger exactly). Was 5, now 8 for a more responsive dial; raise it for even more, lower it for finer control

private class DialTick(val value: Float, val ln: Float, val kind: Int)   // kind: 0 minor, 1 medium, 2 major

private val FixedMajors = floatArrayOf(1f, 2f, 3f, 5f, 10f, 20f, 30f, 50f, 100f, 200f)

/** Values that get a number + focal length: fixed steps inside this phone's range, its ultra-wide end and its top end. */
private fun majorValues(minZoom: Float, maxZoom: Float): List<Float> {
    val m = ArrayList<Float>()
    // ultra-wide end: only phones that really zoom out below 1x have it
    if (minZoom < 0.95f) m.add(kotlin.math.ceil(minZoom * 10f - 0.001f) / 10f)
    for (v in FixedMajors) if (v >= minZoom - 0.001f && v <= maxZoom + 0.001f) m.add(v)
    // top end of the zoom range (for example 40x or 70x) when it is far enough from the last fixed label
    val top = kotlin.math.floor(maxZoom + 0.05f)
    val last = m.lastOrNull() ?: 1f
    if (top >= 3f && top > last * 1.3f) m.add(top)
    return m
}

private fun buildDialTicks(minZoom: Float, maxZoom: Float): List<DialTick> {
    val out = ArrayList<DialTick>()
    val majors = majorValues(minZoom, maxZoom)
    var v = ceil(max(0.1f, minZoom) * 10f - 0.001f) / 10f
    var guard = 0
    while (v <= maxZoom + 0.001f && guard++ < 800) {
        val major = majors.any { abs(it - v) < 0.001f }
        val medium = !major && v >= 1f && abs(v - v.roundToInt()) < 0.001f
        out.add(DialTick(v, ln(v), if (major) 2 else if (medium) 1 else 0))
        val step = when {
            v < 2f -> 0.1f
            v < 5f -> 0.2f
            v < 10f -> 0.5f
            v < 20f -> 1f
            v < 50f -> 2f
            else -> 5f
        }
        v = ((v + step) * 100f).roundToInt() / 100f
    }
    // labelled values that fall between two ruler marks (like the top end 57x) get their own mark
    for (m in majors) {
        if (out.none { abs(it.value - m) < 0.01f }) out.add(DialTick(m, ln(m), 2))
    }
    out.sortBy { it.value }
    return out
}

private fun labelFor(v: Float): String =
    if (v < 1f) String.format(Locale.US, "%.1f", v) else v.roundToInt().toString()

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The visible part of a big circle: only its top arc shows, the rest is cut off by the bottom of the area. */
private object DialShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = size.width * DIAL_RADIUS_FACTOR
        val cx = size.width / 2f
        val path = Path().apply { addOval(Rect(cx - r, 0f, cx + r, 2f * r)) }
        return Outline.Generic(path)
    }
}

/**
 * Modern zoom dial: a see-through half-circle (the same plain translucent black as the bottom container, no blur) with a ruler on its rim. The ruler is logarithmic, so every zoom step feels
 * the same. The dial turns smoothly under a yellow marker that shows the current zoom (for example 4.9x); the main
 * zoom levels carry a number and, below it, the matching focal length. Numbers fade as they pass under the marker.
 * Dragging the dial sideways zooms too, [DIAL_DRAG_GAIN] times faster than the finger moves. [onTouchChange] tells the
 * caller while a finger is on the dial, so it can keep the dial on screen. [zoom] is the real zoom ratio; show and hide it
 * from the caller.
 */
@Composable
fun ZoomDial(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onTouchChange: (Boolean) -> Unit = {}
) {
    val lo = max(0.1f, minZoom)
    val hi = max(lo, maxZoom)
    val ticks = remember(lo, hi) { buildDialTicks(lo, hi) }
    val textMeasurer = rememberTextMeasurer()
    val numberStyle = remember { TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    val mmStyle = remember { TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp) }
    val valueStyle = remember { TextStyle(color = DialYellow, fontSize = 18.sp, fontWeight = FontWeight.Bold) }

    // numbers and focal lengths are measured once, not every frame
    val labels = remember(ticks, textMeasurer) {
        ticks.filter { it.kind == 2 }.map { t ->
            Triple<DialTick, TextLayoutResult, TextLayoutResult>(
                t,
                textMeasurer.measure(labelFor(t.value), numberStyle),
                textMeasurer.measure("${(t.value * BASE_MM).roundToInt()} MM", mmStyle)
            )
        }
    }

    // the dial turns smoothly towards the real zoom (pinch updates and preset jumps both glide)
    val zoomLn = animateFloatAsState(
        targetValue = ln(zoom.coerceIn(lo, hi)),
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 900f),     // follows fast swipes closely
        label = "ZoomDialTurn"
    )
    val valueText by remember { derivedStateOf { String.format(Locale.US, "%.1f\u00D7", exp(zoomLn.value)) } }

    val zoomNow by rememberUpdatedState(zoom)
    val onDragNow by rememberUpdatedState(onZoomDrag)
    val onTouchNow by rememberUpdatedState(onTouchChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            // only the dial's own round shape takes touches; the corners beside the arc fall through to the viewfinder
            .clip(DialShape)
            .pointerInput(lo, hi) {
                val lnLo = ln(lo)
                val lnHi = ln(hi)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onTouchNow(true)                       // finger on the dial: the caller keeps it visible
                    try {
                        val pxPerLn = size.width * DIAL_RADIUS_FACTOR * DIAL_RAD_PER_LN / DIAL_DRAG_GAIN
                        // The zoom is accumulated here instead of being re-read from the state on every move, so no
                        // movement is lost when two moves arrive before the screen has recomposed.
                        var lnTarget = ln(zoomNow.coerceIn(lo, hi))
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            // dragging left brings higher zoom values under the marker
                            lnTarget = (lnTarget - over / pxPerLn).coerceIn(lnLo, lnHi)
                            onDragNow(exp(lnTarget).coerceIn(lo, hi))
                        }
                        if (slop != null) {
                            horizontalDrag(slop.id) { change ->
                                change.consume()
                                lnTarget = (lnTarget - change.positionChange().x / pxPerLn).coerceIn(lnLo, lnHi)
                                onDragNow(exp(lnTarget).coerceIn(lo, hi))
                            }
                        }
                    } finally {
                        onTouchNow(false)
                    }
                }
            }
    ) {
        // the same plain translucent black as the top / bottom containers (no blur, no edge lighting)
        TranslucentEdgeBar(isTop = false, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val r = w * DIAL_RADIUS_FACTOR
            val cx = w / 2f
            val cy = r                                   // circle centre; the top of the arc is at y = 0
            val lnNow = zoomLn.value
            val appear = 1f                              // no fade-in: the dial slides up fully drawn
            val spread = 1f
            val tickInset = 9.dp.toPx()
            val lens = floatArrayOf(8.dp.toPx(), 13.dp.toPx(), 20.dp.toPx())
            val widths = floatArrayOf(1.dp.toPx(), 1.3.dp.toPx(), 2.dp.toPx())
            val baseAlpha = floatArrayOf(0.50f, 0.72f, 0.95f)

            for (t in ticks) {
                val th = (t.ln - lnNow) * DIAL_RAD_PER_LN * spread
                val a = abs(th)
                if (a > DIAL_MAX_ANGLE) continue
                val fade = 1f - smooth(0.50f, DIAL_MAX_ANGLE, a)
                val alpha = baseAlpha[t.kind] * (0.2f + 0.8f * fade) * appear
                if (alpha < 0.02f) continue
                val s = sin(th)
                val c = cos(th)
                val r0 = r - tickInset
                val r1 = r0 - lens[t.kind]
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(cx + r0 * s, cy - r0 * c),
                    end = Offset(cx + r1 * s, cy - r1 * c),
                    strokeWidth = widths[t.kind],
                    cap = StrokeCap.Round
                )
            }

            // numbers with their focal length underneath, tilted along the arc
            for ((t, num, mm) in labels) {
                val th = (t.ln - lnNow) * DIAL_RAD_PER_LN * spread
                val a = abs(th)
                if (a > DIAL_MAX_ANGLE) continue
                val nearMarker = smooth(0.13f, 0.27f, a)         // fades as it slides under the yellow value
                val fade = 1f - smooth(0.50f, DIAL_MAX_ANGLE, a)
                val alpha = nearMarker * (0.2f + 0.8f * fade) * appear
                if (alpha < 0.02f) continue
                val s = sin(th)
                val c = cos(th)
                val deg = th * 180f / PI.toFloat()
                val rn = r - 46.dp.toPx()
                val pn = Offset(cx + rn * s, cy - rn * c)
                rotate(degrees = deg, pivot = pn) {
                    drawText(
                        textLayoutResult = num,
                        topLeft = Offset(pn.x - num.size.width / 2f, pn.y - num.size.height / 2f),
                        alpha = alpha
                    )
                }
                val rm = r - 66.dp.toPx()
                val pm = Offset(cx + rm * s, cy - rm * c)
                rotate(degrees = deg, pivot = pm) {
                    drawText(
                        textLayoutResult = mm,
                        topLeft = Offset(pm.x - mm.size.width / 2f, pm.y - mm.size.height / 2f),
                        alpha = alpha * 0.55f
                    )
                }
            }

            // yellow marker and the current zoom
            val tri = Path().apply {
                moveTo(cx - 6.dp.toPx(), 4.dp.toPx())
                lineTo(cx + 6.dp.toPx(), 4.dp.toPx())
                lineTo(cx, 15.dp.toPx())
                close()
            }
            drawPath(tri, DialYellow.copy(alpha = appear))
            val value = textMeasurer.measure(valueText, valueStyle)
            drawText(
                textLayoutResult = value,
                topLeft = Offset(cx - value.size.width / 2f, 36.dp.toPx()),
                alpha = appear
            )
        }
    }
}
