package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val FocusYellow = Color(0xFFFFD60A)
private val FocusBoxSize = 78.dp           // the frame (four corner brackets) drawn around the tapped spot
private val ExposureTrackHalf = 70.dp      // the exposure line reaches this far above and below the middle of the box
private val ExposureSideGap = 24.dp        // space between the box and the exposure line
private val ExposureHitWidth = 56.dp       // width of the area that takes the finger for the exposure slider
private val ExposureHitPad = 22.dp         // extra touch area above and below the line

/**
 * The tap-to-focus box (the iPhone look): a yellow square that pops in around the spot you tapped, with a small sun
 * on a thin vertical line next to it. Drag the sun up to make the picture brighter, down to make it darker.
 *
 * The box only draws. The caller does the real focusing and exposure ([onExposureChange] gives the new value) and
 * decides when it is shown ([visible]), so it can keep the box on screen while the finger is on the sun
 * ([onTouchChange]).
 *
 * @param point the tapped spot in pixels, relative to this overlay (keep it after the box has faded, so the
 *        fade-out can finish)
 * @param tapCount goes up by one on every tap; this restarts the pop-in animation
 * @param exposure -1 (darkest) .. 1 (brightest), 0 = normal exposure
 * @param showExposure false hides the sun (phone without exposure control, or Pro mode with its own brightness)
 */
