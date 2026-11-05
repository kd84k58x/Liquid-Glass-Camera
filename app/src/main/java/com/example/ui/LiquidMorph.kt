package com.example.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Liquid morph for floating glass menus (rebuilt after the reference screen recording, frame by frame).
 *
 * What the recording does, and what this file now does:
 *
 *  OPEN
 *   - an oval droplet, TALLER than wide, appears at the anchor and stretches (height first, then width).
 *   - the WHOLE menu is squeezed into the droplet: the labels are not cropped, they are scaled with the blob
 *     (squashed sideways at first), heavily blurred and faded. They sharpen while the blob settles.
 *   - the blob overshoots the final size by a few percent and springs back.
 *   - the corners start fully round (oval) and relax into the menu's own radius.
 *
 *  CLOSE
 *   - the labels blur out first (~100 ms), while the menu is still almost full size.
 *   - the menu pinches into a squircle, then into a BOTTLE: a heavy round body at the bottom and a thin neck
 *     reaching up to the anchor, then the body retracts through the neck and melts into the anchor.
 *   - closing is faster and has no overshoot.
 *
 * Why it stays cheap: the menu is laid out ONCE at its final size. Only a clip path, a scale and (while the
 * labels are not sharp yet) a blur are changed, all in graphics layers: no re-layout, no re-composition.
 *
 * The droplet that used to stay on screen below the trigger pill is fixed in two ways: the blob now shrinks to
 * NOTHING at progress 0 (it used to snap back to the 40x30dp seed and stay there), and the surface is removed
 * from the tree as soon as the close animation has really finished (`hidden`), not by guessing from a threshold.
 */
@Immutable
class LiquidMorphSpec(
    /** Size of the droplet the morph starts from / collapses back to. Taller than wide, like in the recording. */
    val seedSize: DpSize = DpSize(34.dp, 44.dp),
    /** Corner radius of the finished menu. Unspecified = full capsule (half of the height). */
    val expandedCorner: Dp = Dp.Unspecified,
    // Height is a little softer and starts first: the droplet gets TALL first and then widens.
    val openWidth: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.62f, stiffness = 380f),
    val openHeight: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.58f, stiffness = 340f),
    // Closing is quicker, tighter and does not overshoot: width pinches in first, height follows.
    val closeWidth: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.90f, stiffness = 820f),
    val closeHeight: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 560f),
    val openContent: FiniteAnimationSpec<Float> = tween(240, delayMillis = 90, easing = LinearOutSlowInEasing),
    val closeContent: FiniteAnimationSpec<Float> = tween(100),
    /** Blur of the content at the start of the open / end of the close (Android 12+ only). */
    val maxBlur: Dp = 16.dp,
    /** How much the shape squashes and stretches while the width and height are out of step. */
    val stretch: Float = 0.08f,
    /** How far the bottle neck reaches (fraction of the blob height) at its longest. 0 = no neck. */
    val neck: Float = 0.30f,
    /** Width of the glass rim that follows the moving outline. Very thin on purpose. */
    val rim: Dp = 0.35.dp
)

val DefaultLiquidMorphSpec = LiquidMorphSpec()

@Stable
class LiquidMorphState(expanded: Boolean) {
    val width = Animatable(if (expanded) 1f else 0f)
    val height = Animatable(if (expanded) 1f else 0f)
    val content = Animatable(if (expanded) 1f else 0f)
    var closing by mutableStateOf(!expanded)

    /** true only once a close animation has really finished: then the surface leaves the tree. */
    var hidden by mutableStateOf(!expanded)

    /** Springs are re-targeted from wherever they are, so a quick tap-tap never snaps. */
    suspend fun morphTo(expanded: Boolean, spec: LiquidMorphSpec) {
        closing = !expanded
        if (expanded) hidden = false
        val target = if (expanded) 1f else 0f
        coroutineScope {
            launch { width.animateTo(target, if (expanded) spec.openWidth else spec.closeWidth) }
            launch { height.animateTo(target, if (expanded) spec.openHeight else spec.closeHeight) }
            launch { content.animateTo(target, if (expanded) spec.openContent else spec.closeContent) }
        }
        // only reached when nothing re-targeted us: the blob is gone, so drop it from the tree
        if (!expanded) hidden = true
    }
}

private class BlobShape(private val path: Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(path)
}

private fun smooth01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Current width / height of the blob in px. It grows out of NOTHING (no snap to the seed size). */
private fun blobWidth(size: Size, pw: Float, seedW: Float): Float {
    val grow = smooth01(pw / 0.12f)
    return lerp(min(seedW, size.width) * grow, size.width, pw.coerceIn(0f, 1f))
}

