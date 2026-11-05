package com.example.ui

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlinx.coroutines.awaitCancellation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.ai.GeminiModel
import com.example.ai.GeminiModelSelection
import com.example.ai.GeminiSearchResult
import com.example.ai.GeminiSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class WatermarkProfile(val label: String, val displayText: String) {
    OFF("Off", ""),
    AI_CAMERA("AI Cam", "AI Camera"),
    SHOT_ON("Shot On", "Shot on Phone"),
    DATETIME("Time", "")
}

/** How strongly a pinch zooms: 1 = the zoom follows the finger distance exactly, higher = a smaller pinch zooms further. */
private const val PINCH_ZOOM_GAIN = 1.5f

/** The zoom dial and the bottom container fade in and out over this long, both driven by one shared value. */
private const val ZOOM_DIAL_FADE_MS = 240

enum class CameraMode(val title: String) {
    VIDEO("VIDEO"),
    PHOTO("PHOTO"),
    PRO("PRO"),
    PANORAMA("PANO"),
    SEARCH("SEARCH")
}

enum class ColorFilterProfile(
    val title: String,
    val brush: Brush,
    val matrixValues: FloatArray?
) {
    ORIGINAL(
        title = "Normal",
        brush = Brush.linearGradient(listOf(Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFFFB74D))),
        matrixValues = null
    ),
    WARM(
        title = "Warm",
        brush = Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFF5722))),
        matrixValues = floatArrayOf(
            1.18f, 0.05f, 0.00f, 0f, 12f,
            0.00f, 1.08f, 0.00f, 0f, 6f,
            0.00f, 0.00f, 0.88f, 0f, -8f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),
    VINTAGE(
        title = "Vintage",
        brush = Brush.linearGradient(listOf(Color(0xFFD7CCC8), Color(0xFF8D6E63), Color(0xFF4E342E))),
        matrixValues = floatArrayOf(
            0.90f, 0.15f, 0.10f, 0f, 10f,
            0.05f, 0.85f, 0.10f, 0f, 12f,
            0.10f, 0.10f, 0.70f, 0f, 18f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    MONO(
        title = "Mono",
        brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFF424242))),
        matrixValues = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    VIVID(
        title = "Vivid",
        brush = Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFFFF7700), Color(0xFF00E5FF))),
        matrixValues = floatArrayOf(
            1.35f, -0.15f, -0.15f, 0f, 10f,
            -0.15f, 1.35f, -0.15f, 0f, 10f,
            -0.15f, -0.15f, 1.35f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CINEMATIC(
        title = "Cinematic",
        brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF6D00))),
        matrixValues = floatArrayOf(
            1.15f, 0.00f, -0.05f, 0f, 8f,
            0.00f, 1.05f, 0.05f, 0f, 0f,
            -0.05f, 0.10f, 1.20f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    SEPIA(
        title = "Sepia",
        brush = Brush.linearGradient(listOf(Color(0xFFE0A96D), Color(0xFF704214))),
        matrixValues = floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    CYBER(
        title = "Cyber",
        brush = Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF))),
        matrixValues = floatArrayOf(
            0.80f, 0.00f, 0.30f, 0f, 10f,
            0.00f, 0.90f, 0.20f, 0f, -5f,
            0.20f, 0.10f, 1.35f, 0f, 25f,
            0f, 0f, 0f, 1f, 0f
        )
    ),
    EMERALD(
        title = "Emerald",
        brush = Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF1B5E20))),
        matrixValues = floatArrayOf(
            0.85f, 0.05f, 0.05f, 0f, -4f,
            0.05f, 1.25f, 0.05f, 0f, 12f,
            0.05f, 0.10f, 0.90f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

val LocalBouncyEnabled = staticCompositionLocalOf { true }

/**
 * Holds a small, frequently refreshed snapshot of the live camera preview.
 * LiquidGlassContainer draws this snapshot (blurred) behind itself, which gives a real
 * "frosted glass" effect instead of a see-through dark overlay.
 *
 * Compose can't blur what is *behind* a composable, and the CameraX PreviewView is an
 * Android View, so we grab a tiny bitmap from its TextureView instead.
 */
@Stable
class CameraBackdrop {
    // Plain (non-state) field: swapping buffers must NOT recompose every glass container.
    // Only `frame` (read in the draw phase) is observed, so each snapshot just redraws.
    @Volatile
    var image: ImageBitmap? = null
    var ready by mutableStateOf(false)             // true once the first snapshot exists (composition read)
    var frame by mutableStateOf(0)                 // bumped on each new snapshot (draw-phase read only)
    var origin by mutableStateOf(Offset.Zero)      // top-left of the preview, in root coordinates
    var size by mutableStateOf(Size.Zero)          // size of the preview, in px
}

val LocalCameraBackdrop = staticCompositionLocalOf<CameraBackdrop?> { null }
val LocalGlassBlurRadius = compositionLocalOf { 28.dp }

private fun findTextureView(v: View?): TextureView? {
    if (v == null) return null
    if (v is TextureView) return v
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            findTextureView(v.getChildAt(i))?.let { return it }
        }
    }
    return null
}

/** One separable box-blur pass over an ARGB pixel array (clamped edges). */
private fun boxBlurPass(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val stride = if (horizontal) 1 else w
    val div = 2 * r + 1
    for (l in 0 until lines) {
        val base = if (horizontal) l * w else l
        var sr = 0
        var sg = 0
        var sb = 0
        for (k in -r..r) {
            val p = inp[base + k.coerceIn(0, len - 1) * stride]
            sr += (p shr 16) and 255
            sg += (p shr 8) and 255
            sb += p and 255
        }
        for (i in 0 until len) {
            out[base + i * stride] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = inp[base + (i + r + 1).coerceAtMost(len - 1) * stride]
            val rem = inp[base + (i - r).coerceAtLeast(0) * stride]
            sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
            sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
            sb += (add and 255) - (rem and 255)
        }
    }
}

/** Blur snapshot tuning. A shorter interval = fresher blur, but more work on the UI thread. */
private const val BACKDROP_INTERVAL_MS = 40L              // normal: a new blurred snapshot about 25 times a second
private const val BACKDROP_HOLD_INTERVAL_MS = 130L        // while a mode switch animates: still refreshed, just less often
private const val BACKDROP_LOW_POWER_INTERVAL_MS = 150L   // recording / burst shooting / panorama
private const val BACKDROP_GLIDE_MIN_NS = 30_000_000L     // the blur glides from one snapshot to the next in the time between them,
private const val BACKDROP_GLIDE_MAX_NS = 160_000_000L    // but never faster or slower than this
private const val BACKDROP_PUBLISH_GAP_NS = 14_000_000L   // at most ~60 redraws a second, even on 90 / 120 Hz screens

/**
 * Averages every 2x2 block of [src] (row length [srcW]) into [dst] ([w] x [h]).
 * Done before the blur, so the tiny snapshot is not aliased (aliasing is what makes fine detail shimmer).
 */