@Composable
fun FocusExposureOverlay(
    point: Offset?,
    tapCount: Int,
    visible: Boolean,
    exposure: Float,
    showExposure: Boolean,
    onExposureChange: (Float) -> Unit,
    onTouchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // the box pops in big and settles to its size
    val scale = remember { Animatable(1f) }
    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            scale.snapTo(1.35f)
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
    }

    // after a moment without any touch the box dims a little, like on the iPhone
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(tapCount, exposure) {
        dimmed = false
        delay(1400)
        dimmed = true
    }
    val boxAlpha = animateFloatAsState(
        targetValue = if (dimmed) 0.6f else 1f,
        animationSpec = tween(350),
        label = "FocusBoxDim"
    )

    val exposureNow by rememberUpdatedState(exposure)
    val onChangeNow by rememberUpdatedState(onExposureChange)
    val onTouchNow by rememberUpdatedState(onTouchChange)

    BoxWithConstraints(modifier = modifier) {
        val p = point
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        AnimatedVisibility(
            visible = visible && p != null,
            enter = fadeIn(tween(80)),
            exit = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (p != null) {
                val half = with(density) { FocusBoxSize.toPx() } / 2f
                val gapPx = with(density) { ExposureSideGap.toPx() }
                val trackPx = with(density) { ExposureTrackHalf.toPx() }
                val hitWidthPx = with(density) { ExposureHitWidth.toPx() }
                val hitPadPx = with(density) { ExposureHitPad.toPx() }

                // keep the box fully on the viewfinder when the tap was close to an edge
                val cx = p.x.coerceIn(half, (widthPx - half).coerceAtLeast(half))
                val cy = p.y.coerceIn(half, (heightPx - half).coerceAtLeast(half))
                // the exposure line goes to the other side when there is no room on the right
                val onLeft = cx + half + gapPx + hitWidthPx / 2f > widthPx
                val lineX = if (onLeft) cx - half - gapPx else cx + half + gapPx

                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
                            .size(FocusBoxSize)
                            .graphicsLayer {
                                val s = scale.value
                                scaleX = s
                                scaleY = s
                                alpha = boxAlpha.value
                            }
                    ) {
                        drawFocusBox()
                    }

                    if (showExposure) {
                        val hitHeightPx = trackPx * 2f + hitPadPx * 2f
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (lineX - hitWidthPx / 2f).roundToInt(),
                                        (cy - hitHeightPx / 2f).roundToInt()
                                    )
                                }
                                .size(
                                    width = with(density) { hitWidthPx.toDp() },
                                    height = with(density) { hitHeightPx.toDp() }
                                )
                                .pointerInput(Unit) {
                                    val track = ExposureTrackHalf.toPx()
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        onTouchNow(true)           // finger on the sun: keep the box on screen
                                        try {
                                            // accumulated here, so no movement is lost between recompositions
                                            var value = exposureNow
                                            val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                                                change.consume()
                                                // dragging up (negative y) = brighter
                                                value = (value - over / track).coerceIn(-1f, 1f)
                                                onChangeNow(value)
                                            }
                                            if (slop != null) {
                                                verticalDrag(slop.id) { change ->
                                                    change.consume()
                                                    value = (value - change.positionChange().y / track).coerceIn(-1f, 1f)
                                                    onChangeNow(value)
                                                }
                                            }
                                        } finally {
                                            onTouchNow(false)
                                        }
                                    }
                                }
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = boxAlpha.value }
                            ) {
                                drawExposureLine(exposure = exposure, trackHalfPx = trackPx, padPx = hitPadPx)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The focus frame: four yellow corner brackets (like the reference camera), with a soft dark outline for bright scenes. */
private fun DrawScope.drawFocusBox() {
    val stroke = 2.dp.toPx()
    val inset = stroke
    val arm = size.width * 0.22f                       // length of each bracket arm
    val l = inset
    val t = inset
    val r = size.width - inset
    val b = size.height - inset

    fun brackets(color: Color, width: Float) {
        // top-left
        drawLine(color, Offset(l, t), Offset(l + arm, t), strokeWidth = width, cap = StrokeCap.Square)
        drawLine(color, Offset(l, t), Offset(l, t + arm), strokeWidth = width, cap = StrokeCap.Square)
        // top-right
        drawLine(color, Offset(r, t), Offset(r - arm, t), strokeWidth = width, cap = StrokeCap.Square)
        drawLine(color, Offset(r, t), Offset(r, t + arm), strokeWidth = width, cap = StrokeCap.Square)
        // bottom-left
        drawLine(color, Offset(l, b), Offset(l + arm, b), strokeWidth = width, cap = StrokeCap.Square)
        drawLine(color, Offset(l, b), Offset(l, b - arm), strokeWidth = width, cap = StrokeCap.Square)
        // bottom-right
        drawLine(color, Offset(r, b), Offset(r - arm, b), strokeWidth = width, cap = StrokeCap.Square)
        drawLine(color, Offset(r, b), Offset(r, b - arm), strokeWidth = width, cap = StrokeCap.Square)
    }
    brackets(Color.Black.copy(alpha = 0.28f), stroke + 1.6.dp.toPx())
    brackets(FocusYellow, stroke)
}

/**
 * The thin vertical line with the sun on it. The line is broken around the sun. [exposure] -1..1 moves the sun
 * from the bottom to the top end of the line.
 */
private fun DrawScope.drawExposureLine(exposure: Float, trackHalfPx: Float, padPx: Float) {
    val x = size.width / 2f
    val midY = size.height / 2f
    val sunY = midY - exposure.coerceIn(-1f, 1f) * trackHalfPx
    val lineTop = padPx
    val lineBottom = size.height - padPx
    val line = 1.6.dp.toPx()

    val sunR = 5.5.dp.toPx()
    val rayFrom = sunR + 3.5.dp.toPx()
    val rayTo = sunR + 7.dp.toPx()
    val gap = rayTo + 3.dp.toPx()          // the line stops this far from the middle of the sun

    fun segment(y0: Float, y1: Float) {
        if (y1 - y0 < 2f) return
        drawLine(Color.Black.copy(alpha = 0.28f), Offset(x, y0), Offset(x, y1), strokeWidth = line + 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(FocusYellow.copy(alpha = 0.75f), Offset(x, y0), Offset(x, y1), strokeWidth = line, cap = StrokeCap.Round)
    }
    segment(lineTop, sunY - gap)
    segment(sunY + gap, lineBottom)

    val c = Offset(x, sunY)
    drawCircle(Color.Black.copy(alpha = 0.28f), radius = sunR + 1.dp.toPx(), center = c)
    drawCircle(FocusYellow, radius = sunR, center = c)
    for (i in 0 until 8) {
        val a = i * (PI / 4.0).toFloat()
        val dx = cos(a)
        val dy = sin(a)
        drawLine(
            color = FocusYellow,
            start = Offset(c.x + dx * rayFrom, c.y + dy * rayFrom),
            end = Offset(c.x + dx * rayTo, c.y + dy * rayTo),
            strokeWidth = line,
            cap = StrokeCap.Round
        )
    }
}