private fun blobHeight(size: Size, ph: Float, seedH: Float): Float {
    val grow = smooth01(ph / 0.12f)
    return lerp(min(seedH, size.height) * grow, size.height, ph.coerceIn(0f, 1f))
}

private const val K = 0.5522847f   // circle -> cubic bezier constant

/**
 * Builds the outline of the blob into [path] (cleared first).
 *  - open: a rounded blob whose corners relax from "fully round" to the menu's radius, the side away from the
 *    anchor a little flatter (droplet).
 *  - close: a bottle. A heavy round body and, towards the anchor, a thin neck with soft (concave) shoulders.
 */
private fun buildBlobPath(
    path: Path,
    size: Size,
    pw: Float,
    ph: Float,
    closing: Boolean,
    seedW: Float,
    seedH: Float,
    anchorX: Float,
    anchorY: Float,
    expandedCornerPx: Float,
    neckFraction: Float
) {
    path.rewind()
    val bw = blobWidth(size, pw, seedW)
    val bh = blobHeight(size, ph, seedH)
    if (bw < 1f || bh < 1f) return           // nothing to show yet / any more

    val left = (size.width - bw) * anchorX
    val top = (size.height - bh) * anchorY
    val right = left + bw
    val bottom = top + bh
    val flip = anchorY > 0.5f                // anchor at the bottom edge: build upside down

    val p = min(pw, ph).coerceIn(0f, 1f)
    val menuRadius = if (expandedCornerPx.isNaN()) size.height / 2f else expandedCornerPx
    val blobRadius = min(bw, bh) / 2f
    val radius = min(lerp(blobRadius, menuRadius, smooth01(p)), blobRadius)
    val bias = 4f * p * (1f - p)

    // neck (closing only, strongest in the middle of the collapse)
    val neckAmt = if (closing && neckFraction > 0f) bias.pow(1.2f) else 0f
    val neckH = bh * neckFraction * neckAmt
    val hasNeck = neckH > 6f

    val bodyTop = top + if (hasNeck) neckH else 0f
    val bodyH = bottom - bodyTop

    val rTop = radius
    val rBot = if (closing) radius else radius * (1f - 0.45f * bias)
    val r1 = min(rTop, min(bw / 2f, bodyH / 2f))
    val r2 = min(rBot, min(bw / 2f, bodyH / 2f))

    // y is mapped so the same code builds the "anchor on top" and the "anchor at the bottom" blob
    fun yy(y: Float) = if (flip) 2f * top + bh - y else y
    fun mv(x: Float, y: Float) = path.moveTo(x, yy(y))
    fun ln(x: Float, y: Float) = path.lineTo(x, yy(y))
    fun cb(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        path.cubicTo(x1, yy(y1), x2, yy(y2), x3, yy(y3))
    fun qd(x1: Float, y1: Float, x2: Float, y2: Float) =
        path.quadraticBezierTo(x1, yy(y1), x2, yy(y2))

    mv(left + r1, bodyTop)

    if (hasNeck) {
        val nw = min(bw * 0.09f, neckH * 0.5f)                  // half width of the neck
        val f = min(nw * 1.2f, neckH - nw)                      // soft shoulder where neck meets body
        val lo = left + r1 + f + nw
        val hi = right - r1 - f - nw
        val nx = if (lo <= hi) (left + bw * anchorX).coerceIn(lo, hi) else left + bw / 2f
        ln(nx - nw - f, bodyTop)
        qd(nx - nw, bodyTop, nx - nw, bodyTop - f)
        ln(nx - nw, top + nw)
        cb(nx - nw, top + nw - K * nw, nx - K * nw, top, nx, top)
        cb(nx + K * nw, top, nx + nw, top + nw - K * nw, nx + nw, top + nw)
        ln(nx + nw, bodyTop - f)
        qd(nx + nw, bodyTop, nx + nw + f, bodyTop)
    }
    ln(right - r1, bodyTop)
    cb(right - r1 + K * r1, bodyTop, right, bodyTop + r1 - K * r1, right, bodyTop + r1)
    ln(right, bottom - r2)
    cb(right, bottom - r2 + K * r2, right - r2 + K * r2, bottom, right - r2, bottom)
    ln(left + r2, bottom)
    cb(left + r2 - K * r2, bottom, left, bottom - r2 + K * r2, left, bottom - r2)
    ln(left, bodyTop + r1)
    cb(left, bodyTop + r1 - K * r1, left + r1 - K * r1, bodyTop, left + r1, bodyTop)
    path.close()
}

/**
 * Wrap ONE floating glass pill / menu with this. The pill keeps its own glass; this adds the morph.
 *
 * @param expanded  true = menu open, false = back to the droplet and removed from the tree.
 * @param anchorX   0 = grow from the left edge, 0.5 = from the centre, 1 = from the right edge.
 * @param anchorY   0 = grow downward from the top edge, 1 = grow upward from the bottom edge.
 * @param content   receives a modifier to put on the INNER items (icons / labels), not on the glass,
 *                  so the glass stays solid while the items scale, blur and fade in.
 */
@Composable
fun LiquidMorphSurface(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    spec: LiquidMorphSpec = DefaultLiquidMorphSpec,
    anchorX: Float = 0.5f,
    anchorY: Float = 0f,
    content: @Composable BoxScope.(contentModifier: Modifier) -> Unit
) {
    val state = remember { LiquidMorphState(expanded) }
    LaunchedEffect(expanded) { state.morphTo(expanded, spec) }

    if (!expanded && state.hidden) return

    val density = LocalDensity.current
    val seedW = with(density) { spec.seedSize.width.toPx() }
    val seedH = with(density) { spec.seedSize.height.toPx() }
    val cornerPx = if (spec.expandedCorner == Dp.Unspecified) Float.NaN else with(density) { spec.expandedCorner.toPx() }
    val maxBlurPx = with(density) { spec.maxBlur.toPx() }
    // half of the stroke is cut off by the clip, so the stroke is drawn twice as wide as the rim that shows
    val rimStroke = with(density) { (spec.rim * 2f).toPx() }
    val clipPath = remember { Path() }
    val rimPath = remember { Path() }
    var surfaceSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { surfaceSize = Size(it.width.toFloat(), it.height.toFloat()) }
            // overshoot + squash & stretch around the anchor edge (not clipped, so the overshoot is visible)
            .graphicsLayer {
                val ow = (state.width.value - 1f).coerceAtLeast(0f)
                val oh = (state.height.value - 1f).coerceAtLeast(0f)
                val k = (state.height.value.coerceIn(0f, 1f) - state.width.value.coerceIn(0f, 1f)).coerceIn(-1f, 1f)
                val s = spec.stretch * k
                scaleX = (1f + ow) * (1f - s * 0.5f)
                scaleY = (1f + oh) * (1f + s)
                transformOrigin = TransformOrigin(anchorX, anchorY)
                // safety net: whatever happens to the state, a blob at progress ~0 is never visible
                val m = maxOf(state.width.value, state.height.value)
                alpha = if (m >= 0.06f) 1f else smooth01(m / 0.06f)
            }
            // the liquid outline itself
            .graphicsLayer {
                buildBlobPath(
                    clipPath, size, state.width.value, state.height.value, state.closing,
                    seedW, seedH, anchorX, anchorY, cornerPx, spec.neck
                )
                shape = BlobShape(clipPath)
                clip = true
            }
            // a very thin glass rim that follows the moving outline; it hands over to the glass's own rim at rest
            .drawWithContent {
                drawContent()
                val p = min(state.width.value, state.height.value).coerceIn(0f, 1f)
                val a = 1f - ((p - 0.9f) / 0.1f).coerceIn(0f, 1f)
                if (a > 0.01f) {
                    buildBlobPath(
                        rimPath, size, state.width.value, state.height.value, state.closing,
                        seedW, seedH, anchorX, anchorY, cornerPx, spec.neck
                    )
                    drawPath(
                        path = rimPath,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.75f * a),
                                Color.White.copy(alpha = 0.08f * a),
                                Color.White.copy(alpha = 0.08f * a),
                                Color.White.copy(alpha = 0.50f * a)
                            )
                        ),
                        style = Stroke(width = rimStroke)
                    )
                }
            }
    ) {
        val contentModifier = Modifier.graphicsLayer {
            val c = state.content.value.coerceIn(0f, 1f)
            alpha = c
            // the WHOLE menu is squeezed into the blob (like the recording), not cropped by it
            val sz = surfaceSize
            if (sz.width > 0f && sz.height > 0f) {
                val bw = blobWidth(sz, state.width.value, seedW)
                val bh = blobHeight(sz, state.height.value, seedH)
                scaleX = (bw / sz.width).coerceIn(0.05f, 1f)
                scaleY = (bh / sz.height).coerceIn(0.05f, 1f)
                transformOrigin = TransformOrigin(anchorX, anchorY)
            }
            renderEffect = null
            if (Build.VERSION.SDK_INT >= 31 && c < 0.97f && maxBlurPx > 0f) {
                // quantised so a new effect is only built every ~2px of blur
                val r = (((1f - c) * maxBlurPx) / 2f).roundToInt() * 2f
                if (r >= 2f) renderEffect = BlurEffect(r, r, TileMode.Decal)
            }
        }
        content(contentModifier)
    }
}