private fun downsample2x(src: IntArray, srcW: Int, dst: IntArray, w: Int, h: Int) {
    for (y in 0 until h) {
        val row0 = 2 * y * srcW
        val row1 = row0 + srcW
        for (x in 0 until w) {
            val i = 2 * x
            val p0 = src[row0 + i]
            val p1 = src[row0 + i + 1]
            val p2 = src[row1 + i]
            val p3 = src[row1 + i + 1]
            val r = (((p0 shr 16) and 255) + ((p1 shr 16) and 255) + ((p2 shr 16) and 255) + ((p3 shr 16) and 255)) shr 2
            val g = (((p0 shr 8) and 255) + ((p1 shr 8) and 255) + ((p2 shr 8) and 255) + ((p3 shr 8) and 255)) shr 2
            val b = ((p0 and 255) + (p1 and 255) + (p2 and 255) + (p3 and 255)) shr 2
            dst[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

/** Nearest-neighbour resize of a pixel grid (used when the preview changes shape, so the blur on screen does not jump). */
private fun resampleNearest(src: IntArray, srcW: Int, srcH: Int, dst: IntArray, dstW: Int, dstH: Int) {
    for (y in 0 until dstH) {
        val sy = (y * srcH / dstH).coerceIn(0, srcH - 1)
        for (x in 0 until dstW) {
            val sx = (x * srcW / dstW).coerceIn(0, srcW - 1)
            dst[y * dstW + x] = src[sy * srcW + sx]
        }
    }
}

/**
 * Glides the blurred picture that is on screen from one snapshot to the next, so the blur moves smoothly instead of
 * ticking a few times a second. Every new snapshot starts a glide from exactly what is on screen right now (so nothing
 * ever jumps), and the glide takes as long as the gap between two snapshots. Main thread only.
 */
private class BackdropSmoother {
    var width = 0
        private set
    var height = 0
        private set

    /** The pixels that are on screen right now (ARGB, always opaque). */
    var shown = IntArray(0)
        private set

    val isGliding: Boolean get() = gliding

    private var startPx = IntArray(0)       // where the current glide began
    private var endPx = IntArray(0)         // the newest blurred snapshot, where the glide is heading
    private var startNs = 0L
    private var durationNs = BACKDROP_GLIDE_MIN_NS
    private var lastSubmitNs = 0L
    private var gliding = false

    /** Takes a new blurred snapshot. Returns true for the very first one: there is nothing to glide from, so show it at once. */
    fun submit(px: IntArray, w: Int, h: Int, nowNs: Long): Boolean {
        val n = w * h
        if (shown.isEmpty()) {
            width = w
            height = h
            shown = px.copyOf(n)
            startPx = px.copyOf(n)
            endPx = px.copyOf(n)
            lastSubmitNs = nowNs
            gliding = false
            return true
        }
        if (w != width || h != height) {
            // The preview changed shape (aspect-ratio animation): carry what is on screen over to the new grid.
            val carried = IntArray(n)
            resampleNearest(shown, width, height, carried, w, h)
            shown = carried
            startPx = carried.copyOf()
            endPx = IntArray(n)
            width = w
            height = h
        } else {
            System.arraycopy(shown, 0, startPx, 0, n)
        }
        System.arraycopy(px, 0, endPx, 0, n)
        durationNs = (nowNs - lastSubmitNs).coerceIn(BACKDROP_GLIDE_MIN_NS, BACKDROP_GLIDE_MAX_NS)
        lastSubmitNs = nowNs
        startNs = nowNs
        gliding = true
        return false
    }

    /** Moves the on-screen pixels one step along the glide. Returns true when at least one pixel changed. */
    fun step(nowNs: Long): Boolean {
        if (!gliding) return false
        val t = ((nowNs - startNs).toFloat() / durationNs.toFloat()).coerceIn(0f, 1f)
        val f = (t * 256f).toInt()
        var changed = false
        val out = shown
        val from = startPx
        val dest = endPx
        for (i in out.indices) {
            val pa = from[i]
            val pb = dest[i]
            val v = if (f >= 256 || pa == pb) {
                pb
            } else {
                val ra = (pa shr 16) and 255
                val ga = (pa shr 8) and 255
                val ba = pa and 255
                val r = ra + ((((pb shr 16) and 255) - ra) * f) / 256
                val g = ga + ((((pb shr 8) and 255) - ga) * f) / 256
                val bl = ba + (((pb and 255) - ba) * f) / 256
                (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
            if (v != out[i]) {
                out[i] = v
                changed = true
            }
        }
        if (t >= 1f) gliding = false
        return changed
    }
}

/**
 * Keeps a small, blurred, always-fresh picture of the live camera preview in [backdrop] for the glass controls.
 *
 *  - Capture: ~25 times a second the preview's TextureView is copied into a tiny bitmap (at 1/8 size, then averaged
 *    down to 1/16 so it is not aliased) and blurred on a background thread (works on every Android version).
 *  - Smoothing: the picture on screen does not jump from one snapshot to the next. It glides between them, one step
 *    per screen frame (BackdropSmoother), so the blur moves like the real preview instead of ticking.
 *  - While a mode switch animates it keeps refreshing, just less often, instead of freezing, so the glass never lags.
 */
@Composable
fun BackdropCaptureEffect(
    previewView: PreviewView?,
    backdrop: CameraBackdrop,
    enabled: Boolean,
    blurIntensity: Float = 28f,
    // While true the last snapshot is kept as-is (used while the preview is frozen for a Gemini scan,
    // so the glass UI keeps matching the frozen frame instead of the live camera behind it).
    paused: Boolean = false,
    // Refresh less often while the device is already busy (recording, burst shooting).
    lowPower: Boolean = false,
    // Refresh less often while this is true (used during mode-switch animations). Nothing is recomposed by it.
    holdProvider: () -> Boolean = { false }
) {
    val holdState = rememberUpdatedState(holdProvider)
    val pausedState = rememberUpdatedState(paused)
    val lowPowerState = rememberUpdatedState(lowPower)
    LaunchedEffect(previewView, enabled, blurIntensity) {
        val pv = previewView
        if (!enabled || pv == null) {
            backdrop.ready = false
            backdrop.image = null
            return@LaunchedEffect
        }
        val passes = ((blurIntensity / 10f).toInt()).coerceIn(2, 5)
        val boxRadius = ((blurIntensity / 8f).toInt()).coerceIn(2, 6)
        val smoother = BackdropSmoother()
        // Wakes the presenting loop when a new snapshot arrives (it sleeps while nothing is moving).
        val wake = Channel<Unit>(Channel.CONFLATED)

        // Three bitmaps take turns being the one on screen: the bitmap being drawn is never the one being rewritten
        // (writing into a bitmap the render thread is still reading is what makes glass flicker).
        val shownBitmaps = arrayOfNulls<Bitmap>(3)
        val shownImages = arrayOfNulls<ImageBitmap>(3)
        var shownIdx = 0
        fun publish() {
            val w = smoother.width
            val h = smoother.height
            shownIdx = (shownIdx + 1) % 3
            val existing = shownBitmaps[shownIdx]
            val bmp = if (existing != null && existing.width == w && existing.height == h) {
                existing
            } else {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    shownBitmaps[shownIdx] = it
                    shownImages[shownIdx] = it.asImageBitmap()
                }
            }
            bmp.setPixels(smoother.shown, 0, w, 0, 0, w, h)
            backdrop.image = shownImages[shownIdx]
            if (!backdrop.ready) backdrop.ready = true
            backdrop.frame++
        }

        // ---- capture: copy the preview, shrink it, blur it (off the UI thread), hand the result to the smoother ----
        launch {
            var captureBitmap: Bitmap? = null
            var raw = IntArray(0)
            var px = IntArray(0)
            var scratch = IntArray(0)
            var cachedTv: TextureView? = null
            while (isActive) {
                if (pausedState.value) {
                    delay(60)
                    continue
                }
                val held = holdState.value()
                val loopStartNs = System.nanoTime()
                var tv = cachedTv
                if (tv == null || !tv.isAttachedToWindow) {
                    tv = findTextureView(pv)
                    cachedTv = tv
                }
                if (tv != null && tv.isAvailable && tv.width > 0 && tv.height > 0) {
                    val cw = (tv.width / 8).coerceAtLeast(32)
                    val ch = (tv.height / 8).coerceAtLeast(32)
                    val gw = cw / 2
                    val gh = ch / 2
                    val cb = captureBitmap?.takeIf { it.width == cw && it.height == ch }
                        ?: Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888).also {
                            captureBitmap = it
                            raw = IntArray(cw * ch)
                        }
                    if (px.size != gw * gh) {
                        px = IntArray(gw * gh)
                        scratch = IntArray(gw * gh)
                    }
                    try {
                        tv.getBitmap(cb)
                        val rawPx = raw
                        val outPx = px
                        val tmpPx = scratch
                        withContext(Dispatchers.Default) {
                            cb.getPixels(rawPx, 0, cw, 0, 0, cw, ch)
                            downsample2x(rawPx, cw, outPx, gw, gh)
                            repeat(passes) {
                                boxBlurPass(outPx, tmpPx, gw, gh, boxRadius, true)
                                boxBlurPass(tmpPx, outPx, gw, gh, boxRadius, false)
                            }
                        }
                        val first = smoother.submit(outPx, gw, gh, System.nanoTime())
                        if (first) publish()
                        wake.trySend(Unit)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                // getBitmap() runs on the UI thread, so keep its duty cycle low: back off further if a snapshot
                // turns out to be slow on this device.
                val workMs = (System.nanoTime() - loopStartNs) / 1_000_000L
                val baseInterval = when {
                    lowPowerState.value -> BACKDROP_LOW_POWER_INTERVAL_MS
                    held -> BACKDROP_HOLD_INTERVAL_MS
                    else -> BACKDROP_INTERVAL_MS
                }
                delay(maxOf(baseInterval, workMs * 6L))
            }
        }

        // ---- present: once per screen frame, glide the picture on screen one step further ----
        launch {
            var lastPublishNs = 0L
            while (isActive) {
                if (!smoother.isGliding) wake.receive()      // nothing is moving: sleep until the next snapshot arrives
                val now = withFrameNanos { it }
                if (now - lastPublishNs < BACKDROP_PUBLISH_GAP_NS) continue
                if (smoother.step(now)) {
                    publish()
                    lastPublishNs = now
                }
            }
        }
    }
}

/**
 * Entry point of the app. The first time the app runs (or after its data is cleared) it shows the one-time
 * setup from FirstRunSetup.kt (device name + automatic camera set-up), then the camera itself.
 */
@Composable
fun LiquidCameraScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }
    // Done only when it ran with the current version of the setup (SETUP_VERSION, see FirstRunSetup.kt): a phone that
    // finished an older setup runs the new Gemini setup once.
    var setupDone by remember {
        mutableStateOf(prefs.getBoolean("setup_done", false) && prefs.getInt("setup_version", 0) >= SETUP_VERSION)
    }
    if (!setupDone) {
        FirstRunSetupScreen(onFinished = { setupDone = true })
    } else {
        LiquidCameraMain(onRerunSetup = { setupDone = false })
    }
}

@Composable
private fun LiquidCameraMain(onRerunSetup: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }

    val uiColor = Color.White

    var blurIntensity by remember {
        mutableFloatStateOf(prefs.getFloat("blur_intensity", 28f))
    }

    // Glass blur on/off (Settings > General). Off = the floating controls get a plain dark fill and the camera
    // snapshot that feeds the blur is not captured at all, which also saves battery.
    var isBlurEnabled by remember {
        mutableStateOf(prefs.getBoolean("blur_enabled", true))
    }

    // Horizon lock is an experimental feature: it only exists in the camera while the Experimental lab is unlocked.
    var isExperimentalUnlocked by remember {
        mutableStateOf(prefs.getBoolean("experimental_unlocked", false))
    }

    var isHapticsEnabled by remember {
        mutableStateOf(prefs.getBoolean("haptics_enabled", false))
    }

    val triggerHaptic: (Int) -> Unit = { feedbackConstant ->
        if (isHapticsEnabled) {
            try {
                view.performHapticFeedback(feedbackConstant)
            } catch (ignored: Exception) {}
        }
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            // Pinch zoom and tap-to-focus are done by the touch layer over the preview (see "touch layer"), which also
            // draws the focus box and the exposure sun. The built-in versions are off so nothing happens twice.
            isPinchToZoomEnabled = false
            isTapToFocusEnabled = false
            try {
                videoCaptureQualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD, Quality.LOWEST),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val sound = remember {
        try {
            MediaActionSound().apply {
                try { load(MediaActionSound.SHUTTER_CLICK) } catch (ignored: Exception) {}
                try { load(MediaActionSound.START_VIDEO_RECORDING) } catch (ignored: Exception) {}
                try { load(MediaActionSound.STOP_VIDEO_RECORDING) } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(sound) {
        onDispose {
            try {
                sound?.release()
            } catch (ignored: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        SoundUtils.init(context)
    }

    var currentScreen by remember { mutableStateOf("camera") }
    var currentMode by remember { mutableStateOf(CameraMode.PHOTO) }
    val isVideoMode = currentMode == CameraMode.VIDEO
    var selectedFilter by remember { mutableStateOf(ColorFilterProfile.ORIGINAL) }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    var isSearchingWithGemini by remember { mutableStateOf(false) }
    var geminiSearchResult by remember { mutableStateOf<GeminiSearchResult?>(null) }
    var searchCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var searchQueryText by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    // circle to search: the frame frozen when the finger went down, and the crop of the circled area
    var circleFrame by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCrop by remember { mutableStateOf<Bitmap?>(null) }
    var searchCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // images + links from Google for the current result
    var webImages by remember { mutableStateOf<List<WebImageResult>>(emptyList()) }
    var webImagesLoading by remember { mutableStateOf(false) }
    var webImagesError by remember { mutableStateOf<String?>(null) }
    var imagesJob by remember { mutableStateOf<Job?>(null) }
    // follow-up questions asked inside the result card, and the job answering the latest one
    var followUps by remember { mutableStateOf<List<FollowUpTurn>>(emptyList()) }
    var followUpJob by remember { mutableStateOf<Job?>(null) }
    // bumped on every search start; the glow keys on it so it still plays when a search ends within a frame
    var searchTrigger by remember { mutableIntStateOf(0) }
    // kept so the result card can finish its slide-down animation after the result is cleared
    var shownSearchResult by remember { mutableStateOf<GeminiSearchResult?>(null) }
    var shownSearchBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shownSearchFrame by remember { mutableStateOf<Bitmap?>(null) }
    // bottom controls + suggestions fade out while Gemini is searching
    val chromeAlpha by animateFloatAsState(
        targetValue = if (isSearchingWithGemini || geminiSearchResult != null) 0f else 1f,
        animationSpec = tween(350),
        label = "ChromeFade"
    )

    var flashToastText by remember { mutableStateOf<String?>(null) }
    var isRecordingPaused by remember { mutableStateOf(false) }


    var currentShutterSound by remember {
        val id = prefs.getString("shutter_sound", ShutterSoundType.DEFAULT.id) ?: ShutterSoundType.DEFAULT.id
        mutableStateOf(ShutterSoundType.values().find { it.id == id } ?: ShutterSoundType.DEFAULT)
    }
    var currentRecordSound by remember {
        val id = prefs.getString("record_sound", RecordSoundType.DEFAULT.id) ?: RecordSoundType.DEFAULT.id
        mutableStateOf(RecordSoundType.values().find { it.id == id } ?: RecordSoundType.DEFAULT)
    }
    var currentTimerSound by remember {
        val id = prefs.getString("timer_sound", TimerSoundType.DEFAULT_TICK.id) ?: TimerSoundType.DEFAULT_TICK.id
        mutableStateOf(TimerSoundType.values().find { it.id == id } ?: TimerSoundType.DEFAULT_TICK)
    }

    var isGridEnabled by remember {
        mutableStateOf(prefs.getBoolean("grid_enabled", false))
    }
    var isLevelerEnabled by remember {
        mutableStateOf(prefs.getBoolean("leveler_enabled", false))
    }
    // The dark containers behind the top and bottom floating controls: off by default (Settings > Control bars).
    var isEdgeBarsEnabled by remember {
        mutableStateOf(prefs.getBoolean("edge_bars_enabled", false))
    }
    var isPalmGestureEnabled by remember {
        mutableStateOf(prefs.getBoolean("palm_gesture_enabled", false))
    }
    // "Cheese": say the word and the photo is taken at once (Settings > Camera utilities). Needs the microphone.
    var isCheeseEnabled by remember {
        mutableStateOf(prefs.getBoolean("cheese_enabled", false))
    }

    // Gemini model picker (Search mode): the choice is saved and published for GeminiSearchService to read
    var selectedGeminiModel by remember { mutableStateOf(GeminiModel.fromId(prefs.getString("gemini_model", null))) }
    var isModelMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(selectedGeminiModel) { GeminiModelSelection.selectedId = selectedGeminiModel.id }
    LaunchedEffect(currentMode, isSearchingWithGemini) {
        if (currentMode != CameraMode.SEARCH || isSearchingWithGemini) isModelMenuOpen = false
    }

    var isRecordingLocked by remember { mutableStateOf(false) }
    var isQuickVideoActive by remember { mutableStateOf(false) }
    var isBursting by remember { mutableStateOf(false) }
    var burstCount by remember { mutableStateOf(0) }
    var burstJob by remember { mutableStateOf<Job?>(null) }
    var burstToastMessage by remember { mutableStateOf<String?>(null) }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val accelerometer = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    var currentRoll by remember { mutableFloatStateOf(0f) }
    var isPhoneLevel by remember { mutableStateOf(false) }

    var isBurstMotionExcessive by remember { mutableStateOf(false) }
    var burstMotionMagnitude by remember { mutableFloatStateOf(0f) }
    var lastExcessiveMotionTimestamp by remember { mutableLongStateOf(0L) }

    DisposableEffect(isLevelerEnabled, isBursting) {
        if ((!isLevelerEnabled && !isBursting) || sensorManager == null || accelerometer == null) {
            return@DisposableEffect onDispose {}
        }
        var lastAx = 0f
        var lastAy = 0f
        var lastAz = 0f
        var isFirstSample = true

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                if (isLevelerEnabled) {
                    val rollRad = atan2(-ax.toDouble(), ay.toDouble())
                    var rollDeg = Math.toDegrees(rollRad).toFloat()
                    if (rollDeg > 90f) rollDeg -= 180f else if (rollDeg < -90f) rollDeg += 180f
                    currentRoll = currentRoll + 0.28f * (rollDeg - currentRoll)
                    val leveled = abs(currentRoll) <= 1.5f
                    if (leveled && !isPhoneLevel) {
                        triggerHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    isPhoneLevel = leveled
                }

                if (isBursting) {
                    if (!isFirstSample) {
                        val dX = ax - lastAx
                        val dY = ay - lastAy
                        val dZ = az - lastAz
                        val delta = sqrt((dX * dX + dY * dY + dZ * dZ).toDouble()).toFloat()
                        burstMotionMagnitude = burstMotionMagnitude * 0.6f + delta * 0.4f
                        if (burstMotionMagnitude > 2.2f) {
                            lastExcessiveMotionTimestamp = System.currentTimeMillis()
                            isBurstMotionExcessive = true
                        }
                    } else {
                        isFirstSample = false
                    }
                    lastAx = ax
                    lastAy = ay
                    lastAz = az
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        try {
            cameraController.setEnabledUseCases(
                CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE or CameraController.IMAGE_ANALYSIS
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                cameraController.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            } catch (ignored: Exception) {}
        }
    }

    var flashEnabled by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordingDurationNanos by remember { mutableLongStateOf(0L) }
    var currentZoom by remember { mutableStateOf(1f) }

    // real zoom range of THIS phone's camera (filled in by the zoom state below)
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    // Zoom dial: shown while the zoom changes (pinch, preset tap, dragging the dial), hides 1.5 s after the last change.
    // While a finger is still pinching or resting on the dial, it stays.
    var zoomDialActive by remember { mutableStateOf(false) }
    var zoomDialPrimed by remember { mutableStateOf(false) }
    var zoomGestureActive by remember { mutableStateOf(false) }
    LaunchedEffect(currentZoom, zoomGestureActive) {
        if (!zoomDialPrimed) {
            zoomDialPrimed = true          // the first value is just the starting zoom
        } else if (currentMode == CameraMode.PANORAMA) {
            zoomDialActive = false         // panorama has no zoom, so the dial never shows
        } else {
            zoomDialActive = true
            if (!zoomGestureActive) {
                delay(1500)
                zoomDialActive = false
            }
        }
    }
    val zoomDialShown = zoomDialActive && currentMode != CameraMode.PANORAMA && geminiSearchResult == null && !isSearchingWithGemini
    // One fade value for the dial AND the bottom container behind it: both read exactly this, so they start and end
    // at the very same moment (no slide, no delay). It is only read inside graphicsLayer, so it does not recompose the screen.
    val zoomDialFade = animateFloatAsState(
        targetValue = if (zoomDialShown) 1f else 0f,
        animationSpec = tween(ZOOM_DIAL_FADE_MS, easing = FastOutSlowInEasing),
        label = "ZoomDialFade"
    )
    val zoomDialInTree by remember { derivedStateOf { zoomDialFade.value > 0f } }
    // Tap-to-focus box with the exposure sun (the box itself is drawn by FocusExposureOverlay)
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusVisible by remember { mutableStateOf(false) }
    var focusTapCount by remember { mutableIntStateOf(0) }
    var exposureValue by remember { mutableFloatStateOf(0f) }          // -1 (darker) .. 1 (brighter), 0 = normal
    var exposureIndexApplied by remember { mutableIntStateOf(0) }
    var exposureTouching by remember { mutableStateOf(false) }

    // The box stays 3 s after the last tap or sun movement, and as long as a finger is on the sun.
    LaunchedEffect(focusTapCount, exposureValue, exposureTouching) {
        if (focusTapCount > 0) {
            focusVisible = true
            if (!exposureTouching) {
                delay(3000)
                focusVisible = false
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (flashEnabled) {
                    flashEnabled = false
                    try {
                        cameraController.enableTorch(false)
                        cameraController.imageCaptureFlashMode = androidx.camera.core.ImageCapture.FLASH_MODE_OFF
                    } catch (e: Exception) {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                cameraController.enableTorch(false)
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(lifecycleOwner) {
        cameraController.zoomState.observe(lifecycleOwner) { state ->
            if (state != null) {
                maxZoom = state.maxZoomRatio
                minZoom = state.minZoomRatio
                // while a finger is pinching / on the dial, our own value is the truth (the camera lags a little behind)
                if (!zoomGestureActive && abs(currentZoom - state.zoomRatio) > 0.03f) {
                    currentZoom = state.zoomRatio
                }
            }
        }
    }

    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                lastCapturedUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
    }

    var isFrontCamera by remember { mutableStateOf(false) }

    // Horizon lock (video only), see HorizonLockEffect.kt: the viewfinder and the recorded video both stay level.
    var horizonMode by remember { mutableStateOf(HorizonLockMode.fromName(prefs.getString("horizon_lock", null))) }
    val horizonEffect = remember { HorizonLockEffect.create(context) }
    var horizonAttached by remember { mutableStateOf(false) }
    SideEffect {
        horizonEffect.isFrontCamera = isFrontCamera
        horizonEffect.displayRotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
    }
    // The user's choice is kept, but it only takes effect while the Experimental lab is unlocked.
    val effectiveHorizonMode = if (isExperimentalUnlocked) horizonMode else HorizonLockMode.OFF
    LaunchedEffect(isVideoMode, effectiveHorizonMode, isRecording) {
        // Changing effects rebinds the camera, which would cut a recording: wait until it has stopped.
        if (isRecording) return@LaunchedEffect
        horizonEffect.mode = effectiveHorizonMode
        val want = isVideoMode && effectiveHorizonMode != HorizonLockMode.OFF
        if (want != horizonAttached) {
            try {
                // This CameraX version takes a non-null Set: an empty set is how effects are cleared.
                cameraController.setEffects(
                    if (want) setOf<androidx.camera.core.CameraEffect>(horizonEffect)
                    else emptySet<androidx.camera.core.CameraEffect>()
                )
                horizonAttached = want
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    DisposableEffect(horizonEffect) {
        onDispose {
            try { cameraController.setEffects(emptySet<androidx.camera.core.CameraEffect>()) } catch (ignored: Exception) {}
            horizonEffect.release()
        }
    }

    var isSwitchingCamera by remember { mutableStateOf(false) }
    // 0 = the live picture, 1 = fully covered by the blurred snapshot (read in the draw phase only).
    val switchBlurProgress = remember { Animatable(0f) }
    val switchIconRotation = remember { Animatable(0f) }

    val switchViewfinderScale by animateFloatAsState(
        targetValue = if (isSwitchingCamera) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "SwitchViewfinderScale"
    )

    // Camera flip: the picture blurs out until it is just soft colour, the camera is swapped while it is hidden,
    // then the blur clears and the new camera's picture shows again. (Plain dark instead of blur when Blur is off.)
    val switchCamera: () -> Unit = {
        if (!isSwitchingCamera && !isRecording && !isQuickVideoActive && !isBursting) {
            val targetSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            scope.launch {
                isSwitchingCamera = true
                triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)

                launch {
                    val currentRot = switchIconRotation.value
                    switchIconRotation.animateTo(
                        targetValue = currentRot + 180f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
                    )
                }

                try {
                    // 1. blur out
                    switchBlurProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )

                    // 2. fully blurred: swap the camera
                    try {
                        if (cameraController.hasCamera(targetSelector)) {
                            cameraController.cameraSelector = targetSelector
                            isFrontCamera = !isFrontCamera

                            if (isFrontCamera && flashEnabled) {
                                flashEnabled = false
                                try {
                                    cameraController.enableTorch(false)
                                    cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                                } catch (ignored: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // wait (at most 0.8 s) for the first picture of the new camera
                    delay(90)
                    withTimeoutOrNull(800) {
                        while (previewView?.previewStreamState?.value != PreviewView.StreamState.STREAMING) delay(16)
                    }

                    // 3. the blur clears and the new picture shows
                    switchBlurProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                    )
                } finally {
                    // also when cancelled half way: never leave the viewfinder covered
                    withContext(NonCancellable) {
                        switchBlurProgress.snapTo(0f)
                        isSwitchingCamera = false
                    }
                }
            }
        }
    }

    var videoResolutions by remember { mutableStateOf(listOf("720p", "1080p")) }
    var supportedFps by remember { mutableStateOf(listOf("30")) }
    // start from what the first-run setup picked for this phone (falls back to 1080p / 30fps)
    var currentVideoRes by remember { mutableStateOf(prefs.getString("setup_video_res", null) ?: "1080p") }
    var currentFps by remember { mutableStateOf(prefs.getString("setup_video_fps", null) ?: "30") }
    val deviceName = remember { prefs.getString("device_name", null)?.takeIf { it.isNotBlank() } ?: "Phone" }

    LaunchedEffect(isFrontCamera) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                val cameraIdList = cameraManager.cameraIdList
                val targetLens = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
                val cameraId = cameraIdList.firstOrNull { id ->
                    try {
                        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == targetLens
                    } catch (e: Exception) {
                        false
                    }
                } ?: cameraIdList.firstOrNull()

                if (cameraId != null) {
                    val chars = cameraManager.getCameraCharacteristics(cameraId)
                    val streamConfig = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                    val videoSizes = streamConfig?.getOutputSizes(android.media.MediaRecorder::class.java)
                    val res = mutableListOf<String>()
                    if (videoSizes?.any { it.height == 720 || it.width == 1280 } == true) res.add("720p")
                    if (videoSizes?.any { it.height == 1080 || it.width == 1920 } == true) res.add("1080p")
                    if (videoSizes?.any { it.height >= 2160 || it.width >= 3840 } == true) res.add("4K")

                    if (res.isEmpty()) {
                        res.addAll(listOf("720p", "1080p"))
                    }
                    videoResolutions = res
                    if (!res.contains(currentVideoRes)) currentVideoRes = res.last()

                    val fpsList = mutableListOf("30")
                    val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    if (fpsRanges?.any { it.upper >= 60 } == true) {
                        fpsList.add("60")
                    }
                    supportedFps = fpsList
                    if (!fpsList.contains(currentFps)) currentFps = fpsList.first()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            videoResolutions = listOf("720p", "1080p")
            supportedFps = listOf("30")
            if (!videoResolutions.contains(currentVideoRes)) currentVideoRes = "1080p"
            if (!supportedFps.contains(currentFps)) currentFps = "30"
        }
    }

    LaunchedEffect(currentVideoRes) {
        try {
            val targetQuality = when (currentVideoRes) {
                "4K" -> Quality.UHD
                "1080p" -> Quality.FHD
                "720p" -> Quality.HD
                else -> Quality.SD
            }
            val selector = QualitySelector.fromOrderedList(
                listOf(targetQuality, Quality.FHD, Quality.HD, Quality.SD, Quality.LOWEST),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
            cameraController.videoCaptureQualitySelector = selector
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val aspectRatios = listOf("FULL", "9:16", "3:4")
    var currentAspect by remember {
        mutableStateOf(prefs.getString("current_aspect", "9:16") ?: "9:16")
    }

    var currentTimer by remember { mutableStateOf(0) }
    var activeTimer by remember { mutableStateOf(0) }
    var timerJob by remember { mutableStateOf<Job?>(null) }

    var isFadeEffectEnabled by remember {
        mutableStateOf(prefs.getBoolean("fade_effect", true))
    }
    var isBouncyAnimationEnabled by remember {
        mutableStateOf(prefs.getBoolean("bouncy_anim", true))
    }

    var isTopDropdownOpen by remember { mutableStateOf(false) }
    var activeQuickSetting by remember { mutableStateOf<String?>(null) }

    var isNightModeAuto by remember {
        mutableStateOf(prefs.getBoolean("night_mode_auto", false))
    }
    var selectedWatermark by remember {
        val savedName = prefs.getString("selected_watermark", WatermarkProfile.OFF.name) ?: WatermarkProfile.OFF.name
        mutableStateOf(try { WatermarkProfile.valueOf(savedName) } catch (e: Exception) { WatermarkProfile.OFF })
    }
    var isWatermarkEnabled by remember {
        mutableStateOf(prefs.getBoolean("watermark_enabled", false))
    }

    var showBlink by remember { mutableStateOf(false) }

    val getLastLocation: () -> android.location.Location? = {
        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val takePhoto: () -> Unit = {
        scope.launch {
            // shutter blink: the viewfinder dips to black for a moment, then fades back in
            showBlink = true
            delay(110)
            showBlink = false
        }

        SoundUtils.playShutter(context, currentShutterSound, sound)

        val currentTimeMillis = System.currentTimeMillis()
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(currentTimeMillis)
        val location = getLastLocation()

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, currentTimeMillis)
            put(MediaStore.Images.Media.DATE_ADDED, currentTimeMillis / 1000)
            if (location != null) {
                put(MediaStore.Images.Media.LATITUDE, location.latitude)
                put(MediaStore.Images.Media.LONGITUDE, location.longitude)
            }
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
            }
        }

        val metadata = ImageCapture.Metadata().apply {
            if (location != null) {
                this.location = location
            }
            isReversedHorizontal = isFrontCamera
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).setMetadata(metadata).build()

        try {
            cameraController.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        outputFileResults.savedUri?.let { uri ->
                            lastCapturedUri = uri
                            val shouldApplyFilter = selectedFilter != ColorFilterProfile.ORIGINAL
                            val shouldApplyWatermark = isWatermarkEnabled && selectedWatermark != WatermarkProfile.OFF

                            scope.launch(Dispatchers.IO) {
                                if (shouldApplyFilter || shouldApplyWatermark) {
                                    try {
                                        // upright pixels (EXIF rotation applied) + the EXIF tags to keep
                                        val decoded = PhotoRetouch.decodeUpright(context, uri)
                                        if (decoded != null) {
                                            val mutableBitmap = decoded.bitmap

                                            if (shouldApplyFilter && selectedFilter.matrixValues != null) {
                                                val canvas = android.graphics.Canvas(mutableBitmap)
                                                val paint = android.graphics.Paint()
                                                val cm = android.graphics.ColorMatrix(selectedFilter.matrixValues)
                                                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                                                canvas.drawBitmap(mutableBitmap, 0f, 0f, paint)
                                            }

                                            if (shouldApplyWatermark) {
                                                val canvas = android.graphics.Canvas(mutableBitmap)
                                                val paint = android.graphics.Paint().apply {
                                                    color = android.graphics.Color.WHITE
                                                    textSize = (mutableBitmap.height * 0.035f).coerceAtLeast(32f)
                                                    isAntiAlias = true
                                                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.DKGRAY)
                                                }
                                                val watermarkText = when (selectedWatermark) {
                                                    WatermarkProfile.AI_CAMERA -> "AI Camera"
                                                    WatermarkProfile.SHOT_ON -> "Shot on $deviceName"
                                                    WatermarkProfile.DATETIME -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                                    WatermarkProfile.OFF -> ""
                                                }
                                                if (watermarkText.isNotEmpty()) {
                                                    val bounds = android.graphics.Rect()
                                                    paint.getTextBounds(watermarkText, 0, watermarkText.length, bounds)
                                                    val x = mutableBitmap.width - bounds.width() - (mutableBitmap.width * 0.05f)
                                                    val y = mutableBitmap.height - (mutableBitmap.height * 0.05f)
                                                    canvas.drawText(watermarkText, x, y, paint)
                                                }
                                            }

                                            context.contentResolver.openOutputStream(uri, "rwt")?.use { outStream ->
                                                mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outStream)
                                            }
                                            PhotoRetouch.restoreExif(context, uri, decoded.exif)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val performGeminiSearch: (Bitmap?, String?) -> Unit = { capturedBmp, customQuery ->
        val bmp = capturedBmp ?: previewView?.bitmap
        if (bmp != null) {
            val crop = pendingCrop
            pendingCrop = null
            searchCapturedBitmap = bmp
            searchCropBitmap = crop
            imagesJob?.cancel()
            followUpJob?.cancel()
            followUps = emptyList()
            webImages = emptyList()
            webImagesError = null
            webImagesLoading = false
            searchTrigger++
            isSearchingWithGemini = true
            geminiSearchResult = null
            triggerHaptic(HapticFeedbackConstants.CLOCK_TICK)
            val query = buildSearchQuery(customQuery, crop != null)
            searchJob?.cancel()
            searchJob = scope.launch {
                val result = GeminiSearchService.searchWithImage(crop ?: bmp, query)
                geminiSearchResult = result
                isSearchingWithGemini = false
                triggerHaptic(HapticFeedbackConstants.CONFIRM)
                if (result.isSuccess) {
                    val imageQuery = cleanAiTitle(result.title)
                    if (imageQuery.isNotBlank()) {
                        webImagesLoading = true
                        imagesJob = scope.launch {
                            val r = WebImageSearch.search(imageQuery)
                            webImages = r.getOrDefault(emptyList())
                            webImagesError = r.exceptionOrNull()?.message
                            webImagesLoading = false
                        }
                    }
                }
            }
        }
    }

    // Panorama mode: tap the shutter, sweep the phone sideways while the strip builds up, tap again to finish.
    val panorama = rememberPanoramaController(
        grabFrame = { previewView?.bitmap },
        onSaved = { uri ->
            if (uri != null) {
                lastCapturedUri = uri
                triggerHaptic(HapticFeedbackConstants.CONFIRM)
                burstToastMessage = "Panorama saved"
                scope.launch {
                    delay(2400)
                    if (burstToastMessage == "Panorama saved") burstToastMessage = null
                }
            }
        }
    )
    LaunchedEffect(currentMode) {
        if (currentMode != CameraMode.PANORAMA) {
            panorama.cancel()
        } else {
            if (isFrontCamera) switchCamera()      // panorama needs the rear camera
            // panorama has no zoom: always start (and stay) at 1x
            val z = 1f.coerceIn(minZoom, maxZoom)
            if (abs(currentZoom - z) > 0.001f) {
                currentZoom = z
                try { cameraController.setZoomRatio(z) } catch (e: Exception) {}
            }
        }
    }

    // Video recording, a panorama sweep and a running countdown (timer or palm gesture) are "immersive": the black bars,
    // the zoom row, the mode row and the floating top controls slide away (up or down) so only the viewfinder and the
    // shutter stay. When the countdown ends and the photo is taken, everything slides back in.
    val panoBusy = panorama.capturing || panorama.saving
    val timerCountdown = activeTimer > 0
    val immersive = isRecording || panoBusy || timerCountdown
    val immersiveProgress = animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "ImmersiveChrome"
    )
    // the two buttons beside the shutter slide away for a panorama or a countdown (they are used while recording)
    val sideHidden = panoBusy || timerCountdown
    val panoSideHide = animateFloatAsState(
        targetValue = if (sideHidden) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "PanoSideHide"
    )
    LaunchedEffect(immersive) {
        if (immersive) {
            isTopDropdownOpen = false
            activeQuickSetting = null
        }
    }
    // the flashlight button lives outside the sliding top row, so it gets its own slide-away for the countdown
    val timerChromeHide = animateFloatAsState(
        targetValue = if (timerCountdown) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "TimerChromeHide"
    )
    // an open zoom dial (and the bar behind it) fades out with the rest while the countdown runs
    LaunchedEffect(timerCountdown) {
        if (timerCountdown) {
            zoomGestureActive = false
            zoomDialActive = false
        }
    }

    // A new camera or a new mode starts at normal exposure again (the sun goes back to the middle). This effect is
    // declared before the Pro one on purpose: Pro mode then sets its own brightness after this reset, never before it.
    LaunchedEffect(currentMode, isFrontCamera) {
        exposureValue = 0f
        if (exposureIndexApplied != 0) {
            exposureIndexApplied = 0
            try {
                cameraController.cameraControl?.setExposureCompensationIndex(0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Pro mode: manual brightness, ISO, shutter, focus and white balance, applied live. Leaving the mode (or
    // flipping the camera) puts the camera back to automatic / re-reads what the other lens can do.
    val pro = remember { ProController(cameraController) }
    LaunchedEffect(currentMode, isFrontCamera, isSwitchingCamera) {
        if (currentMode == CameraMode.PRO) {
            if (!isSwitchingCamera) pro.enter()
        } else {
            pro.exit()
        }
    }

    // Pick any image from the gallery and search about it (Search mode, button next to the shutter)
    val pickSearchImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeBitmapForSearch(context, uri) }
                if (bmp != null) {
                    pendingCrop = null
                    performGeminiSearch(bmp, searchQueryText.ifBlank { null })
                }
            }
        }
    }

    val startVideoRecording = {
        if (!isRecording) {
            SoundUtils.playRecordStartSound(context, currentRecordSound, sound)
            triggerHaptic(HapticFeedbackConstants.CONFIRM)

            val currentTimeMillis = System.currentTimeMillis()
            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(currentTimeMillis)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, currentTimeMillis)
                put(MediaStore.Video.Media.DATE_ADDED, currentTimeMillis / 1000)
                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraApp")
                }
            }

            val outputOptions = androidx.camera.video.MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            val audioConfig = if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                AudioConfig.create(true)
            } else {
                AudioConfig.create(false)
            }

            try {
                val activeRecording = cameraController.startRecording(
                    outputOptions,
                    audioConfig,
                    ContextCompat.getMainExecutor(context)
                ) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            isRecordingPaused = false
                            recordingDurationNanos = 0L
                        }
                        is VideoRecordEvent.Pause -> {
                            isRecordingPaused = true
                        }
                        is VideoRecordEvent.Resume -> {
                            isRecordingPaused = false
                        }
                        is VideoRecordEvent.Status -> {
                            recordingDurationNanos = event.recordingStats.recordedDurationNanos
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            isRecordingPaused = false
                            isQuickVideoActive = false
                            isRecordingLocked = false
                            recordingDurationNanos = 0L
                            if (!event.hasError()) {
                                event.outputResults.outputUri.let { uri ->
                                    lastCapturedUri = uri
                                }
                            }
                        }
                    }
                }
                recording = activeRecording
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val stopVideoRecording = {
        if (isRecording || recording != null) {
            SoundUtils.playRecordStopSound(context, currentRecordSound, sound)
            triggerHaptic(HapticFeedbackConstants.CONFIRM)
            try {
                recording?.stop()
                recording = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isRecording = false
            isRecordingPaused = false
            isQuickVideoActive = false
            isRecordingLocked = false
        }
    }

    // Palm gesture shutter (selfie): a REAL hand detector, see PalmGestureDetector.kt. It runs on the phone and only reacts to
    // an open palm, not to anything bright. Hold the palm up to the front camera: a 3 second countdown runs at the top
    // of the screen (time to lower the hand), then the selfie is taken. There is no pop-up, only the countdown.
    // Pause it with the setting, or by leaving the camera.
    LaunchedEffect(isPalmGestureEnabled, isFrontCamera) {
        if (!isPalmGestureEnabled || !isFrontCamera) {
            cameraController.clearImageAnalysisAnalyzer()
            return@LaunchedEffect
        }
        // the model loads off the UI thread (the very first time it is downloaded once, about 8 MB, then it stays on the phone)
        val detector = withContext(Dispatchers.IO) {
            PalmGestureDetector.create(context).also { if (!isActive) it?.close() }
        }
        if (detector == null) {
            android.widget.Toast.makeText(
                context,
                "Palm gesture could not start. The first time it needs internet to download a small hand model. Connect, then turn it off and on again.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return@LaunchedEffect
        }

        val analysisExecutor = Executors.newSingleThreadExecutor()
        var armed = true            // after a shot the palm must disappear once before the next one can start
        var lastTriggerTime = 0L
        var lastLookTime = 0L

        val analyzer = ImageAnalysis.Analyzer { imageProxy ->
            try {
                val now = System.currentTimeMillis()
                val busy = timerJob?.isActive == true || isRecording || currentScreen != "camera" ||
                    currentMode == CameraMode.SEARCH || currentMode == CameraMode.PANORAMA
                if (busy) {
                    detector.resetHold()
                    return@Analyzer
                }
                if (now - lastLookTime < PalmGestureDetector.LOOK_INTERVAL_MS) return@Analyzer
                lastLookTime = now

                // true once the palm has been steady for a moment (it forgives a missed frame or two)
                val held = detector.isPalmHeld(imageProxy)
                if (detector.palmVotes == 0) armed = true          // the palm is gone: ready for the next shot
                if (!held || !armed || now - lastTriggerTime < 3000L) return@Analyzer

                armed = false
                lastTriggerTime = now
                detector.resetHold()
                scope.launch(Dispatchers.Main) {
                    triggerHaptic(HapticFeedbackConstants.CONFIRM)
                    timerJob?.cancel()
                    timerJob = scope.launch {
                        for (i in 3 downTo 1) {
                            activeTimer = i
                            SoundUtils.playTimerTick(currentTimerSound)
                            delay(1000)
                        }
                        activeTimer = 0
                        if (isVideoMode) startVideoRecording() else takePhoto()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                imageProxy.close()
            }
        }

        cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
        try {
            awaitCancellation()
        } finally {
            cameraController.clearImageAnalysisAnalyzer()
            // close the detector on the analysis thread, after any frame that is still being looked at
            analysisExecutor.execute { detector.close() }
            analysisExecutor.shutdown()
        }
    }

    // Cheese: a REAL speech listener (Android SpeechRecognizer, on the phone when the device has an offline speech pack).
    // The moment "cheese" shows up in what it hears, even before the sentence is finished, the photo is taken. No countdown.
    // It only listens while this screen is open, in the photo modes, and stops when the app goes to the background.
    val takePhotoNow by rememberUpdatedState(takePhoto)
    var micGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (!granted && isCheeseEnabled) {
            isCheeseEnabled = false
            prefs.edit().putBoolean("cheese_enabled", false).apply()
            android.widget.Toast.makeText(context, "Cheese needs the microphone permission.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(isCheeseEnabled, micGranted, lifecycleOwner) {
        if (!isCheeseEnabled || !micGranted) return@DisposableEffect onDispose { }
        val listener = CheeseListener(
            context = context,
            canFire = {
                currentScreen == "camera" && !isRecording && !isBursting && timerJob?.isActive != true && activeTimer == 0 &&
                    currentMode != CameraMode.SEARCH && currentMode != CameraMode.PANORAMA && currentMode != CameraMode.VIDEO
            },
            onCheese = {
                triggerHaptic(HapticFeedbackConstants.CONFIRM)
                takePhotoNow()
            },
            onUnavailable = {
                android.widget.Toast.makeText(
                    context,
                    "Cheese needs speech recognition, and this phone has none installed or enabled.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        )
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> listener.start()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> listener.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) listener.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            listener.destroy()
        }
    }

    // Navigation bar: always fully transparent, on the camera and on Settings, in every mode.
    // (run on every recomposition so nothing else can switch it back)
    SideEffect {
        var ctx: Context? = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        (ctx as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                w.isNavigationBarContrastEnforced = false
            }
        }
    }

    val cameraBackdrop = remember { CameraBackdrop() }
    // getBitmap() runs on the UI thread; skipping it while the mode switch animates keeps that animation smooth.
    val modeSwitchHold = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    // Same while the aspect ratio animates: the preview is hidden under a blurred snapshot then.
    val aspectHold = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    LaunchedEffect(currentMode) {
        modeSwitchHold.set(true)
        delay(550)
        modeSwitchHold.set(false)
    }
    // Settings can unlock or lock the Experimental lab: re-read it when the camera screen comes back.
    LaunchedEffect(currentScreen) {
        if (currentScreen == "camera") {
            isExperimentalUnlocked = prefs.getBoolean("experimental_unlocked", false)
            if (!isExperimentalUnlocked && activeQuickSetting == "Horizon") activeQuickSetting = null
        }
    }
    BackdropCaptureEffect(
        previewView,
        cameraBackdrop,
        enabled = currentScreen == "camera" && isBlurEnabled,
        blurIntensity = blurIntensity,
        // also held still while the camera flips, so the glass and the cover never show a black in-between frame
        paused = searchCapturedBitmap != null || isSwitchingCamera,
        lowPower = isRecording || isBursting || panorama.capturing,
        holdProvider = { modeSwitchHold.get() || aspectHold.get() }
    )

    CompositionLocalProvider(
        LocalBouncyEnabled provides isBouncyAnimationEnabled,
        LocalCameraBackdrop provides cameraBackdrop,
        LocalGlassBlurRadius provides blurIntensity.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState == "settings") {
                        (slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200)))
                    }
                },
                label = "ScreenNavigationTransition"
            ) { screen ->
                if (screen == "settings") {
                    BackHandler { currentScreen = "camera" }
                    SettingsScreen(
                        onBack = { currentScreen = "camera" },
                        uiColor = uiColor,
                        isFadeEffectEnabled = isFadeEffectEnabled,
                        onFadeEffectChange = {
                            isFadeEffectEnabled = it
                            prefs.edit().putBoolean("fade_effect", it).apply()
                        },
                        isBouncyAnimationEnabled = isBouncyAnimationEnabled,
                        onBouncyAnimationChange = {
                            isBouncyAnimationEnabled = it
                            prefs.edit().putBoolean("bouncy_anim", it).apply()
                        },
                        isHapticsEnabled = isHapticsEnabled,
                        onHapticsChange = {
                            isHapticsEnabled = it
                            prefs.edit().putBoolean("haptics_enabled", it).apply()
                        },
                        isBlurEnabled = isBlurEnabled,
                        onBlurChange = {
                            isBlurEnabled = it
                            prefs.edit().putBoolean("blur_enabled", it).apply()
                        },
                        horizonMode = horizonMode,
                        onHorizonModeChange = {
                            horizonMode = it
                            prefs.edit().putString("horizon_lock", it.name).apply()
                        },
                        isGridEnabled = isGridEnabled,
                        onGridChange = {
                            isGridEnabled = it
                            prefs.edit().putBoolean("grid_enabled", it).apply()
                        },
                        isLevelerEnabled = isLevelerEnabled,
                        onLevelerChange = {
                            isLevelerEnabled = it
                            prefs.edit().putBoolean("leveler_enabled", it).apply()
                        },
                        isEdgeBarsEnabled = isEdgeBarsEnabled,
                        onEdgeBarsChange = {
                            isEdgeBarsEnabled = it
                            prefs.edit().putBoolean("edge_bars_enabled", it).apply()
                        },
                        isPalmGestureEnabled = isPalmGestureEnabled,
                        onPalmGestureChange = {
                            isPalmGestureEnabled = it
                            prefs.edit().putBoolean("palm_gesture_enabled", it).apply()
                        },
                        isCheeseEnabled = isCheeseEnabled,
                        onCheeseChange = {
                            isCheeseEnabled = it
                            prefs.edit().putBoolean("cheese_enabled", it).apply()
                            if (it && !micGranted) micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        currentShutterSound = currentShutterSound,
                        onShutterSoundChange = {
                            currentShutterSound = it
                            prefs.edit().putString("shutter_sound", it.id).apply()
                        },
                        currentRecordSound = currentRecordSound,
                        onRecordSoundChange = {
                            currentRecordSound = it
                            prefs.edit().putString("record_sound", it.id).apply()
                        },
                        currentTimerSound = currentTimerSound,
                        onTimerSoundChange = {
                            currentTimerSound = it
                            prefs.edit().putString("timer_sound", it.id).apply()
                        },
                        isWatermarkEnabled = isWatermarkEnabled,
                        onWatermarkChange = {
                            isWatermarkEnabled = it
                            selectedWatermark = if (it) WatermarkProfile.AI_CAMERA else WatermarkProfile.OFF
                            prefs.edit()
                                .putBoolean("watermark_enabled", it)
                                .putString("selected_watermark", selectedWatermark.name)
                                .apply()
                        },
                        isNightModeAuto = isNightModeAuto,
                        onNightModeAutoChange = {
                            isNightModeAuto = it
                            prefs.edit().putBoolean("night_mode_auto", it).apply()
                        },
                        aspectRatios = aspectRatios,
                        currentAspect = currentAspect,
                        onAspectChange = {
                            currentAspect = it
                            prefs.edit().putString("current_aspect", it).apply()
                        },
                        onRerunSetup = onRerunSetup
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        BackHandler(enabled = currentMode == CameraMode.SEARCH || currentMode == CameraMode.PANORAMA || currentMode == CameraMode.PRO) {
                            currentMode = CameraMode.PHOTO
                        }

                        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val fullAspect = maxWidth.value / maxHeight.value
                            val targetAspect = if (currentMode == CameraMode.PANORAMA) fullAspect else when (currentAspect) {
                                "3:4" -> 3f / 4f
                                "9:16" -> 9f / 16f
                                else -> fullAspect
                            }
                            // Kept as State (not read with `by`) so the animated value is only read during layout
                            // and draw. Reading it in composition recomposed the whole viewfinder on every frame.
                            // A fixed-length tween (no spring): no overshoot, and it ends exactly on time.
                            val animatedAspectState = animateFloatAsState(
                                targetValue = targetAspect,
                                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                label = "AspectRatioAnim"
                            )
                            // True from the first frame of a ratio change until it has settled. derivedStateOf only
                            // recomposes when this flips, not on every animation frame.
                            val aspectAnimating by remember(targetAspect) {
                                derivedStateOf { abs(animatedAspectState.value - targetAspect) > 0.002f }
                            }
                            // Blur cover: appears instantly (so the preview resize never shows), fades out after.
                            val aspectBlur = animateFloatAsState(
                                targetValue = if (aspectAnimating) 1f else 0f,
                                animationSpec = if (aspectAnimating) snap() else tween(220),
                                label = "AspectBlurCover"
                            )
                            SideEffect { aspectHold.set(aspectAnimating) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .layout { measurable, constraints ->
                                        val width = constraints.maxWidth
                                        // The preview is measured ONCE at its final size and only the visible window
                                        // (the clipped box) animates. The camera surface is therefore not resized
                                        // and re-laid out on every frame, which is what caused the stutter.
                                        val targetH = (width / targetAspect).roundToInt()
                                            .coerceIn(constraints.minHeight, constraints.maxHeight)
                                        val curH = (width / animatedAspectState.value).roundToInt()
                                            .coerceIn(constraints.minHeight, constraints.maxHeight)
                                        val placeable = measurable.measure(Constraints.fixed(width, targetH))
                                        layout(width, curH) { placeable.place(0, (curH - targetH) / 2) }
                                    }
                                    .graphicsLayer {
                                        scaleX = switchViewfinderScale
                                        scaleY = switchViewfinderScale
                                        clip = true
                                    }
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PreviewView(ctx).apply {
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                            controller = cameraController
                                            try {
                                                cameraController.bindToLifecycle(lifecycleOwner)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                            previewView = this
                                        }
                                    },
                                    update = { pv ->
                                        try {
                                            // Only touch the layer when the filter actually changed. Re-setting it on every
                                            // recomposition rebuilt the TextureView layer and made the preview/blur flicker.
                                            if (pv.tag != selectedFilter) {
                                                pv.tag = selectedFilter
                                                val matrix = selectedFilter.matrixValues
                                                if (matrix == null) {
                                                    pv.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                                                } else {
                                                    val cm = android.graphics.ColorMatrix(matrix)
                                                    val cf = android.graphics.ColorMatrixColorFilter(cm)
                                                    val paint = android.graphics.Paint().apply { colorFilter = cf }
                                                    pv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                                                }
                                                pv.invalidate()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onGloballyPositioned { coords ->
                                            cameraBackdrop.origin = coords.positionInRoot()
                                            cameraBackdrop.size = Size(
                                                coords.size.width.toFloat(),
                                                coords.size.height.toFloat()
                                            )
                                        }
                                )

                                // ---- touch layer over the preview ----
                                // tap = focus (with the focus box and exposure sun), double tap = flip the camera,
                                // two fingers = pinch zoom. The preview itself takes no touches.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (!isRecording && !isQuickVideoActive && currentMode != CameraMode.PANORAMA) {
                                                        switchCamera()
                                                    }
                                                },
                                                onTap = { offset ->
                                                    focusPoint = offset
                                                    focusTapCount++
                                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    previewView?.let { pv ->
                                                        try {
                                                            val factory = pv.meteringPointFactory
                                                            val point = factory.createPoint(offset.x, offset.y)
                                                            val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
                                                            cameraController.cameraControl?.startFocusAndMetering(action)
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        // Declared after the tap detector, so it sees the touches first: while two
                                        // fingers are down it uses them for zoom and marks them as used, which cancels
                                        // the tap / double tap above (no focus or camera flip after a pinch).
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                var pinching = false
                                                var idA = -1L
                                                var idB = -1L
                                                var startDist = 1f
                                                var startLn = 0f
                                                try {
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        val pressed = event.changes.filter { it.pressed }
                                                        val canPinch = currentMode != CameraMode.PANORAMA && maxZoom > minZoom + 0.01f
                                                        if (pressed.size >= 2 && canPinch) {
                                                            val a = pressed[0]
                                                            val b = pressed[1]
                                                            val dist = (a.position - b.position).getDistance()
                                                            if (!pinching || a.id.value != idA || b.id.value != idB) {
                                                                // two fingers (or a new pair): this is the anchor
                                                                pinching = true
                                                                idA = a.id.value
                                                                idB = b.id.value
                                                                startDist = maxOf(dist, 1f)
                                                                startLn = ln(currentZoom.coerceIn(minZoom, maxZoom))
                                                                zoomGestureActive = true
                                                            } else {
                                                                val target = startLn + PINCH_ZOOM_GAIN * ln(maxOf(dist, 1f) / startDist)
                                                                val z = exp(target).coerceIn(minZoom, maxZoom)
                                                                if (abs(z - currentZoom) > 0.001f) {
                                                                    currentZoom = z
                                                                    cameraController.setZoomRatio(z)
                                                                }
                                                            }
                                                            event.changes.forEach { it.consume() }
                                                        } else if (pinching) {
                                                            // a finger lifted: the pinch is over
                                                            pinching = false
                                                            event.changes.forEach { it.consume() }
                                                        }
                                                    } while (event.changes.any { it.pressed })
                                                } finally {
                                                    zoomGestureActive = false      // all fingers are up (or the gesture was cancelled)
                                                }
                                            }
                                        }
                                )

                                // Frozen frame: the exact image Gemini is scanning stays on screen (instantly, no fade)
                                // until the result sheet is closed, so the user can see what was analysed.
                                searchCapturedBitmap?.let { frozen ->
                                    val frozenImage = remember(frozen) { frozen.asImageBitmap() }
                                    val frozenBlurState = animateFloatAsState(
                                        targetValue = if (geminiSearchResult != null && Build.VERSION.SDK_INT >= 31) 10f else 0f,
                                        animationSpec = tween(350),
                                        label = "FrozenFrameBlur"
                                    )
                                    Image(
                                        bitmap = frozenImage,
                                        contentDescription = "Frame being analysed",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val r = frozenBlurState.value
                                                renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                                            }
                                            // swallow taps so tap-to-focus / double-tap-to-flip don't fire on a still image
                                            .pointerInput(Unit) { detectTapGestures { } }
                                    )
                                }

                                // Circle to search (Search mode only): draw over the preview to mark what to look up.
                                CircleSearchOverlay(
                                    enabled = currentMode == CameraMode.SEARCH && !isSearchingWithGemini && geminiSearchResult == null,
                                    showTrail = isSearchingWithGemini,
                                    onGestureStart = {
                                        val frame = previewView?.bitmap
                                        circleFrame = frame
                                        if (frame != null) searchCapturedBitmap = frame   // freeze what the user is marking
                                        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                    },
                                    onGestureCancel = {
                                        circleFrame = null
                                        searchCapturedBitmap = null
                                    },
                                    onCircled = { bounds, areaSize ->
                                        val frame = circleFrame
                                        if (frame != null) {
                                            pendingCrop = cropForCircle(frame, bounds, areaSize)
                                            performGeminiSearch(frame, searchQueryText.ifBlank { null })
                                        } else {
                                            searchCapturedBitmap = null
                                        }
                                        circleFrame = null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Panorama mode: guide line, live stitched strip and hints
                                if (currentMode == CameraMode.PANORAMA) {
                                    PanoramaOverlay(
                                        controller = panorama,
                                        modifier = Modifier.fillMaxSize(),
                                        onToggleDirection = {
                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                            panorama.toggleDirection()
                                        }
                                    )
                                }

                                if (isGridEnabled && currentMode != CameraMode.SEARCH && currentMode != CameraMode.PANORAMA) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val gridColor = Color.White.copy(alpha = 0.32f)
                                        val strokeW = 1.dp.toPx()

                                        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeW)
                                        drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeW)
                                        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeW)
                                        drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeW)
                                    }
                                }

                                if (isLevelerEnabled && currentMode != CameraMode.SEARCH && currentMode != CameraMode.PANORAMA) {
                                    val levelColor = if (isPhoneLevel) uiColor else Color.White.copy(alpha = 0.65f)
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(
                                            modifier = Modifier
                                                .size(160.dp, 60.dp)
                                                .graphicsLayer { rotationZ = currentRoll }
                                        ) {
                                            val cX = size.width / 2f
                                            val cY = size.height / 2f
                                            val lineLen = 54.dp.toPx()
                                            val gap = 14.dp.toPx()
                                            val strokeWidth = if (isPhoneLevel) 2.5.dp.toPx() else 1.5.dp.toPx()

                                            drawLine(
                                                color = levelColor,
                                                start = Offset(cX - gap - lineLen, cY),
                                                end = Offset(cX - gap, cY),
                                                strokeWidth = strokeWidth,
                                                cap = StrokeCap.Round
                                            )
                                            drawLine(
                                                color = levelColor,
                                                start = Offset(cX + gap, cY),
                                                end = Offset(cX + gap + lineLen, cY),
                                                strokeWidth = strokeWidth,
                                                cap = StrokeCap.Round
                                            )
                                            drawCircle(
                                                color = levelColor,
                                                radius = if (isPhoneLevel) 3.5.dp.toPx() else 2.5.dp.toPx(),
                                                center = Offset(cX, cY)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .offset(y = 38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isPhoneLevel) uiColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f))
                                                .border(1.dp, if (isPhoneLevel) uiColor else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            // Own composable: the roll value changes ~15x/s, and this keeps that
                                            // recomposition limited to the label instead of the whole viewfinder.
                                            LevelReadout(
                                                rollProvider = { currentRoll },
                                                isLevel = isPhoneLevel,
                                                uiColor = uiColor
                                            )
                                        }
                                    }
                                }

                                // Blurred cover while the aspect ratio animates or the camera flips (plain dark when blur is off). Both animated
                                // values are read in the draw phase only, so this costs no recomposition.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawBehind {
                                            // While the ratio animates, the live preview is covered by the blurred
                                            // camera snapshot (already on hand for the glass controls), so the
                                            // TextureView resizing underneath is never seen.
                                            val blurA = maxOf(aspectBlur.value, switchBlurProgress.value)
                                            if (blurA > 0.001f) {
                                                val img = cameraBackdrop.image
                                                if (img != null) {
                                                    cameraBackdrop.frame // keep following the snapshot
                                                    drawImage(
                                                        image = img,
                                                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                                        alpha = blurA,
                                                        filterQuality = FilterQuality.Medium
                                                    )
                                                    drawRect(Color.Black.copy(alpha = 0.18f * blurA))
                                                } else {
                                                    drawRect(Color.Black.copy(alpha = 0.7f * blurA))
                                                }
                                            }
                                        }
                                )

                                AnimatedVisibility(
                                    visible = showBlink,
                                    enter = fadeIn(animationSpec = tween(60)),
                                    exit = fadeOut(animationSpec = tween(300)),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)))
                                }

                                // Focus box + exposure sun (tap the preview). Not in Search mode, which has its own touch handling.
                                if (currentMode != CameraMode.SEARCH) {
                                    val exposureInfo = cameraController.cameraInfo?.exposureState
                                    val exposureRange = exposureInfo?.exposureCompensationRange
                                    val exposureSupported = exposureInfo != null && exposureInfo.isExposureCompensationSupported &&
                                        exposureRange != null && (exposureRange.upper > 0 || exposureRange.lower < 0)
                                    FocusExposureOverlay(
                                        point = focusPoint,
                                        tapCount = focusTapCount,
                                        visible = focusVisible,
                                        exposure = exposureValue,
                                        // Pro mode has its own brightness slider, so the sun is not shown there
                                        showExposure = exposureSupported && currentMode != CameraMode.PRO,
                                        onExposureChange = { value ->
                                            exposureValue = value
                                            val range = cameraController.cameraInfo?.exposureState?.exposureCompensationRange
                                            if (range != null) {
                                                // map -1..1 onto this phone's own range (for example -12..12 steps)
                                                val index = (if (value >= 0f) value * range.upper else value * -range.lower)
                                                    .roundToInt()
                                                    .coerceIn(range.lower, range.upper)
                                                if (index != exposureIndexApplied) {
                                                    exposureIndexApplied = index
                                                    try {
                                                        cameraController.cameraControl?.setExposureCompensationIndex(index)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                    triggerHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                                }
                                            }
                                        },
                                        onTouchChange = { exposureTouching = it },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                if (isWatermarkEnabled && selectedWatermark != WatermarkProfile.OFF) {
                                    val watermarkText = when (selectedWatermark) {
                                        WatermarkProfile.AI_CAMERA -> "AI Camera"
                                        WatermarkProfile.SHOT_ON -> "Shot on $deviceName"
                                        WatermarkProfile.DATETIME -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                        WatermarkProfile.OFF -> ""
                                    }
                                    if (watermarkText.isNotEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                                            Text(
                                                text = watermarkText,
                                                color = Color.White.copy(alpha = 0.65f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = burstToastMessage != null,
                                    enter = fadeIn(animationSpec = tween(150)) + slideInVertically(animationSpec = tween(200)) { -it },
                                    exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(180)) { -it }
                                ) {
                                    burstToastMessage?.let { msg ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(top = 110.dp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            LiquidGlassContainer(
                                                shape = CircleShape
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.PhotoLibrary,
                                                        contentDescription = "Burst Photos",
                                                        tint = uiColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = msg,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isBursting && isBurstMotionExcessive,
                                    enter = fadeIn(animationSpec = tween(150)) + slideInVertically(animationSpec = tween(220)) { -it / 2 } + scaleIn(initialScale = 0.92f),
                                    exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(180)) { -it / 2 } + scaleOut(targetScale = 0.92f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 90.dp, start = 20.dp, end = 20.dp),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "MotionPulse")
                                        val pulseAlpha by infiniteTransition.animateFloat(
                                            initialValue = 0.8f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(400, easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "PulseAlpha"
                                        )
                                        val pulseBorderColor = Color(0xFFFFB300).copy(alpha = pulseAlpha)

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(Color(0xF21F180A))
                                                .border(1.5.dp, pulseBorderColor, RoundedCornerShape(22.dp))
                                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFFB300).copy(alpha = 0.22f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.WarningAmber,
                                                        contentDescription = "Excessive Motion Warning",
                                                        tint = Color(0xFFFFB300),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Column {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Hold Camera Steady",
                                                            color = Color(0xFFFFE082),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFFFB300))
                                                        )
                                                    }
                                                    Text(
                                                        text = "Excessive motion detected • Keep steady for sharper burst shots",
                                                        color = Color.White.copy(alpha = 0.9f),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Translucent black bars behind the floating controls.
                        // Top bar: off by default, Settings > Control bars turns it on.
                        // Bottom bar: shown only while the zoom dial is on. It fades in and out at the same time as the dial.
                        AnimatedVisibility(
                            visible = isEdgeBarsEnabled && currentMode != CameraMode.SEARCH && currentMode != CameraMode.PANORAMA && !immersive,
                            enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(300)),
                            exit = slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(300)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 62.dp)
                        ) {
                            TranslucentEdgeBar(isTop = true, modifier = Modifier.fillMaxSize())
                        }
                        if (zoomDialInTree) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 274.dp)
                                    .graphicsLayer { alpha = zoomDialFade.value }
                            ) {
                                TranslucentEdgeBar(isTop = false, modifier = Modifier.fillMaxSize())
                            }
                        }

                        // White glow: blinks at the exact bottom-centre of the screen, spreads over everything,
                        // with ~3s of sparkles, then stays as a soft edge glow while Gemini is working
                        SearchGlowOverlay(
                            active = isSearchingWithGemini,
                            trigger = searchTrigger,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Status line in the middle of the screen ("Searching", "Parsing the results", ...)
                        SearchStatusLabel(
                            visible = isSearchingWithGemini,
                            query = searchQueryText.ifBlank { null },
                            modifier = Modifier.align(Alignment.Center)
                        )

                        // Countdown (timer or palm gesture): a glass circle at the top centre. It slides in with a little
                        // bounce, and every new number MORPHS out of the old one: the old digit swells, blurs and melts
                        // away while the new one condenses out of a blur from a smaller size (like a numeric text morph).
                        // The last number is remembered so the circle can leave without flashing a "0".
                        val timerDigitMemory = remember { IntArray(1) { 1 } }
                        if (activeTimer > 0) timerDigitMemory[0] = activeTimer
                        val shownTimerDigit = timerDigitMemory[0]
                        AnimatedVisibility(
                            visible = activeTimer > 0,
                            enter = fadeIn(tween(220)) +
                                scaleIn(
                                    initialScale = 0.55f,
                                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
                                ) +
                                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 2 },
                            exit = fadeOut(tween(200)) +
                                scaleOut(targetScale = 0.7f, animationSpec = tween(200)) +
                                slideOutVertically(tween(240, easing = FastOutLinearInEasing)) { -it / 2 },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 10.dp)
                        ) {
                            LiquidGlassContainer(
                                shape = CircleShape,
                                modifier = Modifier.size(68.dp)
                            ) {
                                AnimatedContent(
                                    targetState = shownTimerDigit,
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                    transitionSpec = {
                                        (fadeIn(tween(260, delayMillis = 90)) +
                                            scaleIn(
                                                initialScale = 0.35f,
                                                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
                                            ))
                                            .togetherWith(
                                                fadeOut(tween(170)) +
                                                    scaleOut(targetScale = 1.7f, animationSpec = tween(260, easing = FastOutLinearInEasing))
                                            )
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "TimerDigitMorph"
                                ) { digit ->
                                    // the entering digit starts blurred and sharpens, the leaving digit blurs as it melts away
                                    val digitBlur by transition.animateDp(label = "TimerDigitBlur") { state ->
                                        if (state == EnterExitState.Visible) 0.dp else 10.dp
                                    }
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$digit",
                                            color = uiColor,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.blur(digitBlur, BlurredEdgeTreatment.Unbounded)
                                        )
                                    }
                                }
                            }
                        }

                        // Tap anywhere outside the floating top bar (the Filter / Ratio / Watermark / Timer tabs and their
                        // options) to close it. This is a transparent layer over the viewfinder but below the top controls
                        // and the bottom controls, so those keep working normally; everything else on screen lands here.
                        // It is only there while the bar is actually visible: Search mode and recording/panorama hide the
                        // bar, and then this layer must not block the circle-to-search drawing or the preview.
                        if (isTopDropdownOpen && !immersive && currentMode != CameraMode.SEARCH) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                            isTopDropdownOpen = false
                                            activeQuickSetting = null
                                        }
                                    }
                            )
                        }

                        var settingsRotationTarget by remember { mutableFloatStateOf(0f) }
                        val settingsRotation by animateFloatAsState(
                            targetValue = settingsRotationTarget,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                            label = "SettingsRotation"
                        )


                        val arrowRotation by animateFloatAsState(
                            targetValue = if (isTopDropdownOpen) 180f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "TopArrowRotation"
                        )

                        // Flashlight toggle. It lives outside the top row that slides away while recording or sweeping a
                        // panorama, so the flashlight stays reachable in video.
                        val flashToggleButton: @Composable (Modifier) -> Unit = { flashModifier ->
                                        LiquidGlassContainer(
                                            shape = CircleShape,
                                            modifier = flashModifier
                                                .size(46.dp)
                                                .testTag("flash_toggle_button")
                                                .bouncyClickable {
                                                    flashEnabled = !flashEnabled
                                                    flashToastText = if (flashEnabled) "⚡ Flashlight ON" else "⚡ Flashlight OFF"
                                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    try {
                                                        val hasFlash = cameraController.cameraInfo?.hasFlashUnit() == true
                                                        if (hasFlash && !isFrontCamera) {
                                                            cameraController.enableTorch(flashEnabled)
                                                        }
                                                        cameraController.imageCaptureFlashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                AnimatedContent(
                                                    targetState = flashEnabled,
                                                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                                                    label = "FlashIconFade"
                                                ) { on ->
                                                    Icon(
                                                        imageVector = if (on) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                                        contentDescription = if (on) "Flashlight On" else "Flashlight Off",
                                                        tint = if (on) Color(0xFFFFD600) else Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 10.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AnimatedVisibility(
                                visible = isRecording,
                                enter = slideInVertically(
                                    initialOffsetY = { -it },
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeIn(animationSpec = tween(220)),
                                exit = slideOutVertically(
                                    targetOffsetY = { -it },
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeOut(animationSpec = tween(180))
                            ) {
                                val totalSeconds = (recordingDurationNanos / 1_000_000_000L).toInt()
                                val minutes = totalSeconds / 60
                                val seconds = totalSeconds % 60
                                val formattedTime = String.format("%02d:%02d", minutes, seconds)
                                val infiniteTransition = rememberInfiniteTransition(label = "RecBlink")
                                val blinkAlpha by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "BlinkAlpha"
                                )

                                LiquidGlassContainer(
                                    shape = CircleShape,
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .graphicsLayer { alpha = if (isRecordingPaused) 1f else blinkAlpha }
                                                .clip(CircleShape)
                                                .background(if (isRecordingPaused) Color(0xFFFF9500) else Color(0xFFFF3B30))
                                        )
                                        Text(
                                            text = if (isRecordingPaused) "PAUSED $formattedTime" else "REC $formattedTime",
                                            color = Color.White,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "$currentVideoRes",
                                            color = uiColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !immersive && currentMode != CameraMode.SEARCH,
                                enter = slideInVertically(
                                    initialOffsetY = { -it },
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeIn(animationSpec = tween(220)),
                                exit = slideOutVertically(
                                    targetOffsetY = { -it },
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                ) + fadeOut(animationSpec = tween(180))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .zIndex(0f)
                                        ) {
                                            Canvas(modifier = Modifier.size(54.dp, 24.dp)) {
                                                drawArc(
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    startAngle = 0f,
                                                    sweepAngle = 180f,
                                                    useCenter = false,
                                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                                )
                                            }
                                        }

                                        // Center Dropdown Trigger Pill (Frosted Liquid Glass)
                                        LiquidGlassContainer(
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .zIndex(1f)
                                                .height(42.dp)
                                                .bouncyClickable {
                                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    isTopDropdownOpen = !isTopDropdownOpen
                                                    if (!isTopDropdownOpen) {
                                                        activeQuickSetting = null
                                                    }
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            ) {
                                                val pillLabel = if (currentMode == CameraMode.SEARCH) "Gemini AI · Search" else if (currentMode == CameraMode.PRO) "Pro · Manual" else if (currentMode == CameraMode.PANORAMA) "Panorama · Live" else if (isVideoMode) "$currentVideoRes · ${currentFps}fps" + (if (effectiveHorizonMode != HorizonLockMode.OFF) " · Lock" else "") else "${currentAspect} · ${selectedFilter.title}"
                                                AnimatedContent(
                                                    targetState = pillLabel,
                                                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                                                    label = "PillLabelFade"
                                                ) { label ->
                                                    Text(
                                                        text = label,
                                                        color = Color.White,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.widthIn(max = 140.dp)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Options",
                                                    tint = uiColor,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .graphicsLayer {
                                                            rotationZ = arrowRotation
                                                        }
                                                )
                                            }
                                        }

                                        // Settings Screen Trigger Button (Frosted Liquid Glass)
                                        LiquidGlassContainer(
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .size(44.dp)
                                                .bouncyClickable {
                                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    settingsRotationTarget += 180f
                                                    currentScreen = "settings"
                                                }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Settings,
                                                    contentDescription = "Settings",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .graphicsLayer {
                                                            rotationZ = settingsRotation
                                                        }
                                                )
                                            }
                                        }
                                    }

                                    run {
                                        val quickTabs = if (isVideoMode) {
                                            // Horizon lock is experimental: no tab until the Experimental lab is unlocked
                                            if (isExperimentalUnlocked) listOf("Quality", "FPS", "Horizon", "Timer")
                                            else listOf("Quality", "FPS", "Timer")
                                        } else {
                                            listOf("Filter", "Ratio", "Watermark", "Timer")
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val tabItemWidth = if (quickTabs.size > 4) 58.dp else 78.dp
                                            val barItemHeight = 34.dp
                                            val selectedTabIndex = quickTabs.indexOf(activeQuickSetting)
                                            val isAnyTabSelected = selectedTabIndex >= 0
                                            // keeps the last tab's options on screen while the sub-options pill melts away
                                            val lastQuickTab = remember { arrayOfNulls<String>(1) }
                                            if (activeQuickSetting != null) lastQuickTab[0] = activeQuickSetting
                                            val shownTab = activeQuickSetting ?: lastQuickTab[0]

                                            val categoryIndicatorOffset by animateDpAsState(
                                                targetValue = if (isAnyTabSelected) tabItemWidth * selectedTabIndex else 0.dp,
                                                animationSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                label = "CategoryIndicatorOffset"
                                            )

                                            // liquid morph: the tab bar grows out of the trigger pill as a droplet and melts back into it
                                            LiquidMorphSurface(expanded = isTopDropdownOpen, modifier = Modifier.padding(top = 10.dp)) { morph ->
                                            LiquidGlassPill(
                                                // a tap on the bar's own glass (between the options) must not count as "outside"
                                                modifier = Modifier
                                                    .wrapContentWidth()
                                                    .pointerInput(Unit) { detectTapGestures { } }
                                            ) {
                                                Box(
                                                    modifier = morph.padding(4.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (isAnyTabSelected) {
                                                        LiquidIndicator(offset = categoryIndicatorOffset, itemWidth = tabItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = tabItemWidth * quickTabs.size)
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        quickTabs.forEach { tab ->
                                                            val isSelected = activeQuickSetting == tab
                                                            val textColor by animateColorAsState(
                                                                targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                animationSpec = tween(200),
                                                                label = "TabTextColor"
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(tabItemWidth)
                                                                    .height(barItemHeight)
                                                                    .clip(CircleShape)
                                                                    .clickable(
                                                                        interactionSource = remember { MutableInteractionSource() },
                                                                        indication = null
                                                                    ) {
                                                                        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                        activeQuickSetting = if (activeQuickSetting == tab) null else tab
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = tab,
                                                                    color = textColor,
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    fontSize = if (quickTabs.size > 4) 11.sp else 12.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            }

                                                // liquid morph: the sub-options pill grows out of the tab bar as a droplet and melts back into it
                                                LiquidMorphSurface(expanded = activeQuickSetting != null) { morph ->
                                                LiquidGlassPill(
                                                // a tap on the bar's own glass (between the options) must not count as "outside"
                                                modifier = Modifier
                                                    .wrapContentWidth()
                                                    .pointerInput(Unit) { detectTapGestures { } }
                                            ) {
                                                    Box(modifier = morph.padding(4.dp), contentAlignment = Alignment.Center) {
                                                        AnimatedContent(
                                                            targetState = shownTab,
                                                            transitionSpec = {
                                                                fadeIn(animationSpec = tween(140)) togetherWith fadeOut(animationSpec = tween(90))
                                                            },
                                                            label = "SubOptionsContent"
                                                        ) { currentTab ->
                                                            when (currentTab) {
                                                                "Ratio" -> {
                                                                    val subItemWidth = 56.dp
                                                                    val selectedRatioIdx = aspectRatios.indexOf(currentAspect).coerceAtLeast(0)
                                                                    val ratioOffset by animateDpAsState(
                                                                        targetValue = subItemWidth * selectedRatioIdx,
                                                                        animationSpec = spring(
                                                                            dampingRatio = 0.82f,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        ),
                                                                        label = "RatioIndicatorOffset"
                                                                    )
                                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                                        LiquidIndicator(offset = ratioOffset, itemWidth = subItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = subItemWidth * aspectRatios.size)
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            aspectRatios.forEach { aspect ->
                                                                                val isSelected = currentAspect == aspect
                                                                                val textColor by animateColorAsState(
                                                                                    targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                                    animationSpec = tween(200),
                                                                                    label = "RatioTextColor"
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .width(subItemWidth)
                                                                                        .height(barItemHeight)
                                                                                        .clip(CircleShape)
                                                                                        .clickable(
                                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                                            indication = null
                                                                                        ) {
                                                                                            currentAspect = aspect
                                                                                            prefs.edit().putString("current_aspect", aspect).apply()
                                                                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                                        },
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = aspect,
                                                                                        color = textColor,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                "Watermark" -> {
                                                                    val wmList = WatermarkProfile.values().toList()
                                                                    val subItemWidth = 58.dp
                                                                    val selectedWmIdx = wmList.indexOf(selectedWatermark).coerceAtLeast(0)
                                                                    val wmOffset by animateDpAsState(
                                                                        targetValue = subItemWidth * selectedWmIdx,
                                                                        animationSpec = spring(
                                                                            dampingRatio = 0.82f,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        ),
                                                                        label = "WmIndicatorOffset"
                                                                    )
                                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                                        LiquidIndicator(offset = wmOffset, itemWidth = subItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = subItemWidth * wmList.size)
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            wmList.forEach { profile ->
                                                                                val isSelected = selectedWatermark == profile
                                                                                val textColor by animateColorAsState(
                                                                                    targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                                    animationSpec = tween(200),
                                                                                    label = "WmTextColor"
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .width(subItemWidth)
                                                                                        .height(barItemHeight)
                                                                                        .clip(CircleShape)
                                                                                        .clickable(
                                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                                            indication = null
                                                                                        ) {
                                                                                            selectedWatermark = profile
                                                                                            isWatermarkEnabled = profile != WatermarkProfile.OFF
                                                                                            prefs.edit()
                                                                                                .putString("selected_watermark", profile.name)
                                                                                                .putBoolean("watermark_enabled", isWatermarkEnabled)
                                                                                                .apply()
                                                                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                                        },
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = profile.label,
                                                                                        color = textColor,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                "Horizon" -> {
                                                                    val horizonOptions = HorizonLockMode.values().toList()
                                                                    val subItemWidth = 58.dp
                                                                    val selectedHorizonIdx = horizonOptions.indexOf(horizonMode).coerceAtLeast(0)
                                                                    val horizonOffset by animateDpAsState(
                                                                        targetValue = subItemWidth * selectedHorizonIdx,
                                                                        animationSpec = spring(
                                                                            dampingRatio = 0.82f,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        ),
                                                                        label = "HorizonIndicatorOffset"
                                                                    )
                                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                                        LiquidIndicator(offset = horizonOffset, itemWidth = subItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = subItemWidth * horizonOptions.size)
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            horizonOptions.forEach { option ->
                                                                                val isSelected = horizonMode == option
                                                                                val textColor by animateColorAsState(
                                                                                    targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                                    animationSpec = tween(200),
                                                                                    label = "HorizonTextColor"
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .width(subItemWidth)
                                                                                        .height(barItemHeight)
                                                                                        .clip(CircleShape)
                                                                                        .clickable(
                                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                                            indication = null
                                                                                        ) {
                                                                                            if (!isRecording) {
                                                                                                horizonMode = option
                                                                                                prefs.edit().putString("horizon_lock", option.name).apply()
                                                                                                triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                                            }
                                                                                        },
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = option.label,
                                                                                        color = textColor,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                "Timer" -> {
                                                                    val timerOptions = listOf(0 to "Off", 3 to "3s", 5 to "5s", 10 to "10s")
                                                                    val subItemWidth = 58.dp
                                                                    val selectedTimerIdx = timerOptions.indexOfFirst { it.first == currentTimer }.coerceAtLeast(0)
                                                                    val timerOffset by animateDpAsState(
                                                                        targetValue = subItemWidth * selectedTimerIdx,
                                                                        animationSpec = spring(
                                                                            dampingRatio = 0.82f,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        ),
                                                                        label = "TimerIndicatorOffset"
                                                                    )
                                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                                        LiquidIndicator(offset = timerOffset, itemWidth = subItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = subItemWidth * timerOptions.size)
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            timerOptions.forEach { option ->
                                                                                val isSelected = currentTimer == option.first
                                                                                val textColor by animateColorAsState(
                                                                                    targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                                    animationSpec = tween(200),
                                                                                    label = "TimerTextColor"
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .width(subItemWidth)
                                                                                        .height(barItemHeight)
                                                                                        .clip(CircleShape)
                                                                                        .clickable(
                                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                                            indication = null
                                                                                        ) {
                                                                                            currentTimer = option.first
                                                                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                                        },
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = option.second,
                                                                                        color = textColor,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                "Filter" -> {
                                                                    val filterList = ColorFilterProfile.values().toList()
                                                                    val subItemWidth = 60.dp
                                                                    val selectedFilterIdx = filterList.indexOf(selectedFilter).coerceAtLeast(0)
                                                                    val filterOffset by animateDpAsState(
                                                                        targetValue = subItemWidth * selectedFilterIdx,
                                                                        animationSpec = spring(
                                                                            dampingRatio = 0.82f,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        ),
                                                                        label = "FilterIndicatorOffset"
                                                                    )
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .horizontalScroll(rememberScrollState())
                                                                            .padding(horizontal = 2.dp),
                                                                        contentAlignment = Alignment.CenterStart
                                                                    ) {
                                                                        LiquidIndicator(offset = filterOffset, itemWidth = subItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = subItemWidth * filterList.size)
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            filterList.forEach { filter ->
                                                                                val isSelected = selectedFilter == filter
                                                                                val textColor by animateColorAsState(
                                                                                    targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                                    animationSpec = tween(200),
                                                                                    label = "FilterTextColor"
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .width(subItemWidth)
                                                                                        .height(barItemHeight)
                                                                                        .clip(CircleShape)
                                                                                        .clickable(
                                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                                            indication = null
                                                                                        ) {
                                                                                            selectedFilter = filter
                                                                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                                        },
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = filter.title,
                                                                                        color = textColor,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                "Quality" -> {
                                                                    val subItemWidth = 56.dp
                                                                    val selectedResIdx = videoResolutions.indexOf(currentVideoRes).coerceAtLeast(0)
                                                                    val resOffset by animateDpAsState(
                                                                        targetValue = subItemWidth * selectedResIdx,
                                                                        animationSpec = spring(
                                                                            dampingRatio = 0.82f,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        ),
                                                                        label = "QualityIndicatorOffset"
                                                                    )
                                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                                        LiquidIndicator(offset = resOffset, itemWidth = subItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = subItemWidth * videoResolutions.size)
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            videoResolutions.forEach { res ->
                                                                                val isSelected = currentVideoRes == res
                                                                                val textColor by animateColorAsState(
                                                                                    targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                                    animationSpec = tween(200),
                                                                                    label = "QualityTextColor"
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .width(subItemWidth)
                                                                                        .height(barItemHeight)
                                                                                        .clip(CircleShape)
                                                                                        .clickable(
                                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                                            indication = null
                                                                                        ) {
                                                                                            currentVideoRes = res
                                                                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                                        },
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = res,
                                                                                        color = textColor,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                "FPS" -> {
                                                                    val subItemWidth = 58.dp
                                                                    val selectedFpsIdx = supportedFps.indexOf(currentFps).coerceAtLeast(0)
                                                                    val fpsOffset by animateDpAsState(
                                                                        targetValue = subItemWidth * selectedFpsIdx,
                                                                        animationSpec = spring(
                                                                            dampingRatio = 0.82f,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        ),
                                                                        label = "FpsIndicatorOffset"
                                                                    )
                                                                    Box(contentAlignment = Alignment.CenterStart) {
                                                                        LiquidIndicator(offset = fpsOffset, itemWidth = subItemWidth, itemHeight = barItemHeight, color = uiColor, trackWidth = subItemWidth * supportedFps.size)
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            supportedFps.forEach { fps ->
                                                                                val isSelected = currentFps == fps
                                                                                val textColor by animateColorAsState(
                                                                                    targetValue = if (isSelected) Color(0xFF121214) else Color(0xFFD1D1D6),
                                                                                    animationSpec = tween(200),
                                                                                    label = "FpsTextColor"
                                                                                )
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .width(subItemWidth)
                                                                                        .height(barItemHeight)
                                                                                        .clip(CircleShape)
                                                                                        .clickable(
                                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                                            indication = null
                                                                                        ) {
                                                                                            currentFps = fps
                                                                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                                        },
                                                                                    contentAlignment = Alignment.Center
                                                                                ) {
                                                                                    Text(
                                                                                        text = "${fps}fps",
                                                                                        color = textColor,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = currentMode == CameraMode.SEARCH && !isSearchingWithGemini && geminiSearchResult == null,
                                    enter = slideInVertically(
                                        initialOffsetY = { -it },
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                    ) + fadeIn(animationSpec = tween(220)),
                                    exit = slideOutVertically(
                                        targetOffsetY = { -it },
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                    ) + fadeOut(animationSpec = tween(180))
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        LiquidGlassContainer(
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .size(46.dp)
                                                .testTag("search_flash_toggle_button")
                                                .bouncyClickable {
                                                    flashEnabled = !flashEnabled
                                                    flashToastText = if (flashEnabled) "⚡ Flashlight ON" else "⚡ Flashlight OFF"
                                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    try {
                                                        val hasFlash = cameraController.cameraInfo?.hasFlashUnit() == true
                                                        if (hasFlash && !isFrontCamera) {
                                                            cameraController.enableTorch(flashEnabled)
                                                        }
                                                        cameraController.imageCaptureFlashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                AnimatedContent(
                                                    targetState = flashEnabled,
                                                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                                                    label = "FlashIconFade"
                                                ) { on ->
                                                    Icon(
                                                        imageVector = if (on) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                                        contentDescription = if (on) "Flashlight On" else "Flashlight Off",
                                                        tint = if (on) Color(0xFFFFD600) else Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        LiquidGlassContainer(
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .size(44.dp)
                                                .bouncyClickable {
                                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    currentMode = CameraMode.PHOTO
                                                }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Exit Search Mode",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        // Floating model selector, top centre
                                        GeminiModelPill(
                                            model = selectedGeminiModel,
                                            expanded = isModelMenuOpen,
                                            onClick = {
                                                triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                isModelMenuOpen = !isModelMenuOpen
                                            },
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }

                                    GeminiModelMenu(
                                        visible = isModelMenuOpen,
                                        selected = selectedGeminiModel,
                                        onSelect = { model ->
                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                            selectedGeminiModel = model
                                            prefs.edit().putString("gemini_model", model.id).apply()
                                            isModelMenuOpen = false
                                        },
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                    }
                                }
                            }

                            // always visible (Search mode has its own flash button)
                            if (currentMode != CameraMode.SEARCH) {
                                flashToggleButton(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 16.dp, top = 1.dp)
                                        .graphicsLayer {
                                            val hide = timerChromeHide.value
                                            alpha = 1f - hide
                                            translationY = -hide * 70.dp.toPx()
                                        }
                                        .then(if (timerCountdown) Modifier.blockTouches() else Modifier)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {

                            AnimatedContent(
                                targetState = when (currentMode) {
                                    CameraMode.SEARCH -> 1
                                    CameraMode.PRO -> 2
                                    else -> 0
                                },
                                modifier = Modifier
                                    .graphicsLayer {
                                        val hide = immersiveProgress.value
                                        alpha = chromeAlpha * (1f - hide)
                                        // zoom presets sit a bit higher than before (-10dp)
                                        translationY = hide * 90.dp.toPx() - 10.dp.toPx()
                                    }
                                    .then(if (immersive) Modifier.blockTouches() else Modifier),
                                transitionSpec = {
                                    // No sliding: the old panel fades out and the new one fades in. Pro mode's chips fade
                                    // in one by one on their own (see ProPanel), so Pro enters without a fade of its own.
                                    val enter = if (targetState == 2) EnterTransition.None else fadeIn(tween(240, delayMillis = 80))
                                    (enter togetherWith fadeOut(tween(140))).using(SizeTransform(clip = false))
                                },
                                label = "BottomPanelFade"
                            ) { kind ->
                                if (kind == 1) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SearchAnythingBar(
                                        onSubmit = { q ->
                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                            val bmp = previewView?.bitmap
                                            if (bmp != null) {
                                                searchQueryText = q
                                                performGeminiSearch(bmp, q)
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp).imePadding()
                                    )
                                    val searchSuggestions = listOf(
                                        "Identify object",
                                        "Translate text",
                                        "Find on Web",
                                        "Solve math",
                                        "Summarize scene"
                                    )
                                    // The chips sit still. The user scrolls them sideways by hand.
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        searchSuggestions.forEach { suggestion ->
                                            LiquidGlassContainer(
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .bouncyClickable {
                                                        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        val bmp = previewView?.bitmap
                                                        if (bmp != null) {
                                                            searchQueryText = suggestion
                                                            performGeminiSearch(bmp, suggestion)
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = suggestion,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                } else if (kind == 2) {
                                    ProPanel(
                                        pro = pro,
                                        uiColor = uiColor,
                                        onHaptic = { triggerHaptic(HapticFeedbackConstants.CLOCK_TICK) }
                                    )
                                } else {
                                    // Panorama has no zoom: the presets fade out (they keep their space, so nothing jumps)
                                    // and take no touches.
                                    val noZoom = currentMode == CameraMode.PANORAMA
                                    val zoomRowAlpha = animateFloatAsState(
                                        targetValue = if (noZoom) 0f else 1f,
                                        animationSpec = tween(250),
                                        label = "ZoomRowAlpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer { alpha = zoomRowAlpha.value }
                                            .then(if (noZoom) Modifier.blockTouches() else Modifier)
                                    ) {
                                        ExactZoomRow(currentZoom, uiColor, maxZoom, minZoom = minZoom) { zoom ->
                                            currentZoom = zoom
                                            triggerHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                            cameraController.setZoomRatio(zoom)
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = isQuickVideoActive && !isRecordingLocked,
                                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                                exit = fadeOut() + scaleOut(targetScale = 0.8f)
                            ) {
                                LiquidGlassContainer(
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "Slide up to lock | Swipe sideways for burst",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    // shutter row (shutter + side buttons) sits a bit higher than before (-10dp)
                                    .graphicsLayer { translationY = -10.dp.toPx() }
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentMode == CameraMode.SEARCH) {
                                    Box(modifier = Modifier.graphicsLayer { alpha = chromeAlpha }) {
                                        LiquidGlassContainer(
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .testTag("search_pick_image_button")
                                                .bouncyClickable {
                                                    if (!isSearchingWithGemini) {
                                                        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        pickSearchImage.launch("image/*")
                                                    }
                                                }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.PhotoLibrary,
                                                    contentDescription = "Pick an image to search",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (currentMode != CameraMode.SEARCH) {
                                    Box(
                                        modifier = Modifier
                                                .graphicsLayer {
                                                    val hide = panoSideHide.value
                                                    alpha = 1f - hide
                                                    translationY = hide * 70.dp.toPx()
                                                }
                                                .then(if (sideHidden) Modifier.blockTouches() else Modifier)
                                    ) {
                                        if (isRecording) {
                                            LiquidGlassContainer(
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .bouncyClickable {
                                                        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        takePhoto()
                                                    }
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CameraAlt,
                                                        contentDescription = "Take Photo While Recording",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            if (lastCapturedUri != null) {
                                                AsyncImage(
                                                    model = lastCapturedUri,
                                                    contentDescription = "Gallery",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(CircleShape)
                                                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                                        .bouncyClickable {
                                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                            lastCapturedUri?.let { uri ->
                                                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                                    setDataAndType(uri, if (isVideoMode) "video/*" else "image/*")
                                                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                                }
                                                                try {
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                }
                                                            }
                                                        }
                                                )
                                            } else {
                                                LiquidGlassContainer(
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(52.dp)
                                                ) {}
                                            }
                                        }
                                    }
                                }

                                val isVideo = isVideoMode
                                val isTimerActive = timerJob?.isActive == true
                                Box(modifier = Modifier.graphicsLayer { alpha = chromeAlpha }) {
                                    ExactShutterButton(
                                        isVideoMode = isVideo,
                                        isSearchMode = currentMode == CameraMode.SEARCH,
                                        isPanoramaMode = currentMode == CameraMode.PANORAMA,
                                        isPanoramaCapturing = panorama.capturing,
                                        isRecording = isRecording,
                                        isRecordingLocked = isRecordingLocked,
                                        isBursting = isBursting,
                                        burstCount = burstCount,
                                        isTimerRunning = isTimerActive,
                                        uiColor = uiColor,
                                        onClick = {
                                            triggerHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
                                            if (isSearchingWithGemini) return@ExactShutterButton
                                            if (currentMode == CameraMode.PANORAMA) {
                                                // first tap starts the sweep, the next tap finishes and saves it
                                                if (!panorama.saving) panorama.toggle(currentZoom)
                                                return@ExactShutterButton
                                            }
                                            if (currentMode == CameraMode.SEARCH) {
                                                val bmp = previewView?.bitmap
                                                if (bmp != null) {
                                                    performGeminiSearch(bmp, searchQueryText.ifBlank { null })
                                                }
                                                return@ExactShutterButton
                                            }
                                            if (timerJob?.isActive == true) {
                                                timerJob?.cancel()
                                                activeTimer = 0
                                                return@ExactShutterButton
                                            }
                                            if (isRecordingLocked || (isVideo && isRecording)) {
                                                stopVideoRecording()
                                            } else if (currentTimer > 0) {
                                                timerJob = scope.launch {
                                                    for (i in currentTimer downTo 1) {
                                                        activeTimer = i
                                                        SoundUtils.playTimerTick(currentTimerSound)
                                                        delay(1000)
                                                    }
                                                    activeTimer = 0
                                                    if (isVideo) startVideoRecording() else takePhoto()
                                                }
                                            } else {
                                                if (isVideo) startVideoRecording() else takePhoto()
                                            }
                                        },
                                        onLongPressStart = {
                                            if (currentMode != CameraMode.SEARCH && currentMode != CameraMode.PANORAMA && timerJob?.isActive != true && !isRecording && !isBursting) {
                                                isQuickVideoActive = true
                                                isRecordingLocked = false
                                                startVideoRecording()
                                            }
                                        },
                                        onSwipeUpLock = {
                                            if ((isQuickVideoActive || isRecording) && !isRecordingLocked) {
                                                isRecordingLocked = true
                                                triggerHaptic(HapticFeedbackConstants.CONFIRM)
                                                currentMode = CameraMode.VIDEO
                                                isQuickVideoActive = false
                                                isRecordingLocked = false
                                            }
                                        },
                                        onSwipeHorizontalBurst = {
                                            if (isQuickVideoActive && !isRecordingLocked && isRecording) {
                                                stopVideoRecording()
                                            }
                                            if (!isBursting && timerJob?.isActive != true && !isVideo && currentMode != CameraMode.PANORAMA) {
                                                isBursting = true
                                                burstCount = 0
                                                burstJob?.cancel()
                                                burstJob = scope.launch {
                                                    while (isBursting) {
                                                        burstCount++
                                                        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        takePhoto()
                                                        delay(120)
                                                    }
                                                }
                                            }
                                        },
                                        onRelease = {
                                            if (isBursting) {
                                                val captured = burstCount
                                                isBursting = false
                                                isBurstMotionExcessive = false
                                                lastExcessiveMotionTimestamp = 0L
                                                burstMotionMagnitude = 0f
                                                burstJob?.cancel()
                                                if (captured > 1) {
                                                    burstToastMessage = "Burst • $captured photos captured"
                                                    scope.launch {
                                                        delay(2400)
                                                        if (burstToastMessage?.startsWith("Burst • $captured") == true) {
                                                            burstToastMessage = null
                                                        }
                                                    }
                                                }
                                            }
                                            if (isQuickVideoActive && !isRecordingLocked) {
                                                stopVideoRecording()
                                            }
                                        }
                                    )
                                }

                                if (currentMode == CameraMode.SEARCH) {
                                    Spacer(modifier = Modifier.size(54.dp))   // keeps the shutter centred
                                }
                                if (currentMode != CameraMode.SEARCH) {
                                    Box(
                                        modifier = Modifier
                                                .graphicsLayer {
                                                    val hide = panoSideHide.value
                                                    alpha = 1f - hide
                                                    translationY = hide * 70.dp.toPx()
                                                }
                                                .then(if (sideHidden) Modifier.blockTouches() else Modifier)
                                    ) {
                                        if (isRecording) {
                                            LiquidGlassContainer(
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .bouncyClickable {
                                                        triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        if (isRecordingPaused) {
                                                            try { recording?.resume() } catch (e: Exception) { e.printStackTrace() }
                                                            isRecordingPaused = false
                                                        } else {
                                                            try { recording?.pause() } catch (e: Exception) { e.printStackTrace() }
                                                            isRecordingPaused = true
                                                        }
                                                    }
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (isRecordingPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                                        contentDescription = if (isRecordingPaused) "Resume" else "Pause",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                        } else if (currentMode == CameraMode.PANORAMA) {
                                            // sweep direction: left to right or right to left
                                            PanoDirectionButton(
                                                vertical = panorama.vertical,
                                                direction = panorama.direction,
                                                enabled = !panoBusy,
                                                onClick = {
                                                    triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    panorama.toggleDirection()
                                                }
                                            )
                                        } else {
                                            LiquidGlassContainer(
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .bouncyClickable { switchCamera() }
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Sync,
                                                        contentDescription = "Switch Camera",
                                                        tint = if (isSwitchingCamera) uiColor else Color.White,
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .graphicsLayer {
                                                                rotationZ = switchIconRotation.value
                                                            }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // The mode row slides down and fades while recording or sweeping a panorama. It keeps its
                            // space, so the shutter row, the pause button and the photo button never drop down.
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            val hide = immersiveProgress.value
                                            alpha = chromeAlpha * (1f - hide)
                                            translationY = hide * 90.dp.toPx()
                                        }
                                        .then(if (immersive) Modifier.blockTouches() else Modifier)
                                ) {
                                    ExactModeRow(
                                        selectedMode = currentMode,
                                        uiColor = uiColor,
                                        onModeChange = { mode ->
                                            if (!isRecording && !isSearchingWithGemini && !panorama.capturing && !panorama.saving) {
                                                triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                currentMode = mode
                                            }
                                        },
                                        onHoldStart = { triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP) },
                                        onTick = { triggerHaptic(HapticFeedbackConstants.CLOCK_TICK) }
                                    )
                                }
                        }

                        // Zoom dial: sits right on top of the bottom container (the translucent bar behind the zoom presets,
                        // shutter and mode row). Those controls stay untouched. The dial and the container fade in and
                        // out together (the same zoomDialFade value), with no slide.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 274.dp)       // same height as the bottom TranslucentEdgeBar
                                .height(168.dp)                 // same height as ZoomDial
                        ) {
                            if (zoomDialInTree) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = zoomDialFade.value }
                                ) {
                                    ZoomDial(
                                        zoom = currentZoom,
                                        minZoom = minZoom,
                                        maxZoom = maxZoom,
                                        onZoomDrag = { z ->
                                            currentZoom = z
                                            cameraController.setZoomRatio(z)
                                        },
                                        // a finger resting on the dial keeps it on screen
                                        onTouchChange = { zoomGestureActive = it }
                                    )
                                }
                            }
                        }

                        // While a search runs every control is hidden and touches are swallowed.
                        // Only a floating blurred cross (top right) stays, to cancel the search.
                        AnimatedVisibility(
                            visible = isSearchingWithGemini,
                            enter = fadeIn(tween(220)),
                            exit = fadeOut(tween(160)),
                            modifier = Modifier.fillMaxSize().zIndex(24f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) { detectTapGestures { } }
                            ) {
                                BlurGlassBar(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .statusBarsPadding()
                                        .padding(top = 10.dp, end = 16.dp)
                                        .size(44.dp)
                                        .bouncyClickable {
                                            triggerHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                            searchJob?.cancel()
                                            imagesJob?.cancel()
                                            isSearchingWithGemini = false
                                            geminiSearchResult = null
                                            searchCapturedBitmap = null
                                            searchCropBitmap = null
                                            webImages = emptyList()
                                            webImagesLoading = false
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel search",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Back closes the search (result card or in-flight search) and returns to the live preview.
                        // Registered after the mode-level BackHandler, so it takes priority while a search is active.
                        BackHandler(enabled = geminiSearchResult != null || isSearchingWithGemini) {
                            searchJob?.cancel()
                            imagesJob?.cancel()
                            isSearchingWithGemini = false
                            geminiSearchResult = null
                            searchCapturedBitmap = null
                            searchCropBitmap = null
                            webImages = emptyList()
                            webImagesLoading = false
                        }

                        LaunchedEffect(geminiSearchResult) {
                            if (geminiSearchResult == null) {
                                followUpJob?.cancel()
                                delay(450)      // let the card finish sliding out first
                                if (geminiSearchResult == null) followUps = emptyList()
                            }
                            if (geminiSearchResult != null) {
                                shownSearchResult = geminiSearchResult
                                shownSearchBitmap = searchCropBitmap ?: searchCapturedBitmap
                                shownSearchFrame = searchCapturedBitmap
                            }
                        }
                        val sheetResult = geminiSearchResult ?: shownSearchResult
                        val sheetBitmap = searchCropBitmap ?: searchCapturedBitmap ?: shownSearchBitmap
                        AnimatedVisibility(
                            visible = geminiSearchResult != null,
                            // Only a fade here. The cards and bars slide up inside the sheet themselves, so the
                            // dimming never travels with them and no hard-edged band is visible.
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier.fillMaxSize().zIndex(25f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                sheetResult?.let { result ->
                                    GeminiSearchResultSheet(
                                        result = result,
                                        capturedBitmap = sheetBitmap,
                                        frameBitmap = searchCapturedBitmap ?: shownSearchFrame,
                                        images = webImages,
                                        imagesLoading = webImagesLoading,
                                        imagesError = webImagesError,
                                        followUps = followUps,
                                        onClose = {
                                            imagesJob?.cancel()
                                            geminiSearchResult = null
                                            searchCapturedBitmap = null
                                            searchCropBitmap = null
                                            webImages = emptyList()
                                            webImagesLoading = false
                                        },
                                        onSearchAgain = {
                                            geminiSearchResult = null
                                            searchCapturedBitmap = null
                                            val bmp = previewView?.bitmap
                                            if (bmp != null) {
                                                performGeminiSearch(bmp, searchQueryText.ifBlank { null })
                                            }
                                        },
                                        onFollowUp = { question ->
                                            // Answered inside the same card: it opens to full height and the answer
                                            // appears right under the question. No new search, no glow.
                                            val photo = searchCropBitmap ?: searchCapturedBitmap ?: shownSearchBitmap
                                            if (photo != null) {
                                                val id = System.nanoTime()
                                                val history = followUps
                                                followUps = followUps + FollowUpTurn(id = id, question = question)
                                                triggerHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                                val prompt = buildFollowUpPrompt(shownSearchResult, history, question)
                                                followUpJob = scope.launch {
                                                    val r = GeminiSearchService.searchWithImage(photo, prompt)
                                                    followUps = followUps.map {
                                                        if (it.id == id) {
                                                            it.copy(
                                                                loading = false,
                                                                answer = if (r.isSuccess) followUpAnswerText(r) else null,
                                                                error = if (r.isSuccess) null
                                                                else (r.errorMessage ?: "Couldn't get an answer. Please try again.")
                                                            )
                                                        } else it
                                                    }
                                                    triggerHaptic(HapticFeedbackConstants.CONFIRM)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** House style for every answer: no markdown symbols, bullet points, a few emojis. */
private const val SEARCH_STYLE_RULES =
    "Formatting rules for your answer: never use asterisks, hash symbols or any other markdown. " +
        "Write short bullet points, each starting with the \u2022 character, one idea per bullet. " +
        "Add a few fitting emojis (at most one per bullet). Keep the title short and plain. " +
        "Be accurate, clear and friendly."

/** Prompt for a follow-up question: the earlier answer and the last few follow-ups are given as context. */
private fun buildFollowUpPrompt(previous: GeminiSearchResult?, history: List<FollowUpTurn>, question: String): String = buildString {
    append("Look carefully at this photo and answer the user's follow-up question about it. ")
    append("Answer the question directly and do not repeat the earlier analysis. ")
    if (previous != null) {
        append("Earlier analysis of this photo: ")
        append(cleanAiTitle(previous.title))
        append(" - ")
        append(cleanAiText(previous.summary).replace("\n", " "))
        append(" ")
    }
    history.filter { !it.loading && it.answer != null }.takeLast(3).forEach {
        append("Earlier follow-up question: ")
        append(it.question)
        append(" Answer given: ")
        append(cleanAiText(it.answer.orEmpty()).replace("\n", " "))
        append(" ")
    }
    append("New follow-up question: ")
    append(question)
    append("\n\n")
    append(SEARCH_STYLE_RULES)
}

/** The answer text shown in the card: the summary, then the key points as bullets. */
private fun followUpAnswerText(r: GeminiSearchResult): String = buildString {
    append(cleanAiText(r.summary))
    if (r.keyPoints.isNotEmpty()) {
        if (isNotEmpty()) append("\n\n")
        append(r.keyPoints.joinToString("\n") { "\u2022 " + cleanAiText(it).removePrefix("\u2022 ") })
    }
}.trim()

private fun buildSearchQuery(user: String?, isCircledCrop: Boolean): String = buildString {
    if (isCircledCrop) {
        append("The image is a crop of the area the user circled on their camera view. Explain what is inside the circled area. ")
    }
    if (!user.isNullOrBlank()) {
        append(user.trim())
    } else {
        append("Identify and explain what is in this image.")
    }
    append("\n\n")
    append(SEARCH_STYLE_RULES)
}

private fun decodeBitmapForSearch(context: android.content.Context, uri: android.net.Uri, maxDim: Int = 1600): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val factor = kotlin.math.max(w, h).toFloat() / maxDim
            if (factor > 1f) {
                decoder.setTargetSize((w / factor).toInt().coerceAtLeast(1), (h / factor).toInt().coerceAtLeast(1))
            }
        }
    } else {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (kotlin.math.max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
    }
} catch (e: Exception) {
    null
}

/** Timing of the search glow. */
private val ShutterGlowColors = intArrayOf(
    // red, yellow, green, blue and back to red so the sweep loops without a seam
    0xFFFF3B30.toInt(), 0xFFFFD60A.toInt(), 0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFFF3B30.toInt()
)
private val ShutterGlowColorsVideo = intArrayOf(
    0xFFFF2A42.toInt(), 0xFFFF6B3D.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF6B3D.toInt(), 0xFFFF2A42.toInt()
)
private const val GLOW_SHOW_MS = 1700f
private const val GLOW_SPREAD_DELAY_MS = 70f
private const val GLOW_SPREAD_MS = 1500f
private val GlowSpreadEasing = CubicBezierEasing(0.18f, 0.75f, 0.22f, 1f)
private val GlowRimColors = intArrayOf(
    0xFFFFFFFF.toInt(), 0xFFBAE6FD.toInt(), 0xFFFFFFFF.toInt(),
    0xFFFBCFE8.toInt(), 0xFFFFFFFF.toInt(), 0xFFFEF08A.toInt(), 0xFFFFFFFF.toInt()
)
private val GlowRimStops = floatArrayOf(0f, 0.17f, 0.34f, 0.52f, 0.68f, 0.84f, 1f)

/**
 * White search glow, triggered the moment a Gemini search starts ([active] turns true):
 *  1. a quick double blink at the exact bottom-centre of the screen,
 *  2. the glow spreads out from that point over the whole screen (~1.5s),
 *  3. a thin liquid-light rim flows around the screen edge (white with soft pastel tints),
 *  4. the rim and a soft edge glow stay, gently breathing, while Gemini is still working.
 *
 * The blink/spread/rim show always plays to the end, even if the result comes back instantly
 * (for example an API error); the fade-out only starts once both the show and the search are done.
 */
@Composable
fun SearchGlowOverlay(
    active: Boolean,
    trigger: Int,
    modifier: Modifier = Modifier
) {
    val blink = remember { Animatable(0f) }     // quick flash at the origin
    val spread = remember { Animatable(0f) }    // 0..1 growth of the glow front
    val clockMs = remember { Animatable(0f) }   // 0..GLOW_SHOW_MS, times the show
    val presence = remember { Animatable(0f) }  // overall visibility, fades out at the very end
    val activeNow = rememberUpdatedState(active)

    // Keyed on [trigger], not on [active]: if the search finishes (or fails) before the next frame,
    // `active` is never seen as true and a boolean key would skip the glow entirely.
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        blink.snapTo(0f)
        spread.snapTo(0f)
        clockMs.snapTo(0f)
        presence.snapTo(1f)
        coroutineScope {
            launch {
                blink.animateTo(1f, tween(70, easing = FastOutLinearInEasing))
                blink.animateTo(0.15f, tween(130, easing = LinearOutSlowInEasing))
                blink.animateTo(0.85f, tween(70, easing = FastOutLinearInEasing))
                blink.animateTo(0f, tween(320, easing = LinearOutSlowInEasing))
            }
            launch {
                delay(GLOW_SPREAD_DELAY_MS.toLong())
                spread.animateTo(1f, tween(GLOW_SPREAD_MS.toInt(), easing = GlowSpreadEasing))
            }
            launch {
                clockMs.animateTo(GLOW_SHOW_MS, tween(GLOW_SHOW_MS.toInt(), easing = LinearEasing))
            }
        }
        // the wave is a one-off: fade out when it has played
        presence.animateTo(0f, tween(250))
    }

    val visible by remember { derivedStateOf { presence.value > 0.001f } }
    if (visible) {
        SearchGlowCanvas(
            blink = blink,
            spread = spread,
            presence = presence,
            modifier = modifier
        )
    }
}

/** Where the wave is born. 1f is the exact bottom-centre (the shutter). */
private const val GLOW_ORIGIN_Y = 1f

/** A dense white wave: a thick bright ring that expands from the shutter and fades as it reaches the edges. */
@Composable
private fun SearchGlowCanvas(
    blink: Animatable<Float, AnimationVector1D>,
    spread: Animatable<Float, AnimationVector1D>,
    presence: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val origin = Offset(w / 2f, h * GLOW_ORIGIN_Y)
            val dx = kotlin.math.max(origin.x, w - origin.x)
            val dy = kotlin.math.max(origin.y, h - origin.y)
            val maxR = kotlin.math.sqrt(dx * dx + dy * dy) * 1.2f
            val band = 130.dp.toPx()
            onDrawBehind {
                val sp = spread.value
                val pres = presence.value
                if (sp <= 0.001f || pres <= 0.01f) return@onDrawBehind
                val r = (maxR * sp).coerceAtLeast(2f)
                val inner = ((r - band) / r).coerceIn(0f, 0.9f)
                val peak = inner + (1f - inner) * 0.72f
                val a = ((1f - sp * sp) * pres).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.10f * a),
                        inner to Color.White.copy(alpha = 0.14f * a),
                        peak to Color.White.copy(alpha = 0.92f * a),
                        1f to Color.Transparent,
                        center = origin, radius = r
                    ),
                    radius = r, center = origin
                )
            }
        }
    )
}

/** Status lines for the centre label, picked from the kind of search that was started. */
private fun searchStatusSteps(query: String?): List<String> {
    val q = query?.lowercase(Locale.US).orEmpty()
    return when {
        "nutrition" in q -> listOf("Reading the label", "Looking up nutrition", "Parsing the results")
        "translate" in q -> listOf("Reading the text", "Translating", "Parsing the results")
        "math" in q || "solve" in q -> listOf("Reading the problem", "Solving", "Checking the answer")
        "identify" in q || "object" in q -> listOf("Looking at the image", "Identifying the object", "Parsing the results")
        "web" in q -> listOf("Reading the image", "Searching the web", "Parsing the results")
        "summar" in q || "scene" in q -> listOf("Looking at the scene", "Summarising", "Parsing the results")
        else -> listOf("Analysing the image", "Searching", "Parsing the results")
    }
}

/**
 * Centre-of-screen status text shown while Gemini works. The line changes as the search moves through its
 * stages (about every 1.8s), crossfading with a small upward slide, followed by three softly pulsing dots.
 */
@Composable
fun SearchStatusLabel(
    visible: Boolean,
    query: String?,
    modifier: Modifier = Modifier
) {
    val steps = remember(query) { searchStatusSteps(query) }
    var stepIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible, steps) {
        stepIndex = 0
        if (visible) {
            while (stepIndex < steps.lastIndex) {
                delay(2000)
                stepIndex++
            }
        }
    }
    val dotsTransition = rememberInfiniteTransition(label = "StatusDots")
    val dotsPhase = dotsTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "StatusDotsPhase"
    )

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(400)) + slideInVertically(tween(600, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(tween(350)) + slideOutVertically(tween(350, easing = FastOutLinearInEasing)) { -it }
    ) {
        // the whole label also blurs in and out together with its slide
        val shellBlur = statusBlurModifier(transition, 14.dp)
        AnimatedContent(
            targetState = steps[stepIndex.coerceIn(0, steps.lastIndex)],
            modifier = shellBlur,
            transitionSpec = {
                // the old line blurs away and slides up first, then the new one rises in with a soft bounce
                (fadeIn(tween(500, delayMillis = 380)) +
                    slideInVertically(
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow)
                    ) { it })
                    .togetherWith(fadeOut(tween(320)) + slideOutVertically(tween(380, easing = FastOutLinearInEasing)) { -it })
                    .using(SizeTransform(clip = false))
            },
            label = "StatusStepText"
        ) { label ->
            val lineBlur = statusBlurModifier(transition, 14.dp, enterDelayMs = 380)
            Row(
                modifier = lineBlur,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = Offset(0f, 2f),
                            blurRadius = 14f
                        )
                    )
                )
                Canvas(modifier = Modifier.size(width = 22.dp, height = 8.dp)) {
                    val r = 2.dp.toPx()
                    val p = dotsPhase.value
                    for (i in 0..2) {
                        val d = (p - i + 3f) % 3f
                        val a = (1f - d / 1.6f).coerceIn(0.25f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = a),
                            radius = r,
                            center = Offset(r + i * 8.dp.toPx(), size.height / 2f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Real GPU blur that follows an enter/exit transition: fully blurred while the content is entering or leaving,
 * sharp once it is visible. Needs Android 12 (API 31); older versions just skip the blur.
 */
@Composable
private fun statusBlurModifier(
    transition: Transition<EnterExitState>,
    maxBlur: Dp,
    enterDelayMs: Int = 0
): Modifier {
    if (Build.VERSION.SDK_INT < 31) return Modifier
    val maxPx = with(LocalDensity.current) { maxBlur.toPx() }
    val amount by transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(650, delayMillis = enterDelayMs, easing = FastOutSlowInEasing)
            else tween(320, easing = FastOutLinearInEasing)
        },
        label = "StatusBlurAmount"
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    return Modifier.graphicsLayer {
        if (amount > 0.01f) {
            val r = amount * maxPx
            renderEffect = BlurEffect(r, r, TileMode.Decal)
        }
    }
}

@Composable
private fun LevelReadout(rollProvider: () -> Float, isLevel: Boolean, uiColor: Color) {
    // Only re-emits when the displayed text (0.1 degree steps) actually changes.
    val label by remember(isLevel) {
        derivedStateOf {
            if (isLevel) "0.0° LEVEL" else String.format(Locale.US, "%+.1f°", rollProvider())
        }
    }
    Text(
        text = label,
        color = if (isLevel) uiColor else Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}

/** Tweak these to change how the glass looks. */
private val GlassBlurRadius = 28.dp          // higher = blurrier
/** Outline of every floating glass bar / pill. One hairline, change it here to change them all. */
private val GlassOutlineWidth = 0.3.dp

private const val GlassTintTop = 0.40f       // dark tint, same top and bottom so the glass looks even
private const val GlassTintBottom = 0.40f

/**
 * Frosted glass container: blurs the live camera preview behind it, adds a light tint and a
 * luminous edge. Falls back to a solid-ish dark tint when there is no preview (e.g. Settings).
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    showBorder: Boolean = true,
    edgeLighting: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalCameraBackdrop.current
    val hasBackdrop = backdrop?.ready == true
    var myOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { myOrigin = it.positionInRoot() }
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = GlassOutlineWidth,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (hasBackdrop && backdrop != null) {
            // Layer 1: blurred slice of the camera preview, aligned to what's really behind us.
            // The snapshot is already heavily blurred on the CPU (see BackdropCaptureEffect), so no extra
            // GPU RenderEffect blur is applied here: re-blurring it on every glass container on every frame
            // was the main source of dropped frames and did not change how the glass looks.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        backdrop.frame // subscribe to new snapshots
                        val img = backdrop.image ?: return@drawBehind
                        val previewSize = backdrop.size
                        if (previewSize.width <= 0f || previewSize.height <= 0f) return@drawBehind
                        val dx = backdrop.origin.x - myOrigin.x
                        val dy = backdrop.origin.y - myOrigin.y
                        val pw = previewSize.width.roundToInt()
                        val ph = previewSize.height.roundToInt()
                        val ox = dx.roundToInt()
                        val oy = dy.roundToInt()
                        val ch = size.height.roundToInt()
                        // Extend the blurred edge colour above/below the preview, but only where this glass
                        // actually hangs over the black bars (keeps the draw cost low).
                        val belowTop = oy + ph
                        if (belowTop < ch) {
                            val startY = belowTop.coerceAtLeast(0)
                            drawImage(
                                image = img,
                                srcOffset = IntOffset(0, img.height - 1),
                                srcSize = IntSize(img.width, 1),
                                dstOffset = IntOffset(ox, startY),
                                dstSize = IntSize(pw, ch - startY),
                                filterQuality = FilterQuality.Low
                            )
                        }
                        if (oy > 0) {
                            drawImage(
                                image = img,
                                srcOffset = IntOffset(0, 0),
                                srcSize = IntSize(img.width, 1),
                                dstOffset = IntOffset(ox, 0),
                                dstSize = IntSize(pw, oy.coerceAtMost(ch)),
                                filterQuality = FilterQuality.Low
                            )
                        }
                        drawImage(
                            image = img,
                            srcSize = IntSize(img.width, img.height),
                            dstOffset = IntOffset(ox, oy),
                            dstSize = IntSize(pw, ph),
                            filterQuality = FilterQuality.Medium
                        )
                    }
            )
            // Layer 2: light tint so icons and text stay readable on bright scenes.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = GlassTintTop),
                                Color.Black.copy(alpha = GlassTintBottom)
                            )
                        )
                    )
            )
        } else {
            // No preview behind us: use a darker tint so it still looks intentional.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF2E2E38).copy(alpha = 0.80f),
                                Color(0xFF1A1A22).copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }
        // Liquid-glass edge lighting: a soft inner glow plus a thin specular rim.
        // Light catches the top-left and bottom-right edges, like a glass bevel.
        if (edgeLighting) Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = GlassOutlineWidth,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.55f)
                        )
                    ),
                    shape = shape
                )
        )
        content()
    }
}

/**
 * Floating bar with a real blur of what is behind it (the blurred camera snapshot, same as the other glass
 * controls) and one thin, flat liquid-glass edge light. No bevel, no shadow, so it does not look 3D.
 */
@Composable
fun BlurGlassBar(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassContainer(
        modifier = modifier,
        shape = shape,
        showBorder = false,
        edgeLighting = false
    ) {
        content()
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = GlassOutlineWidth,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.60f),
                            Color.White.copy(alpha = 0.07f),
                            Color.White.copy(alpha = 0.07f),
                            Color.White.copy(alpha = 0.40f)
                        )
                    ),
                    shape = shape
                )
        )
    }
}

/**
 * Plain black bar at 50% transparency behind the top / bottom floating controls. No edge lighting.
 */
@Composable
fun TranslucentEdgeBar(
    isTop: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black.copy(alpha = 0.64f)))
}

@Composable
fun QuickTogglePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    uiColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) uiColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) uiColor else Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (active) uiColor else Color.White,
            fontSize = 12.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun FilterCarousel(
    selectedFilter: ColorFilterProfile,
    onFilterSelected: (ColorFilterProfile) -> Unit,
    uiColor: Color
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorFilterProfile.values().forEach { filter ->
                val isSelected = selectedFilter == filter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onFilterSelected(filter) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(filter.brush)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) uiColor else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = filter.title,
                        color = if (isSelected) uiColor else Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

data class AppIconChoice(
    val id: String,
    val name: String,
    val subtitle: String,
    val drawableRes: Int
)

fun safeHaptic(view: View?, feedbackConstant: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
    try {
        view?.performHapticFeedback(feedbackConstant)
    } catch (_: Throwable) {}
}

enum class SettingsTabType(val title: String) {
    CUSTOMIZATION("Customization"),
    GENERAL("General"),
    EXPERIMENTAL("Experimental")
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(durationMillis = 180),
        label = "ThumbOffset"
    )

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) Color.White else Color(0xFF2C2F3A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF14151B) else Color(0xFF94A3B8))
        )
    }
}

/**
 * Flat floating bar: grey fill plus one thin liquid-glass edge light (bright at top-left and bottom-right).
 * No shadow, no bevel, so there is no 3D look. Reuse this for any floating bar.
 */
fun Modifier.liquidGlassBar(
    shape: androidx.compose.ui.graphics.Shape,
    fill: Color = Color(0xFF2C2C2E).copy(alpha = 0.78f)
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(
        width = GlassOutlineWidth,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.40f)
            )
        ),
        shape = shape
    )

/** Smooth fade stops (eased alphas) so the edge fades have no visible band. */
private fun easedFade(color: Color, topToTransparent: Boolean): List<Color> {
    val a = listOf(1f, 0.985f, 0.94f, 0.85f, 0.70f, 0.50f, 0.30f, 0.14f, 0.04f, 0f)
    val list = a.map { color.copy(alpha = it) }
    return if (topToTransparent) list else list.reversed()
}

/** How far the soft fade reaches into the Settings list from the top and from the bottom edge. */
private val SettingsFadeTop = 112.dp      // as tall as the header row + tab row, so the list at rest is untouched
private val SettingsFadeBottom = 56.dp    // a little less than the empty space under the last item

/**
 * Holds two recordings of the Settings list: the normal one and a blurred copy. The floating header pills
 * draw the blurred copy behind themselves, so the list really blurs under them as it scrolls.
 */
class SettingsGlass(val content: GraphicsLayer, val blurred: GraphicsLayer) {
    var originX: Float = 0f
    var originY: Float = 0f
}

@Composable
fun Modifier.settingsGlassBar(
    shape: androidx.compose.ui.graphics.Shape,
    glass: SettingsGlass,
    fill: Color = Color(0xFF2C2C2E).copy(alpha = 0.45f),
    light: Boolean = false
): Modifier {
    var pos by remember { mutableStateOf(Offset.Zero) }
    return this
        .clip(shape)
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            // Live blur: the blurred copy of the list, lined up with what is really behind this pill.
            // Best-effort: this layer is cosmetic. If it hasn't been recorded yet (first-frame race) or throws
            // for any other reason, skip it silently rather than crash the whole screen - the `fill`
            // background below still reads fine on its own. Below Android 12 there is no blur, so it is skipped.
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    translate(left = glass.originX - pos.x, top = glass.originY - pos.y) {
                        drawLayer(glass.blurred)
                    }
                } catch (_: Throwable) {}
            }
        }
        .background(fill)
        .drawBehind {
            // glass sheen: brightest along the top edge, gone by the middle of the pill
            drawRect(
                brush = Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = if (light) 0.22f else 0.12f),
                    0.55f to Color.Transparent
                )
            )
        }
        .border(
            width = GlassOutlineWidth,
            brush = Brush.linearGradient(
                if (light) listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color.Black.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.75f)
                ) else listOf(
                    Color.White.copy(alpha = 0.60f),
                    Color.White.copy(alpha = 0.07f),
                    Color.White.copy(alpha = 0.07f),
                    Color.White.copy(alpha = 0.40f)
                )
            ),
            shape = shape
        )
}

/** The five experimental toggles, kept together so they can be compared and saved as one. */
private data class ExpToggles(
    val neuralDenoise: Boolean,
    val geminiTelemetry: Boolean,
    val highSpeedBurst: Boolean,
    val rawSensorDng: Boolean,
    val vulkanAcceleration: Boolean
)

/** Everything on the Settings screen that is only kept once the user taps Save. */
private data class SettingsDraft(
    val haptics: Boolean,
    val blur: Boolean,
    val horizon: HorizonLockMode,
    val grid: Boolean,
    val leveler: Boolean,
    val edgeBars: Boolean,
    val palmGesture: Boolean,
    val cheese: Boolean,
    val shutterSound: ShutterSoundType,
    val recordSound: RecordSoundType,
    val timerSound: TimerSoundType,
    val watermark: Boolean,
    val nightModeAuto: Boolean,
    val aspect: String,
    val exp: ExpToggles
)

/** Solid (opaque) floating pill: flat fill and one thin edge line. Nothing shows through it. */
private fun Modifier.settingsOpaquePill(
    shape: androidx.compose.ui.graphics.Shape,
    fill: Color,
    light: Boolean
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(
        width = GlassOutlineWidth,
        brush = Brush.linearGradient(
            if (light) listOf(
                Color.White.copy(alpha = 0.95f),
                Color.Black.copy(alpha = 0.10f),
                Color.Black.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.75f)
            ) else listOf(
                Color.White.copy(alpha = 0.60f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.40f)
            )
        ),
        shape = shape
    )

/**
 * Save button. Quiet and opaque while nothing has changed. As soon as something changes it fades to white and
 * a soft glow slowly breathes around it, so it is clear that this is the button to tap.
 */
@Composable
private fun SettingsSaveButton(
    hasChanges: Boolean,
    idleFill: Color,
    ink: Color,
    dark: Boolean,
    onClick: () -> Unit
) {
    val active by animateFloatAsState(
        targetValue = if (hasChanges) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "SaveActive"
    )
    // The breathing only runs while the button is lit (it is not even created while the button is idle).
    val glowing = hasChanges || active > 0.01f
    val breathe: State<Float>? = if (glowing) {
        rememberInfiniteTransition(label = "SaveGlow").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "SaveGlowBreathe"
        )
    } else {
        null
    }
    val glowColor = if (dark) Color.White else Color.Black
    val glowStrength = if (dark) 1f else 0.5f
    val fill = androidx.compose.ui.graphics.lerp(idleFill, Color.White, active)
    val iconTint = androidx.compose.ui.graphics.lerp(ink.copy(alpha = 0.45f), Color(0xFF14151B), active)

    Box(
        modifier = Modifier
            .size(44.dp)
            .drawBehind {
                val a = active
                if (a > 0.01f) {
                    val b = breathe?.value ?: 0.3f
                    val radius = size.minDimension / 2f + 4.dp.toPx() + 10.dp.toPx() * b
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.55f * glowStrength * a * (0.4f + 0.6f * b)),
                                glowColor.copy(alpha = 0.22f * glowStrength * a * b),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }
            }
            .settingsOpaquePill(CircleShape, fill, !dark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = hasChanges
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Save",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * One floating bar holding the three tabs. The selected pill slides to the chosen tab with a small bounce.
 */
@Composable
private fun SettingsTabBar(
    currentTab: SettingsTabType,
    isExperimentalUnlocked: Boolean,
    barFill: Color,
    ink: Color,
    inkInverse: Color,
    dark: Boolean,
    onTabSelected: (SettingsTabType) -> Unit
) {
    val tabs = SettingsTabType.entries
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(42.dp)
            .settingsOpaquePill(CircleShape, barFill, !dark)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * currentTab.ordinal,
            animationSpec = spring(dampingRatio = 0.58f, stiffness = 380f),
            label = "TabIndicatorX"
        )
        // the sliding pill
        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorX.roundToPx(), 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(ink)
        )
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                key(tab) {
                    val isSelected = currentTab == tab
                    val isLockedExp = tab == SettingsTabType.EXPERIMENTAL && !isExperimentalUnlocked
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) inkInverse else ink.copy(alpha = 0.75f),
                        animationSpec = tween(200),
                        label = "TabLabelColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            if (tab == SettingsTabType.EXPERIMENTAL) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (isSelected) inkInverse else if (isLockedExp) Color(0xFFF59E0B) else ink.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = tab.title,
                                color = labelColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One half of the pop-up's button row. Highlights softly while pressed, like an iOS alert. */
@Composable
private fun AlertButton(
    text: String,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .background(if (pressed) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 17.sp, fontWeight = fontWeight)
    }
}

/**
 * "Save changes?" pop-up. Like an iOS alert it zooms out (starts a little large and settles) while fading in,
 * and zooms out again while fading away. Tapping outside it does nothing.
 */
@Composable
private fun UnsavedChangesDialog(
    visible: Boolean,
    dark: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val cardFill = if (dark) Color(0xFF2C2C2E) else Color(0xFFF9F9FB)
    val ink = if (dark) Color.White else Color(0xFF1C1C1E)
    val divider = ink.copy(alpha = 0.14f)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // dimmed background, also swallows touches while the pop-up is up
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .blockTouches()
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + scaleIn(
                initialScale = 1.14f,
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(170)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(180, easing = FastOutLinearInEasing)
            )
        ) {
            Column(
                modifier = Modifier
                    .width(286.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardFill),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Save changes?",
                        color = ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "You changed some camera settings. Do you want to save them before leaving?",
                        color = ink.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(divider))
                Row(modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    AlertButton(
                        text = "No",
                        color = ink,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = onNo
                    )
                    Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(divider))
                    AlertButton(
                        text = "Yes",
                        color = Color(0xFF0A84FF),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = onYes
                    )
                }
            }
        }
    }
}

/** "About this app" row in Settings > General. Tap to open it and read the note; tap again to close. */
@Composable
private fun AboutThisAppCard(cardBg: Color, ink: Color, view: View) {
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "AboutChevron"
    )
    val muted = Color(0xFF94A3B8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                    expanded = !expanded
                }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(text = "About this app", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Who made it, and why", color = muted, fontSize = 11.5.sp)
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ink.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = chevron }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(320, delayMillis = 80)),
            exit = fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))
                Text(
                    text = "This app was made by Rudraksha Manna.",
                    color = ink,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "It started with a simple idea: a camera that feels smooth, looks beautiful and keeps " +
                        "getting better with the people who use it.",
                    color = ink.copy(alpha = 0.8f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "I thought of adding blur to the app, along with every feature users have been asking for. " +
                        "My idea of building this app is to hear all users' conflicts and give my best to get the best review.",
                    color = ink.copy(alpha = 0.8f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "Thank you for being part of it.",
                    color = muted,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

/**
 * Camera Settings. Changes are kept as a draft and only applied when the user taps Save. Leaving with unsaved
 * changes (back button, system back, or re-running the Gemini setup) asks "Save changes?" first.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    uiColor: Color = Color.White,
    isFadeEffectEnabled: Boolean,
    onFadeEffectChange: (Boolean) -> Unit,
    isBouncyAnimationEnabled: Boolean,
    onBouncyAnimationChange: (Boolean) -> Unit,
    isHapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    isBlurEnabled: Boolean = true,
    onBlurChange: (Boolean) -> Unit = {},
    horizonMode: HorizonLockMode = HorizonLockMode.OFF,
    onHorizonModeChange: (HorizonLockMode) -> Unit = {},
    isGridEnabled: Boolean,
    onGridChange: (Boolean) -> Unit,
    isLevelerEnabled: Boolean,
    onLevelerChange: (Boolean) -> Unit,
    isEdgeBarsEnabled: Boolean = false,
    onEdgeBarsChange: (Boolean) -> Unit = {},
    isPalmGestureEnabled: Boolean,
    onPalmGestureChange: (Boolean) -> Unit,
    isCheeseEnabled: Boolean = false,
    onCheeseChange: (Boolean) -> Unit = {},
    currentShutterSound: ShutterSoundType,
    onShutterSoundChange: (ShutterSoundType) -> Unit,
    currentRecordSound: RecordSoundType,
    onRecordSoundChange: (RecordSoundType) -> Unit,
    currentTimerSound: TimerSoundType,
    onTimerSoundChange: (TimerSoundType) -> Unit,
    isWatermarkEnabled: Boolean,
    onWatermarkChange: (Boolean) -> Unit,
    isNightModeAuto: Boolean,
    onNightModeAutoChange: (Boolean) -> Unit,
    aspectRatios: List<String>,
    currentAspect: String,
    onAspectChange: (String) -> Unit,
    onRerunSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // What is saved right now: the values the camera screen passes in, plus the experimental toggles from prefs.
    var savedExp by remember {
        mutableStateOf(
            ExpToggles(
                neuralDenoise = prefs.getBoolean("exp_neural_denoise", true),
                geminiTelemetry = prefs.getBoolean("exp_gemini_telemetry", true),
                highSpeedBurst = prefs.getBoolean("exp_high_speed_burst", false),
                rawSensorDng = prefs.getBoolean("exp_raw_sensor_dng", false),
                vulkanAcceleration = prefs.getBoolean("exp_vulkan_accel", true)
            )
        )
    }
    val saved = SettingsDraft(
        haptics = isHapticsEnabled,
        blur = isBlurEnabled,
        horizon = horizonMode,
        grid = isGridEnabled,
        leveler = isLevelerEnabled,
        edgeBars = isEdgeBarsEnabled,
        palmGesture = isPalmGestureEnabled,
        cheese = isCheeseEnabled,
        shutterSound = currentShutterSound,
        recordSound = currentRecordSound,
        timerSound = currentTimerSound,
        watermark = isWatermarkEnabled,
        nightModeAuto = isNightModeAuto,
        aspect = currentAspect,
        exp = savedExp
    )
    // What the user is editing. Nothing here touches the camera or the saved preferences until Save.
    var draft by remember { mutableStateOf(saved) }
    val hasChanges = draft != saved

    fun commit() {
        val d = draft
        if (d.haptics != isHapticsEnabled) onHapticsChange(d.haptics)
        if (d.blur != isBlurEnabled) onBlurChange(d.blur)
        if (d.horizon != horizonMode) onHorizonModeChange(d.horizon)
        if (d.grid != isGridEnabled) onGridChange(d.grid)
        if (d.leveler != isLevelerEnabled) onLevelerChange(d.leveler)
        if (d.edgeBars != isEdgeBarsEnabled) onEdgeBarsChange(d.edgeBars)
        if (d.palmGesture != isPalmGestureEnabled) onPalmGestureChange(d.palmGesture)
        if (d.cheese != isCheeseEnabled) onCheeseChange(d.cheese)
        if (d.shutterSound != currentShutterSound) onShutterSoundChange(d.shutterSound)
        if (d.recordSound != currentRecordSound) onRecordSoundChange(d.recordSound)
        if (d.timerSound != currentTimerSound) onTimerSoundChange(d.timerSound)
        if (d.watermark != isWatermarkEnabled) onWatermarkChange(d.watermark)
        if (d.nightModeAuto != isNightModeAuto) onNightModeAutoChange(d.nightModeAuto)
        if (d.aspect != currentAspect) onAspectChange(d.aspect)
        if (d.exp != savedExp) {
            prefs.edit()
                .putBoolean("exp_neural_denoise", d.exp.neuralDenoise)
                .putBoolean("exp_gemini_telemetry", d.exp.geminiTelemetry)
                .putBoolean("exp_high_speed_burst", d.exp.highSpeedBurst)
                .putBoolean("exp_raw_sensor_dng", d.exp.rawSensorDng)
                .putBoolean("exp_vulkan_accel", d.exp.vulkanAcceleration)
                .apply()
            savedExp = d.exp
        }
    }

    // "Save changes?" pop-up
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requestLeave(action: () -> Unit) {
        if (hasChanges) {
            pendingLeave = action
            showSaveDialog = true
        } else {
            action()
        }
    }

    fun answerDialog(save: Boolean) {
        if (!showSaveDialog) return
        val leave = pendingLeave
        showSaveDialog = false
        if (save) commit()
        scope.launch {
            // let the pop-up finish zooming out and fading before the screen slides away
            delay(190)
            pendingLeave = null
            leave?.invoke()
        }
    }

    // The pop-up's handler is registered last, so while it is showing, Back just closes it.
    BackHandler { if (!showSaveDialog) requestLeave(onBack) }
    BackHandler(enabled = showSaveDialog) { showSaveDialog = false }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScreenContent(
            onBack = { requestLeave(onBack) },
            exp = draft.exp,
            onExpChange = { draft = draft.copy(exp = it) },
            hasUnsavedChanges = hasChanges,
            onSave = { commit() },
            uiColor = uiColor,
            isFadeEffectEnabled = isFadeEffectEnabled,
            onFadeEffectChange = onFadeEffectChange,
            isBouncyAnimationEnabled = isBouncyAnimationEnabled,
            onBouncyAnimationChange = onBouncyAnimationChange,
            isHapticsEnabled = draft.haptics,
            onHapticsChange = { draft = draft.copy(haptics = it) },
            isBlurEnabled = draft.blur,
            onBlurChange = { draft = draft.copy(blur = it) },
            horizonMode = draft.horizon,
            onHorizonModeChange = { draft = draft.copy(horizon = it) },
            isGridEnabled = draft.grid,
            onGridChange = { draft = draft.copy(grid = it) },
            isLevelerEnabled = draft.leveler,
            onLevelerChange = { draft = draft.copy(leveler = it) },
            isEdgeBarsEnabled = draft.edgeBars,
            onEdgeBarsChange = { draft = draft.copy(edgeBars = it) },
            isPalmGestureEnabled = draft.palmGesture,
            onPalmGestureChange = { draft = draft.copy(palmGesture = it) },
            isCheeseEnabled = draft.cheese,
            onCheeseChange = { draft = draft.copy(cheese = it) },
            currentShutterSound = draft.shutterSound,
            onShutterSoundChange = { draft = draft.copy(shutterSound = it) },
            currentRecordSound = draft.recordSound,
            onRecordSoundChange = { draft = draft.copy(recordSound = it) },
            currentTimerSound = draft.timerSound,
            onTimerSoundChange = { draft = draft.copy(timerSound = it) },
            isWatermarkEnabled = draft.watermark,
            onWatermarkChange = { draft = draft.copy(watermark = it) },
            isNightModeAuto = draft.nightModeAuto,
            onNightModeAutoChange = { draft = draft.copy(nightModeAuto = it) },
            aspectRatios = aspectRatios,
            currentAspect = draft.aspect,
            onAspectChange = { draft = draft.copy(aspect = it) },
            onRerunSetup = { requestLeave(onRerunSetup) }
        )

        UnsavedChangesDialog(
            visible = showSaveDialog,
            dark = dark,
            onYes = { answerDialog(save = true) },
            onNo = { answerDialog(save = false) }
        )
    }
}


@Composable
private fun SettingsScreenContent(
    onBack: () -> Unit,
    exp: ExpToggles,
    onExpChange: (ExpToggles) -> Unit,
    hasUnsavedChanges: Boolean,
    onSave: () -> Unit,
    uiColor: Color = Color.White,
    isFadeEffectEnabled: Boolean,
    onFadeEffectChange: (Boolean) -> Unit,
    isBouncyAnimationEnabled: Boolean,
    onBouncyAnimationChange: (Boolean) -> Unit,
    isHapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    isBlurEnabled: Boolean = true,
    onBlurChange: (Boolean) -> Unit = {},
    horizonMode: HorizonLockMode = HorizonLockMode.OFF,
    onHorizonModeChange: (HorizonLockMode) -> Unit = {},
    isGridEnabled: Boolean,
    onGridChange: (Boolean) -> Unit,
    isLevelerEnabled: Boolean,
    onLevelerChange: (Boolean) -> Unit,
    isEdgeBarsEnabled: Boolean = false,
    onEdgeBarsChange: (Boolean) -> Unit = {},
    isPalmGestureEnabled: Boolean,
    onPalmGestureChange: (Boolean) -> Unit,
    isCheeseEnabled: Boolean = false,
    onCheeseChange: (Boolean) -> Unit = {},
    currentShutterSound: ShutterSoundType,
    onShutterSoundChange: (ShutterSoundType) -> Unit,
    currentRecordSound: RecordSoundType,
    onRecordSoundChange: (RecordSoundType) -> Unit,
    currentTimerSound: TimerSoundType,
    onTimerSoundChange: (TimerSoundType) -> Unit,
    isWatermarkEnabled: Boolean,
    onWatermarkChange: (Boolean) -> Unit,
    isNightModeAuto: Boolean,
    onNightModeAutoChange: (Boolean) -> Unit,
    aspectRatios: List<String>,
    currentAspect: String,
    onAspectChange: (String) -> Unit,
    onRerunSetup: () -> Unit = {}
) {
    // Light / dark: follows the system. `ink` is text and icons, `inkInverse` is what sits on an `ink` pill.
    val dark = isSystemInDarkTheme()
    SystemBarIcons(darkIcons = !dark)
    val settingsBg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val cardBg = if (dark) Color(0xFF1C1C1E) else Color.White
    val ink = if (dark) Color.White else Color(0xFF1C1C1E)
    val inkInverse = if (dark) Color(0xFF14151B) else Color.White
    val blurSupported = Build.VERSION.SDK_INT >= 31
    // Fill of the floating pills. With blur it is only a faint tint, so the blurred list shows through the glass.
    val glassFill = when {
        blurSupported && dark -> Color.White.copy(alpha = 0.07f)
        blurSupported -> Color.White.copy(alpha = 0.30f)
        dark -> Color(0xFF2C2C2E).copy(alpha = 0.90f)
        else -> Color.White.copy(alpha = 0.90f)
    }
    // Fill of the three tab pills (Customization / General / Experimental): fully opaque, no blur shows through.
    val tabFill = if (dark) Color(0xFF2C2C2E) else Color.White
    // Eased stops for the soft fade at the top and bottom edge of the list (settings background colour).
    val fadeTopStops = remember(settingsBg) { easedFade(settingsBg, topToTransparent = true) }
    val fadeBottomStops = remember(settingsBg) { easedFade(settingsBg, topToTransparent = false) }
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }
    val previewSound = remember {
        try {
            MediaActionSound().apply {
                try { load(MediaActionSound.SHUTTER_CLICK) } catch (_: Exception) {}
                try { load(MediaActionSound.START_VIDEO_RECORDING) } catch (_: Exception) {}
                try { load(MediaActionSound.STOP_VIDEO_RECORDING) } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(previewSound) {
        onDispose {
            try {
                previewSound?.release()
            } catch (_: Exception) {}
        }
    }

    var currentTab by remember { mutableStateOf(SettingsTabType.CUSTOMIZATION) }
    var isExperimentalUnlocked by remember {
        mutableStateOf(prefs.getBoolean("experimental_unlocked", false))
    }
    var enteredPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    LaunchedEffect(currentTab) {
        try {
            scrollState.scrollTo(0)
        } catch (_: Throwable) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(settingsBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Main Content List (scrollable with top and bottom spacing for floating controls)
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Soft fade at the very top and bottom of the screen: the list dissolves into the screen colour
                // instead of being cut off. The brushes are built once per size (drawWithCache) and nothing in
                // here reads the scroll position, so scrolling never re-runs this draw code - it only moves the list.
                .drawWithCache {
                    val fadeTopPx = SettingsFadeTop.toPx()
                    val fadeBottomPx = SettingsFadeBottom.toPx()
                    val topBrush = Brush.verticalGradient(
                        colors = fadeTopStops,
                        startY = 0f,
                        endY = fadeTopPx
                    )
                    val bottomBrush = Brush.verticalGradient(
                        colors = fadeBottomStops,
                        startY = size.height - fadeBottomPx,
                        endY = size.height
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = topBrush,
                            topLeft = Offset.Zero,
                            size = Size(size.width, fadeTopPx)
                        )
                        drawRect(
                            brush = bottomBrush,
                            topLeft = Offset(0f, size.height - fadeBottomPx),
                            size = Size(size.width, fadeBottomPx)
                        )
                    }
                }
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp)
        ) {
            // Spacer for floating top header row and floating tabs row
            Spacer(modifier = Modifier.height(124.dp))

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    // slide in the direction of travel, with a small bounce, while the old tab fades out
                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                    ) { w -> dir * (w / 4) } + fadeIn(tween(240, delayMillis = 40)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                            ) { w -> -dir * (w / 4) } + fadeOut(tween(110))
                        )
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "SettingsTabContent"
            ) { tab ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (tab) {
                        SettingsTabType.CUSTOMIZATION -> {
                            // 1. Shutter Sound
                            SettingsSectionHeader(title = "SHUTTER SOUND")

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Choose auditory feedback when taking photos.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                ShutterSoundType.entries.forEach { soundType ->
                                    key(soundType.id) {
                                        val isSelected = currentShutterSound == soundType
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSelected) ink.copy(alpha = 0.12f) else ink.copy(alpha = 0.03f))
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                    onShutterSoundChange(soundType)
                                                    SoundUtils.playShutter(context, soundType, previewSound)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) ink else ink.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(inkInverse)
                                                        )
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = soundType.title,
                                                        color = ink,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = soundType.subtitle,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(ink.copy(alpha = 0.08f))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                        SoundUtils.playShutter(context, soundType, previewSound)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Audition sound",
                                                    tint = ink,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 2. Record Sound
                            SettingsSectionHeader(title = "RECORD SOUND")

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tone cue played on start and stop of video capture.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                RecordSoundType.entries.forEach { soundType ->
                                    key(soundType.id) {
                                        val isSelected = currentRecordSound == soundType
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSelected) ink.copy(alpha = 0.12f) else ink.copy(alpha = 0.03f))
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                    onRecordSoundChange(soundType)
                                                    SoundUtils.playRecordStartSound(context, soundType, previewSound)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) ink else ink.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(inkInverse)
                                                        )
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = soundType.title,
                                                        color = ink,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = soundType.subtitle,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(ink.copy(alpha = 0.08f))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                        SoundUtils.playRecordStartSound(context, soundType, previewSound)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Audition sound",
                                                    tint = ink,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 3. Timer Sound
                            SettingsSectionHeader(title = "TIMER COUNTDOWN SOUND")

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Acoustic pace during countdown self-timer interval.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                TimerSoundType.entries.forEach { soundType ->
                                    key(soundType.id) {
                                        val isSelected = currentTimerSound == soundType
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSelected) ink.copy(alpha = 0.12f) else ink.copy(alpha = 0.03f))
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                    onTimerSoundChange(soundType)
                                                    SoundUtils.playTimerTick(soundType)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) ink else ink.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(inkInverse)
                                                        )
                                                    }
                                                }

                                                Column {
                                                    Text(
                                                        text = soundType.title,
                                                        color = ink,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    if (soundType.subtitle.isNotEmpty()) {
                                                        Text(
                                                            text = soundType.subtitle,
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(ink.copy(alpha = 0.08f))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                        SoundUtils.playTimerTick(soundType)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = "Audition sound",
                                                    tint = ink,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsTabType.GENERAL -> {
                            // 1. Aspect Ratio of Image
                            SettingsSectionHeader(title = "ASPECT RATIO OF THE IMAGE")

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Choose image capture proportion and viewfinder framing.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    aspectRatios.forEach { aspect ->
                                        key(aspect) {
                                            val isSelected = currentAspect == aspect
                                            val (aspectLabel, aspectSub) = when (aspect) {
                                                "3:4" -> "3:4" to "Standard"
                                                "9:16" -> "9:16" to "Reel"
                                                "1:1" -> "1:1" to "Square"
                                                else -> "Full" to "Screen"
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(if (isSelected) ink else ink.copy(alpha = 0.05f))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                        onAspectChange(aspect)
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(
                                                                width = when (aspect) {
                                                                    "1:1" -> 22.dp
                                                                    "9:16" -> 16.dp
                                                                    else -> 20.dp
                                                                },
                                                                height = when (aspect) {
                                                                    "1:1" -> 22.dp
                                                                    "9:16" -> 28.dp
                                                                    else -> 26.dp
                                                                }
                                                            )
                                                            .background(if (isSelected) inkInverse else ink.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                    )
                                                    Text(
                                                        text = aspectLabel,
                                                        color = if (isSelected) inkInverse else ink,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = aspectSub,
                                                        color = if (isSelected) Color(0xFF333333) else Color(0xFF94A3B8),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 2. Grid Lines
                            SettingsSectionHeader(title = "GRID LINES")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "3x3 Rule of Thirds Grid",
                                        color = ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Overlays golden-ratio alignment lines for balanced framing and horizons.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }

                                GlassSwitch(
                                    checked = isGridEnabled,
                                    onCheckedChange = {
                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                        onGridChange(it)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 3. Straight Checker
                            SettingsSectionHeader(title = "STRAIGHT CHECKER")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Horizon Straight Checker",
                                        color = ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Displays real-time gyroscopic level crosshairs and degree tilt for zero skew.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }

                                GlassSwitch(
                                    checked = isLevelerEnabled,
                                    onCheckedChange = {
                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                        onLevelerChange(it)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Control bars
                            SettingsSectionHeader(title = "CONTROL BARS")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dark Bars Behind Controls",
                                        color = ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Shows a translucent dark container behind the top and bottom controls. Off by default for a full, clean preview.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }

                                GlassSwitch(
                                    checked = isEdgeBarsEnabled,
                                    onCheckedChange = {
                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                        onEdgeBarsChange(it)
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Camera setup: runs the Gemini research for this phone again (zoom buttons + video quality)
                            SettingsSectionHeader(title = "CAMERA SETUP")

                            val setupInfoText = remember {
                                val phone = prefs.getString("setup_ai_phone", null).orEmpty()
                                val summary = prefs.getString("setup_ai_summary", null).orEmpty()
                                if (phone.isNotBlank()) {
                                    listOf(phone, summary).filter { it.isNotBlank() }.joinToString(" · ") + ". Tap to check again."
                                } else {
                                    "Gemini looks up this phone's camera, then sets the zoom buttons and the video quality for it."
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .clickable {
                                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                        onRerunSetup()
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Set Up Camera With Gemini",
                                        color = ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = setupInfoText,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 4. Other General Utilities
                            SettingsSectionHeader(title = "CAMERA UTILITIES")

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Palm gesture
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Palm Gesture Shutter", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Front camera: show your open palm and a 3s countdown takes the selfie. Hand detection runs on your phone.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    GlassSwitch(
                                        checked = isPalmGestureEnabled,
                                        onCheckedChange = {
                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                            onPalmGestureChange(it)
                                        }
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                // Cheese (voice shutter)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Cheese", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Say \"cheese\" and the photo is taken instantly. Uses the microphone and the phone's own speech recognition, front or back camera.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    GlassSwitch(
                                        checked = isCheeseEnabled,
                                        onCheckedChange = {
                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                            onCheeseChange(it)
                                        }
                                    )
                                }

                                // AI Watermark
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "AI Camera Watermark", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Stamp captured photos with date, time, and camera optics signature.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    GlassSwitch(
                                        checked = isWatermarkEnabled,
                                        onCheckedChange = {
                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                            onWatermarkChange(it)
                                        }
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                // Auto Night Mode
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Auto Night Mode", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Automatically optimize exposure when low light environment detected.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    GlassSwitch(
                                        checked = isNightModeAuto,
                                        onCheckedChange = {
                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                            onNightModeAutoChange(it)
                                        }
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                // Blur
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Blur", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Frosted-glass blur behind the camera buttons and bars. Turn it off for plain dark controls and lower battery use.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    GlassSwitch(
                                        checked = isBlurEnabled,
                                        onCheckedChange = {
                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                            onBlurChange(it)
                                        }
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                // Haptics
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Haptic Vibration Feedback", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Tactile mechanical vibration when interacting with camera controls.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    GlassSwitch(
                                        checked = isHapticsEnabled,
                                        onCheckedChange = {
                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                            onHapticsChange(it)
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 5. About this app
                            SettingsSectionHeader(title = "ABOUT")
                            AboutThisAppCard(cardBg = cardBg, ink = ink, view = view)
                        }

                        SettingsTabType.EXPERIMENTAL -> {
                            if (!isExperimentalUnlocked) {
                                // Locked State Shield
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(cardBg)
                                        .padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = "Locked",
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    Text(
                                        text = "EXPERIMENTAL LAB LOCKED",
                                        color = ink,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )

                                    Text(
                                        text = "This area contains experimental sensor pipelines and live neural telemetry. Please enter security password kd84k58x to unlock access.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )

                                    // Password TextField
                                    OutlinedTextField(
                                        value = enteredPassword,
                                        onValueChange = {
                                            enteredPassword = it
                                            if (passwordError) passwordError = false
                                        },
                                        placeholder = { Text("Enter password...", color = Color(0xFF64748B), fontSize = 13.sp) },
                                        singleLine = true,
                                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (enteredPassword.trim() == "kd84k58x") {
                                                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                    isExperimentalUnlocked = true
                                                    prefs.edit().putBoolean("experimental_unlocked", true).apply()
                                                    passwordError = false
                                                } else {
                                                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                    passwordError = true
                                                }
                                            }
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                                Text(
                                                    text = if (isPasswordVisible) "HIDE" else "SHOW",
                                                    color = ink.copy(alpha = 0.7f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = ink,
                                            unfocusedTextColor = ink,
                                            focusedContainerColor = inkInverse,
                                            unfocusedContainerColor = inkInverse,
                                            focusedBorderColor = if (passwordError) Color(0xFFEF4444) else ink,
                                            unfocusedBorderColor = if (passwordError) Color(0xFFEF4444) else ink.copy(alpha = 0.25f),
                                            cursorColor = ink
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (passwordError) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Incorrect password. Required code: kd84k58x",
                                                color = Color(0xFFEF4444),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                enteredPassword = "kd84k58x"
                                                passwordError = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262833)),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Auto-fill Code", color = ink, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                if (enteredPassword.trim() == "kd84k58x") {
                                                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                    isExperimentalUnlocked = true
                                                    prefs.edit().putBoolean("experimental_unlocked", true).apply()
                                                    passwordError = false
                                                } else {
                                                    safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                    passwordError = true
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ink),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Unlock", color = inkInverse, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Unlocked State Content
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "EXPERIMENTAL LAB UNLOCKED",
                                            color = ink,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                            isExperimentalUnlocked = false
                                            prefs.edit().putBoolean("experimental_unlocked", false).apply()
                                        }
                                    ) {
                                        Text("Lock Tab", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                SettingsSectionHeader(title = "EXPERIMENTAL FEATURES")

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(cardBg)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // 1. Neural Denoise
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "AI Neural Denoise", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Multi-frame RAW sensor denoise computation for high-clarity low-light images.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                        GlassSwitch(
                                            checked = exp.neuralDenoise,
                                            onCheckedChange = {
                                                safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                onExpChange(exp.copy(neuralDenoise = it))
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                    // 2. Gemini Live Telemetry
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Gemini Live Telemetry", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Real-time semantic recognition HUD in the camera viewfinder.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                        GlassSwitch(
                                            checked = exp.geminiTelemetry,
                                            onCheckedChange = {
                                                safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                onExpChange(exp.copy(geminiTelemetry = it))
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                    // 3. 120 FPS Burst Mode
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "120 FPS Sensor Burst", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Unthrottled circular buffer pipeline for instant action capture.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                        GlassSwitch(
                                            checked = exp.highSpeedBurst,
                                            onCheckedChange = {
                                                safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                onExpChange(exp.copy(highSpeedBurst = it))
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                    // 4. Raw Sensor DNG Stream
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Raw Sensor DNG Stream", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Save uncompressed 14-bit Bayer RAW alongside standard JPEGs.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                        GlassSwitch(
                                            checked = exp.rawSensorDng,
                                            onCheckedChange = {
                                                safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                onExpChange(exp.copy(rawSensorDng = it))
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                    // 5. Vulkan Hardware Acceleration
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Vulkan Compute Acceleration", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "GPU acceleration for real-time frosted glass refraction shaders.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                        GlassSwitch(
                                            checked = exp.vulkanAcceleration,
                                            onCheckedChange = {
                                                safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                onExpChange(exp.copy(vulkanAcceleration = it))
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))

                                    // 6. Horizon Lock (video)
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column {
                                            Text(text = "Horizon Lock", color = ink, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Video only: keeps the viewfinder and the recorded video level while you tilt the phone. Adds a Horizon option to the video quick settings.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizonLockMode.values().forEach { option ->
                                                val isSelected = horizonMode == option
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(if (isSelected) ink else ink.copy(alpha = 0.05f))
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                                            onHorizonModeChange(option)
                                                        }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = option.label,
                                                        color = if (isSelected) inkInverse else ink,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        // Floating header, pinned at the top. Back, title and Save are solid (fully opaque) pills, and the three
        // tabs live in one floating bar whose pill slides to the chosen tab.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back: opaque
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .settingsOpaquePill(CircleShape, tabFill, !dark)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                            onBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ink,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Title: opaque
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .settingsOpaquePill(CircleShape, tabFill, !dark)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ink)
                        )
                        Text(
                            text = "Camera Settings",
                            color = ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }

                // Save: opaque, turns white and glows gently while there are unsaved changes
                SettingsSaveButton(
                    hasChanges = hasUnsavedChanges,
                    idleFill = if (dark) tabFill else Color(0xFFE5E5EA),
                    ink = ink,
                    dark = dark,
                    onClick = {
                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                        onSave()
                    }
                )
            }

            SettingsTabBar(
                currentTab = currentTab,
                isExperimentalUnlocked = isExperimentalUnlocked,
                barFill = tabFill,
                ink = ink,
                inkInverse = inkInverse,
                dark = dark,
                onTabSelected = { tab ->
                    if (tab != currentTab) {
                        safeHaptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                        currentTab = tab
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String
) {
    val ink = if (isSystemInDarkTheme()) Color.White else Color(0xFF1C1C1E)
    Text(
        text = title,
        color = ink.copy(alpha = 0.85f),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

/** Selected-item indicator inside the zoom bar: a plain gray circle with no edge lighting. */
@Composable
fun GlassIndicatorPill(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    Box(modifier = modifier.clip(shape).background(Color(0xFF5A5A5E).copy(alpha = 0.9f)))
}

@Composable
fun ExactZoomRow(
    currentZoom: Float,
    uiColor: Color,
    maxZoom: Float,
    minZoom: Float = 1f,
    onZoomChange: (Float) -> Unit
) {
    // Ultra-wide only exists on phones whose camera really zooms out below 1x (their minimum zoom is under 1).
    val ultraWide = if (minZoom < 0.95f) kotlin.math.ceil(minZoom * 10f - 0.001f) / 10f else null
    val mainZooms = remember(minZoom, maxZoom) {
        buildList {
            if (ultraWide != null) add(ultraWide)
            add(1f)
            if (maxZoom >= 2f) add(2f)
            if (maxZoom >= 5f) add(5f)
        }
    }
    // Far zoom presets come from this phone's own limit (10x, 30x, 50x, 70x, 100x ...), not from a fixed 10x.
    val autoExtraZooms = remember(maxZoom) {
        buildList {
            if (maxZoom >= 10f) add(10f) else if (maxZoom > 5.5f) add(kotlin.math.floor(maxZoom * 10f) / 10f)
            if (maxZoom >= 15f) {
                val top = kotlin.math.floor(maxZoom + 0.05f)
                if (top > 10f) add(top)
            }
        }
    }
    // If the first-run setup saved zoom presets for this phone (for example 10x, 30x, 50x), use those.
    val setupContext = LocalContext.current
    val extraZooms = remember(maxZoom, autoExtraZooms) {
        val saved = setupContext.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)
            .getString("zoom_presets", null)
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?.filter { it > 5.5f && it <= maxZoom + 0.5f }
        if (!saved.isNullOrEmpty()) saved else autoExtraZooms
    }
    val lastMain = mainZooms.last()
    val mainSelectedRange = extraZooms.isEmpty() || currentZoom <= lastMain

    val fractionalIndex = when {
        currentZoom <= mainZooms.first() -> 0f
        else -> {
            var index = 0f
            for (i in 0 until mainZooms.size - 1) {
                if (currentZoom >= mainZooms[i] && currentZoom <= mainZooms[i + 1]) {
                    index = i + (currentZoom - mainZooms[i]) / (mainZooms[i + 1] - mainZooms[i])
                    break
                }
            }
            if (currentZoom > lastMain) {
                index = (mainZooms.size - 1).toFloat()
            }
            index
        }
    }

    val indicatorOffset by animateDpAsState(
        targetValue = (fractionalIndex * 38).dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "ZoomIndicatorOffset"
    )

    // the extra chip that is lit: the highest preset not above the current zoom (the first one in between)
    val extraSelected = if (!mainSelectedRange) {
        maxOf(0, extraZooms.indexOfLast { it <= currentZoom + 0.05f })
    } else -1

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassContainer(
            shape = CircleShape,
            modifier = Modifier.padding(4.dp)
        ) {
            Box(
                modifier = Modifier.padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (mainSelectedRange) {
                    GlassIndicatorPill(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .size(38.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mainZooms.forEachIndexed { index, zoom ->
                        val isSelected = mainSelectedRange && abs(fractionalIndex - index) < 0.5f
                        val text = when {
                            zoom < 1f -> "." + (zoom * 10f).roundToInt()        // ultra-wide: .5 / .6
                            zoom == 1f -> "1x"
                            else -> "${zoom.toInt()}x"
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onZoomChange(zoom)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = text,
                                color = if (isSelected) uiColor else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (extraZooms.isNotEmpty()) {
            LiquidGlassContainer(
                shape = CircleShape,
                modifier = Modifier.padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    extraZooms.forEachIndexed { index, zoom ->
                        val isSelected = index == extraSelected
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .widthIn(min = 38.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onZoomChange(minOf(zoom, maxZoom))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                GlassIndicatorPill(modifier = Modifier.matchParentSize())
                            }
                            Text(
                                text = "${zoom.toInt()}x",
                                color = if (isSelected) uiColor else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovingMeshGradient(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    val transition = rememberInfiniteTransition(label = "MeshGradientTransition")
    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MeshT1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 13200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MeshT2"
    )
    val t3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MeshT3"
    )

    Canvas(
        modifier = modifier.clip(shape)
    ) {
        val w = size.width
        val h = size.height
        val maxDim = maxOf(w, h)

        val blueX = w * (0.35f + 0.35f * sin(t1))
        val blueY = h * (0.35f + 0.35f * cos(t2))

        val pinkX = w * (0.65f + 0.35f * cos(t2))
        val pinkY = h * (0.50f + 0.35f * sin(t3))

        val yellowX = w * (0.50f + 0.35f * sin(t3))
        val yellowY = h * (0.65f + 0.35f * cos(t1))

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFF1F5F9), Color(0xFFFFF1F2)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFBAE6FD), Color(0xFFE0F2FE), Color.Transparent),
                center = Offset(blueX, blueY),
                radius = maxDim * 0.95f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFBCFE8), Color(0xFFFCE7F3), Color.Transparent),
                center = Offset(pinkX, pinkY),
                radius = maxDim * 0.90f
            )
        )

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFEF08A), Color(0xFFFEF9C3), Color.Transparent),
                center = Offset(yellowX, yellowY),
                radius = maxDim * 0.85f
            )
        )
    }
}

/**
 * The liquid glass of the mode selector (Android 13+). It reads the blurred camera snapshot (CameraBackdrop) as the
 * scene "behind" the glass and, for every pixel:
 *  1. works out the distance to the glass edge (a rounded-box distance field) and the edge's outward direction,
 *  2. near the edge slides the place it reads the scene from OUTWARDS (a thick convex rim), with red / green / blue
 *     sliding by slightly different amounts (dispersion),
 *  3. boosts saturation, lays a tint over it, shades the rim away from the light (depth), adds a thin prism fringe
 *     (warm on the lit side, cool on the other) and a two-sided rim light.
 * The shader draws the shape itself (a capsule or a circle), so the area it paints needs no clip.
 */
private const val MODE_GLASS_AGSL = """
uniform shader backdrop;
uniform float2 uLayer;     // size of the painted area in px
uniform float2 uHalf;      // half width / half height of the glass shape in px (centred in the area)
uniform float uCorner;     // corner radius in px
uniform float uBevel;      // rim width in px
uniform float uRefract;    // rim bend strength
uniform float uMagnify;    // zoom of the middle
uniform float uDisp;       // colour dispersion
uniform float uSat;        // saturation
uniform float4 uTint;      // rgb + amount
uniform float uSpec;       // rim light strength
uniform float uInner;      // inner shadow strength
uniform float uAlpha;      // overall opacity (fades the glass in and out)
uniform float2 uLight;     // unit vector pointing towards the light
uniform float uPx;         // 1 dp in px

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float2 sdNormal(float2 p, float2 b, float r) {
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float2 q = abs(p) - b + float2(r);
    float2 g = float2(0.0, 1.0);
    if (q.x > 0.0 && q.y > 0.0) {
        g = normalize(q);
    } else if (q.x > q.y) {
        g = float2(1.0, 0.0);
    }
    return g * s;
}

half4 main(float2 fragCoord) {
    float2 c = uLayer * 0.5;
    float2 p = fragCoord - c;
    float d = sdBox(p, uHalf, uCorner);            // < 0 inside the glass
    float cover = clamp(0.5 - d, 0.0, 1.0);        // anti-aliased shape mask
    if (cover <= 0.0) {
        return half4(0.0);
    }
    float inside = max(-d, 0.0);                   // distance from the edge, inwards
    float2 n = sdNormal(p, uHalf, uCorner);        // outward direction of the nearest edge

    // convex rim: strong bend at the very edge, none in the flat middle
    float k = 1.0 - clamp(inside / uBevel, 0.0, 1.0);
    float disp = uRefract * uBevel * k * k * k;     // thin rim, fades out smoothly

    float2 base = p / uMagnify;
    float3 col = float3(
        backdrop.eval(c + base * (1.0 - 0.04 * uDisp) + n * disp * (1.0 - uDisp)).r,
        backdrop.eval(c + base + n * disp).g,
        backdrop.eval(c + base * (1.0 + 0.04 * uDisp) + n * disp * (1.0 + uDisp)).b
    );

    // colour: richer, then the tint
    float luma = dot(col, float3(0.2126, 0.7152, 0.0722));
    col = mix(float3(luma), col, uSat);
    col = mix(col, uTint.rgb, uTint.a);

    // depth: the rim away from the light is shaded, the body gets a faint sheen from above
    float lit = dot(n, uLight);                    // 1 facing the light, -1 facing away
    float toward = clamp(lit, 0.0, 1.0);
    float away = clamp(-lit, 0.0, 1.0);
    float rim = 1.0 - smoothstep(0.0, uBevel * 1.1, inside);
    col *= 1.0 - uInner * rim * (0.25 + 0.75 * away);
    float vy = clamp(p.y / max(uHalf.y, 1.0), -1.0, 1.0);
    col += uSpec * 0.05 * (0.5 - 0.5 * vy);

    // very soft prism glow just inside the rim: warm on the lit side, cool on the other
    float band = 1.0 - smoothstep(0.0, 1.2 * uPx, inside);
    col += band * band * uDisp * 0.30 * (toward * float3(0.95, 0.45, 0.20) + away * float3(0.20, 0.55, 1.00));

    // rim light: a thin bright line plus a soft glow, strong on the lit side and weaker on the opposite side
    float thin = 1.0 - smoothstep(0.0, 0.5 * uPx, inside);
    float soft = 1.0 - smoothstep(0.0, uBevel * 0.6, inside);
    float side = pow(toward, 1.5) + 0.55 * pow(away, 1.5);
    col += uSpec * (thin * (0.16 + 0.95 * side) + soft * 0.05 * side);
    col = clamp(col, 0.0, 1.0);

    return half4(half3(col), 1.0) * half(cover * uAlpha);
}
"""

/**
 * The look of the liquid glass lens. The held lens AND the resting mode bar both read these numbers, so the bar is
 * always the same glass as the lens. Change a value here and both change together.
 */
private const val LENS_BEVEL_DP = 11f              // width of the bending rim
private const val LENS_REFRACTION = 0.55f          // rim bend at rest
private const val LENS_DISPERSION = 0.12f          // red / blue colour split at the rim
private const val LENS_BACKDROP_ZOOM = 0.12f       // how much the scene behind the glass is magnified
private const val LENS_SATURATION = 1.3f           // colour pulled from the camera snapshot
private const val LENS_TINT_ALPHA = 0.28f          // dark tint laid over the glass
private const val LENS_SPECULAR = 0.85f            // rim light
private const val LENS_INNER_SHADOW = 0.20f        // depth shading on the side away from the light

/**
 * Paints one liquid glass shape with MODE_GLASS_AGSL. The scene behind it is the camera snapshot, placed exactly
 * where the preview really is on screen (the same alignment LiquidGlassContainer uses). One painter per glass shape.
 */
@androidx.annotation.RequiresApi(33)
private class ModeGlassPainter {
    private val shader = android.graphics.RuntimeShader(MODE_GLASS_AGSL)
    private val paint = android.graphics.Paint()
    private val matrix = android.graphics.Matrix()
    private var bitmap: Bitmap? = null
    private var bitmapShader: android.graphics.BitmapShader? = null

    fun draw(
        scope: DrawScope,
        backdrop: CameraBackdrop,
        areaOrigin: Offset,         // top-left of the painted area, in root coordinates
        halfW: Float,               // glass half width / half height in px (the glass is centred in the area)
        halfH: Float,
        bevel: Float,
        refraction: Float,
        magnify: Float,
        dispersion: Float,
        saturation: Float,
        tint: Color,
        specular: Float,
        innerShadow: Float,
        alpha: Float
    ) {
        val img = backdrop.image ?: return
        val previewW = backdrop.size.width
        val previewH = backdrop.size.height
        if (previewW <= 0f || previewH <= 0f) return
        val bmp = img.asAndroidBitmap()
        var bs = bitmapShader
        if (bs == null || bitmap !== bmp) {
            bs = android.graphics.BitmapShader(
                bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP
            )
            bs.filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR
            bitmapShader = bs
            bitmap = bmp
        }
        // snapshot pixels -> pixels of the painted area: the preview's top-left is (dx, dy) away from our own
        matrix.reset()
        matrix.setScale(previewW / bmp.width, previewH / bmp.height)
        matrix.postTranslate(backdrop.origin.x - areaOrigin.x, backdrop.origin.y - areaOrigin.y)
        bs.setLocalMatrix(matrix)
        shader.setInputShader("backdrop", bs)

        val w = scope.size.width
        val h = scope.size.height
        shader.setFloatUniform("uLayer", w, h)
        shader.setFloatUniform("uHalf", halfW, halfH)
        shader.setFloatUniform("uCorner", minOf(halfW, halfH))
        shader.setFloatUniform("uBevel", maxOf(bevel, 1f))
        shader.setFloatUniform("uRefract", refraction)
        shader.setFloatUniform("uMagnify", magnify)
        shader.setFloatUniform("uDisp", dispersion)
        shader.setFloatUniform("uSat", saturation)
        shader.setFloatUniform("uTint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("uSpec", specular)
        shader.setFloatUniform("uInner", innerShadow)
        shader.setFloatUniform("uAlpha", alpha)
        shader.setFloatUniform("uLight", -0.55f, -0.835f)      // the light comes from the top left
        shader.setFloatUniform("uPx", scope.density)
        paint.shader = shader
        scope.drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRect(0f, 0f, w, h, paint) }
    }
}

/**
 * A capsule of liquid glass: the bar behind the mode names and the floating quick-setting pills. On Android 13+ with a camera snapshot it is liquid glass (refracting rim, colour
 * fringe, depth, rim light); otherwise it is the usual frosted LiquidGlassContainer.
 */
@Composable
private fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    // true = exactly the glass of the held lens (used by the mode bar); false = the lighter look of the quick-setting pills
    lens: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalCameraBackdrop.current
    if (Build.VERSION.SDK_INT >= 33 && backdrop != null && backdrop.ready) {
        LiquidGlassPillShader(modifier = modifier, backdrop = backdrop, lens = lens, content = content)
    } else {
        LiquidGlassContainer(shape = CircleShape, modifier = modifier, content = content)
    }
}

@androidx.annotation.RequiresApi(33)
@Composable
private fun LiquidGlassPillShader(
    modifier: Modifier,
    backdrop: CameraBackdrop,
    lens: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val painter = remember { ModeGlassPainter() }
    var myOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned { myOrigin = it.positionInRoot() }
            .drawBehind {
                backdrop.frame // subscribe to new snapshots
                if (lens) {
                    // Same glass numbers (LENS_*) as the quick-setting pills use: same colour pull, same rim bend, same colour split,
                    // same magnified scene behind the glass, same rim light and depth.
                    painter.draw(
                        scope = this,
                        backdrop = backdrop,
                        areaOrigin = myOrigin,
                        halfW = size.width / 2f,
                        halfH = size.height / 2f,
                        bevel = minOf(LENS_BEVEL_DP.dp.toPx(), size.height * 0.45f),
                        refraction = LENS_REFRACTION,
                        magnify = 1f + LENS_BACKDROP_ZOOM,
                        dispersion = LENS_DISPERSION,
                        saturation = LENS_SATURATION,
                        tint = Color.Black.copy(alpha = LENS_TINT_ALPHA),
                        specular = LENS_SPECULAR,
                        innerShadow = LENS_INNER_SHADOW,
                        alpha = 1f
                    )
                } else {
                    painter.draw(
                        scope = this,
                        backdrop = backdrop,
                        areaOrigin = myOrigin,
                        halfW = size.width / 2f,
                        halfH = size.height / 2f,
                        bevel = minOf(12.dp.toPx(), size.height * 0.45f),
                        refraction = 0.45f,
                        magnify = 1f,
                        dispersion = 0.10f,
                        saturation = 1.2f,
                        tint = Color.Black.copy(alpha = 0.34f),     // keeps the grey labels readable
                        specular = 0.70f,
                        innerShadow = 0.18f,
                        alpha = 1f
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The liquid glass lens of the mode selector (Android 13+). ONE shader paints it, and it is applied to the whole bar
 * (hairline outline + the mode names) as a render effect, so the lens really refracts what is under it:
 *
 *  1. The middle of the lens magnifies the names (MODE_LENS_ZOOM) and keeps them sharp and readable.
 *  2. Only the rim bends the light. There the picture is pulled INWARDS, so strongly that it folds over itself: letters
 *     that slide under the rim smear sideways, flip, and split into red / green / blue fringes (each colour is bent
 *     by a slightly different amount, so the split is zero in the middle and grows towards the rim).
 *  3. The body of the glass is a clear smoky graphite tint with a soft sheen along the top.
 *  4. A thin rim light: bright on top, warm (orange) on the left edge, cool (blue) on the right edge.
 *  5. A soft shadow lifts the lens off the bar while it is popped out.
 *
 * Outside the lens nothing changes. All colours are premultiplied, as AGSL expects.
 */
private const val MODE_LENS_AGSL = """
uniform shader content;
uniform float2 uSize;      // size of the layer in px
uniform float2 uHalf;      // half width / half height of the lens in px (the lens is centred in the layer)
uniform float uAmt;        // 0..1: how much lens there is (fades it in and out)
uniform float uBevel;      // width of the bending rim in px
uniform float uBend;       // how far the rim pulls the picture inwards (1 = one rim width)
uniform float uFalloff;    // how fast the bend dies away towards the middle (higher = thinner rim)
uniform float uZoom;       // magnification of the middle
uniform float uDisp;       // colour split of the bend (red / blue)
uniform float uTint;       // dark tint of the glass body
uniform float uSheen;      // light sheen along the top
uniform float uGain;       // the names inside the lens get a little brighter
uniform float uRim;        // rim light strength
uniform float uShadow;     // shadow under the lens
uniform float uPx;         // 1 dp in px

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float2 sdNormal(float2 p, float2 b, float r) {
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    float2 q = abs(p) - b + float2(r);
    float2 g = float2(0.0, 1.0);
    if (q.x > 0.0 && q.y > 0.0) {
        g = normalize(q);
    } else if (q.x > q.y) {
        g = float2(1.0, 0.0);
    }
    return g * s;
}

half4 main(float2 fragCoord) {
    half4 base = content.eval(fragCoord);
    if (uAmt < 0.002) {
        return base;
    }
    float2 c = uSize * 0.5;
    float2 p = fragCoord - c;
    float r = uHalf.y;
    float sd = sdBox(p, uHalf, r);                 // < 0 inside the lens
    float cover = clamp(0.5 - sd, 0.0, 1.0);       // anti-aliased shape mask

    // soft shadow under the lens (only outside of it)
    float sdS = sdBox(p - float2(0.0, 4.0 * uPx), uHalf, r);
    float sh = (1.0 - smoothstep(-2.0 * uPx, 12.0 * uPx, sdS)) * uShadow * uAmt;
    float4 under = float4(base) + float4(0.0, 0.0, 0.0, sh) * (1.0 - float(base.a));
    if (cover <= 0.0) {
        return half4(under);
    }

    float inside = max(-sd, 0.0);                  // distance from the edge, inwards
    float2 n = sdNormal(p, uHalf, r);              // outward direction of the nearest edge

    // the bend: strong at the very edge, none in the flat middle
    float t = clamp(inside / uBevel, 0.0, 1.0);
    float g = pow(1.0 - t, uFalloff);
    float bend = uBend * uBevel * g;
    float2 zp = p / uZoom;
    float2 pr = mix(p, zp - n * bend * (1.0 - uDisp), uAmt);
    float2 pg = mix(p, zp - n * bend, uAmt);
    float2 pb = mix(p, zp - n * bend * (1.0 + uDisp), uAmt);
    half4 cr = content.eval(c + pr);
    half4 cg = content.eval(c + pg);
    half4 cb = content.eval(c + pb);
    float a = max(float(cr.a), max(float(cg.a), float(cb.a)));
    float3 rgb = float3(float(cr.r), float(cg.g), float(cb.b));
    rgb = min(rgb * mix(1.0, uGain, uAmt), float3(a));

    // glass body: clear dark tint plus a sheen that is strongest at the top and gone by the middle
    float v = clamp((p.y + uHalf.y) / (2.0 * uHalf.y), 0.0, 1.0);
    float sheen = uSheen * (1.0 - smoothstep(0.0, 0.55, v)) * uAmt;
    float bodyA = clamp(uTint * uAmt + sheen, 0.0, 1.0);
    rgb = rgb + float3(sheen) * (1.0 - a);         // the names stay in front of the body
    a = a + bodyA * (1.0 - a);

    // rim light: bright on top, warm on the left edge, cool on the right edge
    float thin = 1.0 - smoothstep(0.0, 1.6 * uPx, inside);
    float soft = 1.0 - smoothstep(0.0, 5.0 * uPx, inside);
    float up = max(-n.y, 0.0);
    float dn = max(n.y, 0.0);
    float side = pow(up, 1.2) + 0.30 * pow(dn, 1.5) + 0.18 * abs(n.x);
    float warm = max(-n.x, 0.0);
    float cool = max(n.x, 0.0);
    float rim = uRim * (thin * (0.30 + 0.60 * side) + soft * 0.14 * side);
    float s2 = soft * soft;
    float3 glow = float3(
        rim + s2 * (0.34 * warm + 0.02 * cool),
        rim + s2 * (0.10 * warm + 0.18 * cool),
        rim + s2 * (0.03 * warm + 0.42 * cool)
    ) * 0.9 * uAmt;
    rgb = min(rgb + glow, float3(1.0));
    a = clamp(max(a, max(rgb.r, max(rgb.g, rgb.b))), 0.0, 1.0);

    return half4(mix(under, float4(rgb, a), cover));
}
"""

/** The look of the mode selector. Change a value here and the bar and the lens change together. */
private const val MODE_LENS_BEVEL_DP = 16f              // width of the bending rim
private const val MODE_LENS_BEND = 0.90f                // how far the rim pulls the picture in (1 = one rim width)
private const val MODE_LENS_FALLOFF = 1.6f              // higher = thinner, sharper rim
private const val MODE_LENS_ZOOM = 1.25f                // magnification of the names inside the lens
private const val MODE_LENS_DISPERSION = 0.30f          // red / blue colour split at the rim
private const val MODE_LENS_DISPERSION_STRETCH = 0.12f  // extra split with finger speed
private const val MODE_LENS_TINT = 0.16f                // dark tint of the glass body (0 = fully clear)
private const val MODE_LENS_SHEEN = 0.13f               // sheen along the top of the lens
private const val MODE_LENS_TEXT_GAIN = 1.18f           // names inside the lens are a bit brighter
private const val MODE_LENS_RIM = 1.0f                  // rim light strength
private const val MODE_LENS_SHADOW = 0.22f              // shadow that lifts the lens off the bar
private const val MODE_BAR_OUTLINE_ALPHA = 0.50f        // hairline outline of the bar
private const val MODE_BAR_FILL_ALPHA = 0.14f           // faint dark fill so the names stay readable on a bright scene

@androidx.annotation.RequiresApi(33)
private fun createModeLensShader(): Any = android.graphics.RuntimeShader(MODE_LENS_AGSL)

@androidx.annotation.RequiresApi(33)
private fun modeLensEffect(
    shader: Any,
    width: Float,
    height: Float,
    halfWidth: Float,
    halfHeight: Float,
    bevel: Float,
    amount: Float,
    dispersion: Float,
    px: Float
): androidx.compose.ui.graphics.RenderEffect {
    val s = shader as android.graphics.RuntimeShader
    s.setFloatUniform("uSize", width, height)
    s.setFloatUniform("uHalf", halfWidth, halfHeight)
    s.setFloatUniform("uAmt", amount)
    s.setFloatUniform("uBevel", maxOf(bevel, 1f))
    s.setFloatUniform("uBend", MODE_LENS_BEND)
    s.setFloatUniform("uFalloff", MODE_LENS_FALLOFF)
    s.setFloatUniform("uZoom", MODE_LENS_ZOOM)
    s.setFloatUniform("uDisp", dispersion)
    s.setFloatUniform("uTint", MODE_LENS_TINT)
    s.setFloatUniform("uSheen", MODE_LENS_SHEEN)
    s.setFloatUniform("uGain", MODE_LENS_TEXT_GAIN)
    s.setFloatUniform("uRim", MODE_LENS_RIM)
    s.setFloatUniform("uShadow", MODE_LENS_SHADOW)
    s.setFloatUniform("uPx", px)
    return android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
}

/**
 * Tint laid over the blurred camera behind the mode bar. The bar's own faint fill (MODE_BAR_FILL_ALPHA) sits on top of
 * it, and together they darken the glass about as much as the other floating bars do (GlassTintTop = 0.40).
 */
private const val MODE_BAR_BLUR_TINT_ALPHA = 0.30f

/**
 * Real-time frosted glass behind the mode bar: the blurred camera snapshot every other floating bar uses (see
 * BackdropCaptureEffect), lined up with what is really behind the bar and clipped to the bar's capsule.
 * It is a separate layer UNDER the lens layer of ExactModeRow, so the liquid glass lens itself is not touched.
 * It fades in the first time a snapshot exists, and follows the bar while the bar slides.
 */
@Composable
private fun ModeBarBlur(
    backdrop: CameraBackdrop,
    modifier: Modifier = Modifier
) {
    // The bar's position is read while drawing (not only once after layout), so the blur stays lined up while the bar slides.
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val fade = animateFloatAsState(
        targetValue = if (backdrop.ready) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "ModeBarBlurFade"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = fade.value
                shape = CircleShape
                clip = true
            }
            .onGloballyPositioned { coords[0] = it }
            .drawBehind {
                backdrop.frame // subscribe to new snapshots
                val img = backdrop.image ?: return@drawBehind
                val bar = coords[0]
                if (bar == null || !bar.isAttached) return@drawBehind
                val previewSize = backdrop.size
                if (previewSize.width <= 0f || previewSize.height <= 0f) return@drawBehind
                val barOrigin = bar.positionInRoot()
                val ox = (backdrop.origin.x - barOrigin.x).roundToInt()
                val oy = (backdrop.origin.y - barOrigin.y).roundToInt()
                val pw = previewSize.width.roundToInt()
                val ph = previewSize.height.roundToInt()
                val barH = size.height.roundToInt()
                // Where the bar hangs over the black bars below / above the preview, stretch the preview's edge row
                // (the same way LiquidGlassContainer does), so the blur never ends in a hard edge.
                val belowTop = oy + ph
                if (belowTop < barH) {
                    val startY = belowTop.coerceAtLeast(0)
                    drawImage(
                        image = img,
                        srcOffset = IntOffset(0, img.height - 1),
                        srcSize = IntSize(img.width, 1),
                        dstOffset = IntOffset(ox, startY),
                        dstSize = IntSize(pw, barH - startY),
                        filterQuality = FilterQuality.Low
                    )
                }
                if (oy > 0) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset(0, 0),
                        srcSize = IntSize(img.width, 1),
                        dstOffset = IntOffset(ox, 0),
                        dstSize = IntSize(pw, oy.coerceAtMost(barH)),
                        filterQuality = FilterQuality.Low
                    )
                }
                drawImage(
                    image = img,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(ox, oy),
                    dstSize = IntSize(pw, ph),
                    filterQuality = FilterQuality.Medium
                )
                // dark tint so the grey names stay readable on a bright scene
                drawRect(Color.Black.copy(alpha = MODE_BAR_BLUR_TINT_ALPHA))
            }
    )
}

/**
 * Mode selector. Three modes are visible at a time; the pill stays fixed in the middle and the labels run past it
 * in an endless loop (after the last mode comes the first again). At rest the bar is just a hairline outline and the
 * chosen mode sits in a solid pill. Press and hold the pill: it pops out of the bar (taller and wider than the bar,
 * with a springy overshoot) and turns into a liquid glass lens that magnifies the names and bends them at its rim.
 * Slide left or right to bring the other modes in (they fade and shrink toward the ends of the bar); the lens
 * stretches a little with the speed of the finger. Let go to choose the mode that is under the lens: the pill drops
 * back into the bar with a bounce. A quick tap on a label still picks it.
 *
 * Layers, bottom to top:
 *  0. a real-time blur of the camera behind the bar (ModeBarBlur). It sits UNDER everything below, so the lens is untouched.
 *  1. the "scene": bar outline + grey names. While held, the lens shader (MODE_LENS_AGSL) is applied to this layer.
 *  2. the solid resting pill with its dark name (fades out while the lens fades in)
 *  3. a plain glass lens, only on Android 12 and older (no AGSL there)
 *  4. an invisible touch surface
 */
@Composable
fun ExactModeRow(
    selectedMode: CameraMode,
    uiColor: Color,
    onModeChange: (CameraMode) -> Unit,
    onHoldStart: () -> Unit = {},
    onTick: () -> Unit = {}
) {
    val modes = remember {
        listOf(CameraMode.VIDEO, CameraMode.PHOTO, CameraMode.PRO, CameraMode.PANORAMA, CameraMode.SEARCH)
    }
    val count = modes.size
    val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)

    val itemWidth = 76.dp
    val itemHeight = 44.dp
    val barPadding = 4.dp
    val visibleSlots = 3
    val barWidth = itemWidth * visibleSlots + barPadding * 2
    val barHeight = itemHeight + barPadding * 2
    // Room above and below the bar, so the popped-out lens and its shadow are never cut off.
    // The row itself keeps the height of the bar, so nothing around it moves.
    val overflow = 26.dp

    // Carousel position in item units. It is unbounded (the labels wrap around), the label whose index equals
    // this value modulo the number of modes sits exactly under the pill.
    var scrollPos by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var scrubbing by remember { mutableStateOf(false) }
    val selectedIndexNow = rememberUpdatedState(selectedIndex)
    val onModeChangeNow = rememberUpdatedState(onModeChange)
    val onHoldStartNow = rememberUpdatedState(onHoldStart)
    val onTickNow = rememberUpdatedState(onTick)

    // Drag-speed "stretch" of the lens (0 = none). Driven from the gesture below.
    val uiScope = rememberCoroutineScope()
    val stretch = remember { Animatable(0f) }
    val stretchJob = remember { arrayOfNulls<Job>(1) }

    // Whenever the finger is up, glide to the chosen mode along the shortest way round the loop.
    // A slightly lower damping ratio gives the labels a small bounce when they settle.
    LaunchedEffect(selectedIndex, scrubbing) {
        if (!scrubbing) {
            val turns = ((scrollPos - selectedIndex) / count).roundToInt()
            animate(
                initialValue = scrollPos,
                targetValue = (selectedIndex + turns * count).toFloat(),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
            ) { value, _ -> scrollPos = value }
            scrollPos = selectedIndex.toFloat()      // same picture, keeps the number small
        }
    }

    // Which mode is under the pill right now (changes the pill colour while sliding)
    val modeUnderPill by remember { derivedStateOf { modes[scrollPos.roundToInt().mod(count)] } }

    // 0 = resting pill, 1 = held: the pill is a glass lens. Plain tween, never overshoots (it drives alphas).
    val lens = animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "ModeLensAmount"
    )
    // How far the pill has popped out of the bar. A low damping ratio makes it overshoot when it pops out and
    // dip below 0 (a little squash) when it is released, which is the bouncy feel.
    val pop = animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "ModeLensPop"
    )
    val lensShader: Any? = remember { if (Build.VERSION.SDK_INT >= 33) createModeLensShader() else null }

    // Size of the lens relative to the resting pill (all read in the draw phase only).
    // At pop = 1 it is 30% wider and 42% taller: about 5dp taller than the bar on both the top and the bottom.
    val lensScaleX: () -> Float = { 1f + 0.30f * pop.value + 0.12f * stretch.value }
    val lensScaleY: () -> Float = { 1f + 0.42f * pop.value - 0.06f * stretch.value }
    // The faster the finger, the wider the colour split at the rim.
    val lensDispersion: () -> Float = {
        MODE_LENS_DISPERSION + MODE_LENS_DISPERSION_STRETCH * stretch.value.coerceIn(0f, 1f)
    }

    // The live camera snapshot that all the floating glass bars blur (null in previews).
    val blurBackdrop = LocalCameraBackdrop.current

    Box(
        modifier = Modifier.width(barWidth).height(barHeight),
        contentAlignment = Alignment.Center
    ) {
        // ---- 0. real-time blur of the camera behind the bar. Its own layer under the lens layer, so the lens is not changed ----
        if (blurBackdrop != null) {
            ModeBarBlur(backdrop = blurBackdrop, modifier = Modifier.size(barWidth, barHeight))
        }

        // ---- 1. the scene the lens refracts: hairline outline + the names ----
        Box(
            modifier = Modifier
                .requiredSize(barWidth, barHeight + overflow * 2)
                .graphicsLayer {
                    val a = lens.value.coerceIn(0f, 1f)
                    renderEffect = if (lensShader != null && Build.VERSION.SDK_INT >= 33 && a > 0.003f) {
                        val hh = itemHeight.toPx() * lensScaleY() / 2f
                        modeLensEffect(
                            shader = lensShader,
                            width = size.width,
                            height = size.height,
                            halfWidth = itemWidth.toPx() * lensScaleX() / 2f,
                            halfHeight = hh,
                            bevel = minOf(MODE_LENS_BEVEL_DP.dp.toPx(), hh * 0.8f),
                            amount = a,
                            dispersion = lensDispersion(),
                            px = density
                        )
                    } else {
                        null
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // the bar: a hairline outline and a very faint fill, nothing else
            Box(
                modifier = Modifier
                    .size(barWidth, barHeight)
                    .background(Color.Black.copy(alpha = MODE_BAR_FILL_ALPHA), CircleShape)
                    .border(GlassOutlineWidth, Color.White.copy(alpha = MODE_BAR_OUTLINE_ALPHA), CircleShape)
            )
            // the names, cut off by the ends of the bar like in the original
            Box(
                modifier = Modifier.size(barWidth, barHeight).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ModeCarouselLabels(
                    modes = modes,
                    scroll = { scrollPos },
                    itemWidth = itemWidth,
                    color = { Color(0xFFE2E2E8) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ---- 2. the solid resting pill: solid accent (or the Search gradient) with the dark name ----
        Box(
            modifier = Modifier
                .size(itemWidth, itemHeight)
                .graphicsLayer {
                    // pops out of the bar together with the lens and fades away while the lens fades in
                    scaleX = lensScaleX()
                    scaleY = lensScaleY()
                    alpha = (1f - lens.value).coerceIn(0f, 1f)
                }
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (modeUnderPill == CameraMode.SEARCH) {
                MovingMeshGradient(modifier = Modifier.fillMaxSize(), shape = CircleShape)
            } else {
                Box(modifier = Modifier.fillMaxSize().background(uiColor))
            }
            // the pill is stretched unevenly; the inverse scale here keeps the name at its normal size
            Box(
                modifier = Modifier
                    .requiredSize(itemWidth * visibleSlots, itemHeight)
                    .graphicsLayer {
                        scaleX = 1f / lensScaleX()
                        scaleY = 1f / lensScaleY()
                    },
                contentAlignment = Alignment.Center
            ) {
                ModeCarouselLabels(
                    modes = modes,
                    scroll = { scrollPos },
                    itemWidth = itemWidth,
                    color = { if (it == CameraMode.SEARCH) Color(0xFF0F172A) else Color.Black },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ---- 3. Android 12 and older: no AGSL, so a plain glass lens with white names (no refraction) ----
        if (Build.VERSION.SDK_INT < 33) {
            Box(
                modifier = Modifier
                    .size(itemWidth, itemHeight)
                    .graphicsLayer {
                        scaleX = lensScaleX()
                        scaleY = lensScaleY()
                        alpha = lens.value.coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color(0xFF3B3B3F),
                            0.50f to Color(0xFF1A1A1C),
                            1.00f to Color(0xFF151517)
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            0.00f to Color.White.copy(alpha = 0.90f),
                            0.35f to Color.White.copy(alpha = 0.18f),
                            0.70f to Color.White.copy(alpha = 0.18f),
                            1.00f to Color.White.copy(alpha = 0.75f)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(itemWidth * visibleSlots, itemHeight)
                        .graphicsLayer {
                            scaleX = 1.12f / lensScaleX()
                            scaleY = 1.12f / lensScaleY()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ModeCarouselLabels(
                        modes = modes,
                        scroll = { scrollPos },
                        itemWidth = itemWidth,
                        color = { Color.White },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ---- 4. the touch surface (invisible) ----
        Box(
            modifier = Modifier
                .size(barWidth, barHeight)
                .pointerInput(Unit) {
                    val itemPx = itemWidth.toPx()
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startScroll = scrollPos
                        // Wait a moment: lifted early = tap (1), moved = swipe (2). Holding still also counts as (2).
                        val early = withTimeoutOrNull(220L) {
                            var result = 0
                            while (result == 0) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    result = 1
                                } else if ((change.position - down.position).getDistance() > slop) {
                                    result = 2
                                }
                            }
                            result
                        }
                        if (early == 1) {
                            // plain tap: choose the label that was tapped
                            val slot = ((down.position.x - size.width / 2f) / itemPx).roundToInt()
                            val target = (startScroll.roundToInt() + slot).mod(count)
                            if (target != selectedIndexNow.value) onModeChangeNow.value(modes[target])
                            return@awaitEachGesture
                        }

                        // press and hold / swipe: the labels follow the finger, round and round
                        scrubbing = true
                        onHoldStartNow.value()
                        var lastTick = startScroll.roundToInt()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - down.position.x
                            val next = startScroll - dx / itemPx
                            scrollPos = next
                            val idx = next.roundToInt()
                            if (idx != lastTick) {
                                lastTick = idx
                                onTickNow.value()
                            }

                            // finger speed (px per ms) -> how much the lens stretches; it relaxes again
                            // if no new movement arrives for a moment
                            val stepX = change.position.x - change.previousPosition.x
                            val stepMs = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                            val speed = abs(stepX) / stepMs.toFloat()
                            val stretchTarget = (speed / 2.5f).coerceIn(0f, 1f)
                            stretchJob[0]?.cancel()
                            stretchJob[0] = uiScope.launch {
                                stretch.animateTo(
                                    stretchTarget,
                                    spring(dampingRatio = 0.6f, stiffness = 500f)
                                )
                                delay(60L)
                                stretch.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.45f, stiffness = 400f)
                                )
                            }
                            change.consume()
                        }
                        // let go: the stretch springs back and we choose the mode under the lens
                        stretchJob[0]?.cancel()
                        stretchJob[0] = uiScope.launch {
                            stretch.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 400f))
                        }
                        val target = scrollPos.roundToInt().mod(count)
                        scrubbing = false
                        if (target != selectedIndexNow.value) onModeChangeNow.value(modes[target])
                    }
                }
        )
    }
}

/**
 * The sliding selection pill inside a glass bar. It is elastic: it chases the position the bar asks for on a springy
 * rope, so it trails behind, stretches along the way (a little longer and thinner, like a drop of liquid), overshoots
 * and wobbles into place. A soft highlight and a rim light give it the look of a glass bead.
 *
 * It never leaves its bar: [trackWidth] is the width of the whole row of items. However far the spring overshoots or
 * the pill stretches, the pill is held inside 0..[trackWidth], so on the first and last item it squashes against the
 * end of the bar instead of poking out of the glass.
 */
@Composable
private fun LiquidIndicator(offset: Dp, itemWidth: Dp, itemHeight: Dp, color: Color, trackWidth: Dp) {
    val follow by animateFloatAsState(
        targetValue = offset.value,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "IndicatorJelly"
    )
    val itemW = itemWidth.value
    val trackW = trackWidth.value
    val stretch = (abs(offset.value - follow) / itemW * 1.6f).coerceIn(0f, 0.5f)
    // stretched width, never wider than the bar itself
    val pillW = (itemW * (1f + 0.9f * stretch)).coerceAtMost(trackW)
    // grow around the pill's centre, then slide the pill back inside the bar if it would stick out at either end
    val pillLeft = (follow + itemW / 2f - pillW / 2f).coerceIn(0f, (trackW - pillW).coerceAtLeast(0f))
    val squash = pillW / itemW
    Box(
        modifier = Modifier
            .offset(x = pillLeft.dp)
            .width(pillW.dp)
            .height(itemHeight)
            .graphicsLayer {
                scaleY = 1f / sqrt(squash)         // keeps the volume about the same
            }
            .clip(CircleShape)
            .background(color)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.White.copy(alpha = 0.38f),
                        0.45f to Color.White.copy(alpha = 0.05f),
                        1.00f to Color.Black.copy(alpha = 0.10f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0.00f to Color.White.copy(alpha = 0.90f),
                        0.35f to Color.White.copy(alpha = 0.15f),
                        0.65f to Color.White.copy(alpha = 0.15f),
                        1.00f to Color.White.copy(alpha = 0.70f)
                    ),
                    shape = CircleShape
                )
        )
    }
}

/**
 * The mode names laid out as an endless carousel around the centre: [scroll] is the index that sits in the middle
 * and every label is placed at its shortest distance round the loop. Labels fade and shrink the further they are
 * from the middle. All of it is applied in the draw phase, so sliding the carousel does not recompose anything.
 */
@Composable
private fun ModeCarouselLabels(
    modes: List<CameraMode>,
    scroll: () -> Float,
    itemWidth: Dp,
    color: (CameraMode) -> Color,
    modifier: Modifier = Modifier
) {
    val n = modes.size
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        modes.forEachIndexed { index, mode ->
            Text(
                text = mode.title,
                color = color(mode),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val x = index - scroll()
                    val d = x - n * floor(x / n + 0.5f)          // shortest signed distance round the loop
                    val dist = abs(d)
                    translationX = d * itemWidth.toPx()
                    alpha = (1f - ((dist - 0.9f).coerceAtLeast(0f) / 1.1f)).coerceIn(0f, 1f)
                    val s = 1f - 0.14f * dist.coerceAtMost(1.6f)
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}

@Composable
fun FloatingObjectDetectionPill(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DetectionPillFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatOffset"
    )
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkleScale"
    )

    LiquidGlassContainer(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .offset { IntOffset(0, floatOffset.dp.roundToPx()) }
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFFDE047),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = sparkleScale
                        scaleY = sparkleScale
                    }
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun ExactShutterButton(
    isVideoMode: Boolean,
    isSearchMode: Boolean = false,
    isPanoramaMode: Boolean = false,
    isPanoramaCapturing: Boolean = false,
    isRecording: Boolean,
    isRecordingLocked: Boolean,
    isBursting: Boolean,
    burstCount: Int,
    isTimerRunning: Boolean,
    uiColor: Color,
    onClick: () -> Unit,
    onLongPressStart: () -> Unit,
    onSwipeUpLock: () -> Unit,
    onSwipeHorizontalBurst: () -> Unit,
    onRelease: () -> Unit
) {
    var isPressedState by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val recordRuby = Color(0xFFE50914)
    val recordBright = Color(0xFFFF2A42)
    val recordDark = Color(0xFF8A000F)
    val isVideoActive = isRecording || isRecordingLocked || (isVideoMode && !isTimerRunning)
    val recordRed = recordRuby
    val searchBlue = Color(0xFF3B82F6)

    val pulseTransition = rememberInfiniteTransition(label = "ShutterPulse")
    val pulseRingScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseRingScale"
    )
    val pulseRingAlpha by pulseTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseRingAlpha"
    )

    val animatedDragOffsetX by animateFloatAsState(
        targetValue = if (isBursting) dragOffsetX else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "ShutterDragOffsetX"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressedState || isBursting) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "ShutterScale"
    )

    val isSquare = (isVideoMode && isRecording) || isRecordingLocked || isTimerRunning || isPanoramaCapturing

    // Photo and Panorama: a plain, solid white rim. The soft coloured glow is only for Search and Video.
    val showRimGlow = isSearchMode || isVideoActive
    val outerRingColor by animateColorAsState(
        targetValue = if (isRecording || isRecordingLocked || (isVideoMode && !isTimerRunning)) recordRed
        else if (isBursting) uiColor
        else if (isSearchMode) Color.White.copy(alpha = 0.85f)
        else Color.White,
        animationSpec = tween(300),
        label = "OuterRingColor"
    )

    val searchOuterSweepTransition = rememberInfiniteTransition(label = "SearchOuterRingSweep")
    val searchOuterRingAngle by searchOuterSweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SearchOuterRingAngle"
    )

    Box(
        modifier = Modifier.wrapContentSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isBursting,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.75f, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.75f),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LiquidGlassContainer(
                shape = CircleShape,
                modifier = Modifier.offset(y = (-58).dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(uiColor)
                    )
                    Text(
                        text = "BURST  $burstCount",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }

        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .graphicsLayer {
                        scaleX = pulseRingScale
                        scaleY = pulseRingScale
                        alpha = pulseRingAlpha
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(recordBright.copy(alpha = 0.45f), recordRuby.copy(alpha = 0.15f), Color.Transparent)
                        ),
                        CircleShape
                    )
                    .border(2.dp, Brush.linearGradient(listOf(recordBright, recordDark)), CircleShape)
            )
        }

        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            // Very light blurred glow (red, yellow, white) behind the rim: Search and Video only
            if (showRimGlow) Canvas(
                modifier = Modifier
                    .requiredSize(124.dp)
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= 31) {
                            renderEffect = BlurEffect(12f, 12f, TileMode.Decal)
                        }
                    }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val colors = if (isVideoActive) ShutterGlowColorsVideo else ShutterGlowColors
                val shader = android.graphics.SweepGradient(cx, cy, colors, null)
                val m = android.graphics.Matrix()
                m.setRotate(searchOuterRingAngle, cx, cy)
                shader.setLocalMatrix(m)
                val brush = androidx.compose.ui.graphics.ShaderBrush(shader)
                val radius = 42.dp.toPx()
                // very light in Photo / Video, clearly stronger and a little wider in Search
                val k = if (isSearchMode) 1.35f else 1f
                val g = if (isSearchMode) 2.6f else 1f
                val widths = floatArrayOf(18f, 10f, 4f)
                val alphas = floatArrayOf(0.10f, 0.16f, 0.24f)
                for (i in widths.indices) {
                    drawCircle(
                        brush = brush,
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = (widths[i] * k).dp.toPx()),
                        alpha = (alphas[i] * g).coerceAtMost(0.85f)
                    )
                }
            }
            // Outer ring: frosted glass. It blurs the live camera preview behind it (the same blurred snapshot the
            // other glass controls use) and has one hairline edge light on the outside. The white inner circle
            // sits on top of it. While recording / in video mode the glass is tinted with the ring colour.
            val ringTint = if (isVideoActive || isBursting) outerRingColor else Color.White
            LiquidGlassContainer(
                shape = CircleShape,
                showBorder = false,
                edgeLighting = false,
                modifier = Modifier.fillMaxSize()
            ) {
                // light frost so the ring reads as bright, blurred glass (not a dark tint)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(ringTint.copy(alpha = if (isVideoActive || isBursting) 0.28f else 0.20f))
                )
                // hairline edge line on the outer edge
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 0.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    ringTint.copy(alpha = 0.95f),
                                    ringTint.copy(alpha = 0.40f),
                                    ringTint.copy(alpha = 0.40f),
                                    ringTint.copy(alpha = 0.85f)
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .pointerInput(isVideoMode, isRecording, isRecordingLocked, isTimerRunning) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressedState = true
                            var isBurstTriggered = false
                            var isLongPressTriggered = false
                            val startTime = System.currentTimeMillis()
                            val startX = down.position.x
                            val startY = down.position.y
                            var currentDiffX = 0f
                            var currentDiffY = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    break
                                }

                                val elapsed = System.currentTimeMillis() - startTime
                                val diffX = change.position.x - startX
                                val diffY = change.position.y - startY
                                currentDiffX = diffX
                                currentDiffY = diffY

                                dragOffsetX = (diffX / 2.2f).coerceIn(-65f, 65f)

                                if (!isVideoMode && !isRecording && abs(diffX) > 40f) {
                                    if (!isBurstTriggered) {
                                        isBurstTriggered = true
                                        onSwipeHorizontalBurst()
                                    }
                                    change.consume()
                                } else if (diffY < -80f && (isLongPressTriggered || isRecording)) {
                                    onSwipeUpLock()
                                    change.consume()
                                } else if (!isBurstTriggered && elapsed > 450 && abs(diffX) < 30f && abs(diffY) < 30f) {
                                    if (!isLongPressTriggered) {
                                        isLongPressTriggered = true
                                        onLongPressStart()
                                    }
                                }
                            }

                            isPressedState = false
                            val totalElapsed = System.currentTimeMillis() - startTime
                            dragOffsetX = 0f
                            onRelease()

                            if (!isBurstTriggered && !isLongPressTriggered && totalElapsed < 450 && abs(currentDiffX) < 35f && abs(currentDiffY) < 35f) {
                                onClick()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val innerSize by animateFloatAsState(
                    targetValue = if (isSquare) 38f else 76f,
                    animationSpec = spring(dampingRatio = 0.6f),
                    label = "InnerShutterSize"
                )
                val innerRoundness by animateIntAsState(
                    targetValue = if (isSquare) 12 else 50,
                    animationSpec = spring(dampingRatio = 0.6f),
                    label = "InnerShutterRoundness"
                )
                val innerBrush = if (isVideoActive) {
                    Brush.verticalGradient(listOf(recordBright, recordRuby, recordDark))
                } else if (isBursting) {
                    Brush.verticalGradient(listOf(uiColor, uiColor))
                } else {
                    Brush.verticalGradient(listOf(Color.White, Color.White))
                }

                Box(
                    modifier = Modifier
                        .offset(x = animatedDragOffsetX.dp)
                        .size(innerSize.dp)
                        .clip(RoundedCornerShape(percent = innerRoundness))
                        .background(innerBrush)
                        .border(
                            1.dp,
                            if (isVideoActive) Color(0xFFFF8595).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(percent = innerRoundness)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSearchMode) {
                        // res/drawable/search.png (on the plain white button)
                        Image(
                            painter = painterResource(id = com.example.R.drawable.search),
                            contentDescription = "Search",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    if (isRecordingLocked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (isBursting) {
                        Text(
                            text = "$burstCount",
                            color = Color.Black,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

/**
 * Swallows every touch on this element. Used while a control is slid out of sight (recording or a panorama sweep)
 * so it cannot be hit by accident where it is still laid out.
 */
fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
fun Modifier.bouncyClickable(onClick: () -> Unit): Modifier {
    val bouncyEnabled = LocalBouncyEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (bouncyEnabled && isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 500f),
        label = "BouncyClickableScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun Modifier.glassEffect(shape: androidx.compose.ui.graphics.Shape = CircleShape): Modifier {
    return this.background(Color.Black.copy(alpha = 0.5f), shape)
}
