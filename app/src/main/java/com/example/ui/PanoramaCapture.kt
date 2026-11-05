package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.graphics.Matrix
import android.graphics.RectF as AndroidRectF
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

// ---------------------------------------------------------------------------------------------------------
// Real-time panorama, sideways or up / down.
//
// How it works: while the user sweeps the phone, the fused rotation sensor gives the exact heading. Every ~2.3
// degrees of turning, one frame of the live preview is grabbed and a narrow band from it is pasted onto a wide
// canvas at the position that heading implies (a cylindrical projection, so the bands line up without any image
// matching). Bands overlap a little and are feathered.
//
// Up / down sweeps reuse exactly the same stitcher: every frame is rotated a quarter turn, the tilt of the phone
// plays the role of the sideways heading, and the finished picture is rotated back before it is saved.
//
// Smoothness: the main thread only grabs the preview frame. Scaling, rotating and stitching run on a background
// worker (frames queue up, the oldest is dropped if the worker is busy), and the live strip on screen reads a
// small bitmap that is updated band by band, so nothing is allocated per frame and the strip never stretches.
// ---------------------------------------------------------------------------------------------------------

private const val PANO_WORK_WIDTH = 720                 // frames are scaled to this width before stitching
private const val PANO_MAX_CANVAS_WIDTH = 6000          // keeps the canvas under ~55 MB
private const val PANO_MAX_SWEEP_RAD = 3.1415927f       // exactly 180 degrees: the longest sideways sweep
private const val PANO_MAX_SWEEP_VERT_RAD = 1.75f       // about 100 degrees: the longest up / down sweep
private const val PANO_STEP_RAD = 0.04f                 // grab a new band every ~2.3 degrees (at most)
private const val PANO_MAX_GAP_RAD = 0.2f           // ~11 degrees: never leave a bigger hole between two grabs, whatever the pacing says
private const val PANO_MAX_REST_MS = 120L           // the main thread is never rested longer than this between grabs
private const val PANO_MIN_GRAB_GAP_MS = 45L        // never grab preview frames closer together than this
private const val PANO_GRAB_REST_FACTOR = 2L        // after a grab that took X ms, leave the main thread alone for 2 x X ms
private const val PANO_MIN_SWEEP_RAD = 0.14f            // anything shorter than ~8 degrees is discarded
private const val PANO_WRONG_WAY_RAD = 0.12f            // ~7 degrees the wrong way shows the "other way" hint
private const val PANO_START_HINT_RAD = 0.05f           // the "start panning" hint stays until ~3 degrees are covered
private const val PANO_SLOW_DEG_PER_SEC = 50f
private const val PANO_LEVEL_LIMIT_RAD = 0.14f          // ~8 degrees of wander off the line
private const val PANO_LIVE_H = 160f                    // height of the small live bitmap behind the guide strip
private const val PANO_STAB_TOP = 0.4f                  // stabilizer: how fast the stitched bands follow hand shake up / down (lower = steadier)
private const val PANO_STAB_ARROW = 0.18f               // stabilizer: how fast the guide arrow follows hand shake (lower = steadier)
private const val PANO_STAB_PROGRESS = 0.3f             // stabilizer: same for the arrow's travel along the line
private const val PI_F = 3.1415927f
private const val TWO_PI_F = 6.2831855f

/** Heading of the rear camera from the fused rotation sensor: yaw (turning right is positive) and pitch (up is positive). */
class PanoramaTracker(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isAvailable: Boolean get() = sensor != null

    @Volatile var yaw = 0f                 // radians since start()
    @Volatile var pitch = 0f               // radians since start()
    @Volatile var speedDegPerSec = 0f           // how fast the phone turns sideways
    @Volatile var pitchSpeedDegPerSec = 0f      // how fast the phone tilts up or down
    @Volatile var hasBaseline = false

    private var sensorThread: HandlerThread? = null
    private val rot = FloatArray(9)
    private var lastRawYaw = 0f
    private var accYaw = 0f
    private var basePitch = 0f
    private var lastNs = 0L
    private var lastPitchValue = 0f

    fun start() {
        reset()
        val s = sensor ?: return
        stop()
        // Sensor events come in on their own thread (100 Hz) so a busy main thread never makes the heading stale.
        val t = HandlerThread("PanoSensor").also { it.start() }
        sensorThread = t
        sm?.registerListener(this, s, 10_000, Handler(t.looper))
    }

    fun stop() {
        sm?.unregisterListener(this)
        sensorThread?.quitSafely()
        sensorThread = null
    }

    fun reset() {
        hasBaseline = false
        yaw = 0f
        pitch = 0f
        speedDegPerSec = 0f
        pitchSpeedDegPerSec = 0f
        accYaw = 0f
        lastNs = 0L
        lastPitchValue = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            SensorManager.getRotationMatrixFromVector(rot, event.values)
        } catch (e: Exception) {
            return
        }
        // The rear camera looks along the device -Z axis. Column 2 of the matrix is the device Z axis in world
        // coordinates (x east, y north, z up), so the look direction is minus that column.
        val fx = -rot[2]
        val fy = -rot[5]
        val fz = -rot[8]
        val rawYaw = atan2(fx, fy)
        val p = asin(fz.coerceIn(-1f, 1f))
        if (!hasBaseline) {
            lastRawYaw = rawYaw
            accYaw = 0f
            basePitch = p
            lastNs = event.timestamp
            yaw = 0f
            pitch = 0f
            hasBaseline = true
            return
        }
        // Looking almost straight up or down makes the heading meaningless, so hold the last yaw there.
        if (abs(p) < 1.0f) {
            var d = rawYaw - lastRawYaw
            if (d > PI_F) d -= TWO_PI_F else if (d < -PI_F) d += TWO_PI_F
            accYaw += d
            lastRawYaw = rawYaw
            val dt = (event.timestamp - lastNs) / 1_000_000_000f
            if (dt > 0f) {
                val inst = abs(d) / dt * (180f / PI_F)
                speedDegPerSec = speedDegPerSec * 0.8f + inst * 0.2f
            }
        }
        val pitchNow = p - basePitch
        val dtAll = (event.timestamp - lastNs) / 1_000_000_000f
        if (dtAll > 0f) {
            val inst = abs(pitchNow - lastPitchValue) / dtAll * (180f / PI_F)
            pitchSpeedDegPerSec = pitchSpeedDegPerSec * 0.8f + inst * 0.2f
        }
        lastPitchValue = pitchNow
        lastNs = event.timestamp
        yaw = accYaw
        pitch = pitchNow
    }

    // SensorEventListener declares two abstract methods; the tracker only needs onSensorChanged.
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/** Horizontal and vertical field of view of the rear camera held in portrait, from the characteristics (60 / 78 degrees if unknown). */
private fun estimatePortraitFovsRad(context: Context): Pair<Float, Float> {
    val fallback = Pair(1.047f, 1.35f)
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return fallback
        val ch = cm.getCameraCharacteristics(id)
        val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return fallback
        val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return fallback
        val shortSide = min(size.width, size.height)       // the short sensor side is the horizontal one in portrait
        val longSide = max(size.width, size.height)
        val h = (2.0 * atan((shortSide / (2.0 * focal)))).toFloat().coerceIn(0.5f, 2.0f)
        val v = (2.0 * atan((longSide / (2.0 * focal)))).toFloat().coerceIn(0.6f, 2.4f)
        Pair(h, v)
    } catch (e: Exception) {
        fallback
    }
}

/** Scales a grabbed preview frame to the working size; up / down sweeps also turn it a quarter turn clockwise. */
private fun prepareFrame(raw: Bitmap, vertical: Boolean, w: Int, h: Int): Bitmap {
    if (!vertical) {
        if (raw.width == w && raw.height == h) return raw
        val out = Bitmap.createScaledBitmap(raw, w, h, true)
        if (out !== raw) raw.recycle()
        return out
    }
    // the frame ends up w x h after the turn, so before it the frame is h wide and w tall
    val scaled = if (raw.width == h && raw.height == w) raw else Bitmap.createScaledBitmap(raw, h, w, true)
    if (scaled !== raw) raw.recycle()
    val m = Matrix().apply { postRotate(90f) }
    val turned = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, m, true)
    if (turned !== scaled) scaled.recycle()
    return turned
}

/** Turns the finished up / down panorama back upright (a quarter turn counter-clockwise). */
private fun rotateBack(src: Bitmap): Bitmap {
    val m = Matrix().apply { postRotate(-90f) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** The wide canvas the bands are pasted onto, plus a small live copy for the guide strip. */
class PanoramaSession(
    val workW: Int,
    val workH: Int,
    private val fPx: Float,
    val direction: Int,            // +1 sweeping right (or up), -1 sweeping left (or down)
    maxSweepRad: Float
) {
    val drift = (workH * 0.20f).roundToInt()
    private val canvasH = workH + 2 * drift
    val canvasW: Int = (fPx * maxSweepRad + workW).roundToInt().coerceIn(workW * 2, PANO_MAX_CANVAS_WIDTH)
    private val bmp: Bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
    private val cv = AndroidCanvas(bmp)
    private val paint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG)
    private val feather = (workW * 0.03f).roundToInt().coerceIn(8, 28)
    private val lead = feather + 20
    private var edge = 0f                  // canvas x where the finished content ends, in the sweep direction
    private var started = false
    private var topSmooth = Float.NaN         // low-pass filtered vertical position of the bands (hand-shake stabilizer)
    private var minTop = Int.MAX_VALUE
    private var maxTop = Int.MIN_VALUE
    private val srcRect = AndroidRect()
    private val dstRect = AndroidRect()

    // Small live copy, updated band by band (no allocation per frame)
    val liveScale: Float = PANO_LIVE_H / workH.toFloat()
    private val liveW = max(2, (canvasW * liveScale).roundToInt())
    private val liveH = max(2, (canvasH * liveScale).roundToInt())
    val liveBitmap: Bitmap = Bitmap.createBitmap(liveW, liveH, Bitmap.Config.ARGB_8888)
    val liveImage: ImageBitmap = liveBitmap.asImageBitmap()
    private val liveCv = AndroidCanvas(liveBitmap)
    private val liveDst = AndroidRectF()
    @Volatile var liveX0 = 0               // used columns of the live bitmap
    @Volatile var liveX1 = 0
    @Volatile var isReleased = false; private set

    /** The largest sweep (radians) this canvas can hold. */
    val maxProgressRad: Float get() = (canvasW - workW) / fPx

    private fun centerX(u: Float): Float =
        if (direction > 0) workW / 2f + fPx * u else canvasW - workW / 2f - fPx * u

    private fun blit(frame: Bitmap, frameLeft: Int, top: Int, x0: Int, x1: Int, alpha: Int) {
        if (x1 <= x0) return
        srcRect.set(x0 - frameLeft, 0, x1 - frameLeft, workH)
        dstRect.set(x0, top, x1, top + workH)
        paint.alpha = alpha
        cv.drawBitmap(frame, srcRect, dstRect, paint)
        liveDst.set(x0 * liveScale, top * liveScale, x1 * liveScale, (top + workH) * liveScale)
        liveCv.drawBitmap(frame, srcRect, liveDst, paint)
    }

    /** [u] is how far the sweep has gone (radians, always positive), [pitchRad] the wander across the sweep line. */
    fun addFrame(frame: Bitmap, u: Float, pitchRad: Float, finalFrame: Boolean) {
        if (isReleased) return
        val cx = centerX(u)
        val frameLeft = (cx - workW / 2f).roundToInt()
        val frameRight = frameLeft + workW
        // Stabilizer: hand tremor makes the up / down position jump from band to band (visible stair-steps), so the
        // position is smoothed; the result drifts gently instead of jittering.
        val topRaw = drift - fPx * tan(pitchRad.toDouble()).toFloat()
        topSmooth = if (topSmooth.isNaN()) topRaw else topSmooth + (topRaw - topSmooth) * PANO_STAB_TOP
        val top = topSmooth.roundToInt().coerceIn(0, canvasH - workH)
        minTop = min(minTop, top)
        maxTop = max(maxTop, top)

        var aL: Float
        var aR: Float
        val feathered = started
        if (!started) {
            // first frame: everything behind the start direction, plus a little ahead
            if (direction > 0) { aL = frameLeft.toFloat(); aR = cx + lead } else { aL = cx - lead; aR = frameRight.toFloat() }
            started = true
        } else if (direction > 0) {
            aL = max(edge - feather, frameLeft.toFloat())
            aR = if (finalFrame) frameRight.toFloat() else cx + lead
        } else {
            aL = if (finalFrame) frameLeft.toFloat() else cx - lead
            aR = min(edge + feather, frameRight.toFloat())
        }
        val l = aL.roundToInt().coerceIn(frameLeft, frameRight)
        val r = aR.roundToInt().coerceIn(frameLeft, frameRight)
        if (r - l < 1) return

        val step = 2
        val n = if (feathered) (min(feather, r - l) / step) else 0
        for (i in 0 until n) {
            val a = ((i + 1) * 255f / (n + 1)).toInt()
            if (direction > 0) {
                val x0 = l + i * step
                blit(frame, frameLeft, top, x0, x0 + step, a)
            } else {
                val x1 = r - i * step
                blit(frame, frameLeft, top, x1 - step, x1, a)
            }
        }
        if (direction > 0) blit(frame, frameLeft, top, l + n * step, r, 255)
        else blit(frame, frameLeft, top, l, r - n * step, 255)
        edge = if (direction > 0) r.toFloat() else l.toFloat()

        liveX0 = (usedX0() * liveScale).roundToInt().coerceIn(0, liveW - 1)
        liveX1 = (usedX1() * liveScale).roundToInt().coerceIn(liveX0 + 1, liveW)
    }

    private fun usedX0(): Int = if (direction > 0) 0 else edge.roundToInt().coerceAtLeast(0)
    private fun usedX1(): Int = if (direction > 0) edge.roundToInt().coerceAtMost(canvasW) else canvasW
    private fun usedY0(): Int = if (maxTop > minTop + workH / 2) drift else maxTop
    private fun usedY1(): Int = if (maxTop > minTop + workH / 2) drift + workH else min(minTop + workH, canvasH)

    /** The final panorama, cropped to the area every band covers. */
    fun result(): Bitmap? {
        if (!started || isReleased) return null
        val x0 = usedX0()
        val x1 = usedX1()
        val y0 = usedY0()
        val y1 = usedY1()
        val w = x1 - x0
        val h = y1 - y0
        if (w < workW || h < workH / 2) return null
        return try {
            Bitmap.createBitmap(bmp, x0, y0, w, h)
        } catch (e: Throwable) {
            null
        }
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        try { bmp.recycle() } catch (_: Throwable) {}
        try { liveBitmap.recycle() } catch (_: Throwable) {}
    }
}

enum class PanoHint(val text: String) {
    READY("Tap the shutter, then pan slowly"),
    START("Start panning"),
    WRONG_WAY("Pan the other way"),
    KEEP_GOING("Keep panning"),
    SLOW("Move slower"),
    LEVEL("Keep the phone level"),
    FINISH("Tap the shutter to finish"),
    SAVING("Saving panorama"),
    SHORT("Pan a little further"),
    NO_SENSOR("Panorama needs a motion sensor"),
    SAVE_FAILED("Couldn't save the panorama");

    /** The text for the current sweep ([vertical] = up / down, [direction] +1 right or up, -1 left or down). */
    fun label(vertical: Boolean, direction: Int): String = when (this) {
        READY -> if (vertical) "Tap the shutter, then tilt slowly" else text
        START, WRONG_WAY -> if (vertical) {
            if (direction >= 0) "Tilt the phone up" else "Tilt the phone down"
        } else {
            if (direction >= 0) "Pan to the right" else "Pan to the left"
        }
        KEEP_GOING -> if (vertical) "Keep tilting" else text
        LEVEL -> if (vertical) "Keep the phone straight" else text
        else -> text
    }

    /** True for hints that ask the user to correct something (shown in amber). */
    val isWarning: Boolean
        get() = this == WRONG_WAY || this == SLOW || this == LEVEL || this == SHORT || this == NO_SENSOR || this == SAVE_FAILED
}

private class PanoFrame(val raw: Bitmap, val u: Float, val pitchEq: Float, val final: Boolean)

/** Runs one panorama capture at a time and exposes what the screen needs to show. UI state is touched on the main thread. */
class PanoramaController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val grabFrame: () -> Bitmap?,
    private val onSaved: (Uri?) -> Unit
) {
    var capturing by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var hint by mutableStateOf(PanoHint.READY)
    var progress by mutableFloatStateOf(0f)           // 0..1 of the maximum sweep
    var sweepDeg by mutableFloatStateOf(0f)           // degrees covered so far
    var maxSweepDeg by mutableFloatStateOf(180f)      // degrees one panorama can cover at the current zoom
    var vertical by mutableStateOf(false); private set // false = sideways, true = up / down (chosen before starting)
    var direction by mutableIntStateOf(1); private set // +1 right / up, -1 left / down (chosen before starting)
    var drift by mutableFloatStateOf(0f)              // -1..1, wander across the sweep line for the guide marker

    // live strip: a small bitmap that grows band by band; liveTick changes whenever a band was added
    var liveImage by mutableStateOf<ImageBitmap?>(null); private set
    var liveTick by mutableIntStateOf(0)
    var liveSession: PanoramaSession? = null; private set

    private val tracker = PanoramaTracker(context)
    private var job: Job? = null
    @Volatile private var stopRequested = false
    @Volatile private var lastGrabMs = 0L
    private var session: PanoramaSession? = null

    /** Cycles right -> left -> up -> down. Only possible between captures. */
    fun cycleDirection() {
        if (capturing || saving) return
        when {
            !vertical && direction > 0 -> direction = -1
            !vertical -> { vertical = true; direction = 1 }
            direction > 0 -> direction = -1
            else -> { vertical = false; direction = 1 }
        }
        if (hint != PanoHint.NO_SENSOR) hint = PanoHint.READY
    }

    fun toggleDirection() = cycleDirection()

    fun toggle(zoom: Float) {
        if (capturing) stop() else start(zoom)
    }

    fun start(zoom: Float) {
        if (capturing || saving) return
        if (!tracker.isAvailable) {
            hint = PanoHint.NO_SENSOR
            return
        }
        job?.cancel()
        stopRequested = false
        progress = 0f
        sweepDeg = 0f
        drift = 0f
        liveImage = null
        hint = PanoHint.START
        capturing = true
        job = scope.launch(Dispatchers.Default) { runCapture(zoom) }
    }

    /** Ends the sweep and saves what was captured so far. */
    fun stop() {
        if (capturing) stopRequested = true
    }

    /** Throws the capture away (mode change, leaving the screen, app in the background). */
    fun cancel() {
        job?.cancel()
        tracker.stop()
        liveImage = null          // stop drawing the live bitmap; the capture job releases the session when it ends
        capturing = false
        saving = false
        progress = 0f
        sweepDeg = 0f
        drift = 0f
        hint = PanoHint.READY
    }

    private fun releaseSession(s: PanoramaSession) {
        if (liveSession === s) {
            liveImage = null
            liveSession = null
        }
        if (session === s) session = null
        s.release()
    }

    /** The preview can only be read on the main thread, so this hops there, grabs once and times how long it took. */
    private suspend fun grabOnMain(): Bitmap? = withContext(Dispatchers.Main) {
        val t0 = SystemClock.uptimeMillis()
        val b = try { grabFrame() } catch (e: Exception) { null }
        lastGrabMs = SystemClock.uptimeMillis() - t0
        b
    }

    private suspend fun runCapture(zoom: Float) {
        tracker.start()
        var lastU = 0f
        var created: PanoramaSession? = null
        var channel: Channel<PanoFrame>? = null
        var worker: Job? = null
        try {
            var waited = 0
            while (!tracker.hasBaseline && waited < 40) {
                delay(25)
                waited++
            }
            if (!tracker.hasBaseline) {
                finishWithHint(PanoHint.NO_SENSOR)
                return
            }
            // The first frame is grabbed straight away, before the phone has moved.
            val raw = grabOnMain()
            if (raw == null) {
                finishWithHint(PanoHint.READY)
                return
            }
            val vert = vertical
            val dir = if (direction >= 0) 1 else -1
            val viewAspect = raw.width.toFloat() / raw.height.toFloat()
            // size of a working frame after the (optional) quarter turn
            val workW: Int
            val workH: Int
            if (!vert) {
                workW = min(PANO_WORK_WIDTH, raw.width)
                workH = (raw.height.toFloat() * workW / raw.width).roundToInt().coerceAtLeast(1)
            } else {
                workW = min(PANO_WORK_WIDTH, raw.height)
                workH = (raw.width.toFloat() * workW / raw.height).roundToInt().coerceAtLeast(1)
            }
            val frame0 = withContext(Dispatchers.Default) { prepareFrame(raw, vert, workW, workH) }

            // PreviewView crops the 3:4 sensor image sideways (tall views) or vertically (wide views)
            val fovs = estimatePortraitFovsRad(context)
            val zoomSafe = zoom.coerceAtLeast(0.5f)
            val half = if (vert) {
                atan(tan(fovs.second / 2.0) * min(1f, 0.75f / viewAspect) / zoomSafe).toFloat()
            } else {
                atan(tan(fovs.first / 2.0) * min(1f, viewAspect / 0.75f) / zoomSafe).toFloat()
            }
            val fPx = (frame0.width / 2f) / tan(half.toDouble()).toFloat()

            val sess = try {
                withContext(Dispatchers.Default) {
                    PanoramaSession(
                        frame0.width, frame0.height, fPx, dir,
                        if (vert) PANO_MAX_SWEEP_VERT_RAD else PANO_MAX_SWEEP_RAD
                    ).also { it.addFrame(frame0, 0f, 0f, false) }
                }
            } catch (e: OutOfMemoryError) {          // very low-end phones cannot hold the wide canvas
                finishWithHint(PanoHint.SAVE_FAILED)
                return
            }
            created = sess
            session = sess
            liveSession = sess
            liveImage = sess.liveImage
            liveTick++
            maxSweepDeg = sess.maxProgressRad * 180f / PI_F

            // Background worker: scales, turns and stitches while the main thread keeps grabbing frames.
            // Every frame matters (a lost one leaves a black hole in the picture), so frames are never dropped:
            // the queue holds a few and the grab loop only counts a frame as taken when it was really queued.
            val ch = Channel<PanoFrame>(
                capacity = 6,
                onUndeliveredElement = { try { it.raw.recycle() } catch (_: Throwable) {} }
            )
            channel = ch
            val fw = frame0.width
            val fh = frame0.height
            val workerJob = scope.launch(Dispatchers.Default) {
                for (f in ch) {
                    try {
                        val prepared = prepareFrame(f.raw, vert, fw, fh)
                        sess.addFrame(prepared, f.u, f.pitchEq, f.final)
                        prepared.recycle()
                    } catch (t: Throwable) {
                        // drop this band; the next one overlaps it anyway
                    }
                    withContext(Dispatchers.Main) { liveTick++ }
                }
            }
            worker = workerJob

            var lastHintMs = 0L
            var nextGrabAt = 0L
            while (currentCoroutineContext().isActive) {
                val axis = if (vert) tracker.pitch else tracker.yaw          // the way the phone is sweeping
                val levelDev = if (vert) tracker.yaw else tracker.pitch      // wander across the sweep line
                val signed = dir * axis                       // positive = moving the chosen way
                val u = max(0f, signed)
                val progressTarget = (u / sess.maxProgressRad).coerceIn(0f, 1f)
                progress += (progressTarget - progress) * PANO_STAB_PROGRESS
                sweepDeg = (u * 180f / PI_F).coerceAtMost(maxSweepDeg)
                val driftTarget = ((if (vert) levelDev else -levelDev) / PANO_LEVEL_LIMIT_RAD).coerceIn(-1f, 1f)
                drift += (driftTarget - drift) * PANO_STAB_ARROW

                val now = System.currentTimeMillis()
                if (now - lastHintMs > 200) {
                    lastHintMs = now
                    val speed = if (vert) tracker.pitchSpeedDegPerSec else tracker.speedDegPerSec
                    hint = when {
                        signed < -PANO_WRONG_WAY_RAD && lastU < PANO_STEP_RAD -> PanoHint.WRONG_WAY
                        speed > PANO_SLOW_DEG_PER_SEC -> PanoHint.SLOW
                        abs(levelDev) > PANO_LEVEL_LIMIT_RAD -> PanoHint.LEVEL
                        progress > 0.85f -> PanoHint.FINISH
                        u < PANO_START_HINT_RAD -> PanoHint.START
                        else -> PanoHint.KEEP_GOING
                    }
                }

                val atEnd = u >= sess.maxProgressRad - 0.02f
                if (stopRequested || atEnd) {
                    // one last frame so the whole trailing half of the view is used
                    val rawLast = grabOnMain()
                    val axisLast = if (vert) tracker.pitch else tracker.yaw
                    val poseU = max(0f, dir * axisLast).coerceAtMost(sess.maxProgressRad - 0.01f)
                    val posePitch = if (vert) -tracker.yaw else tracker.pitch
                    if (rawLast != null) ch.send(PanoFrame(rawLast, poseU, posePitch, true))
                    lastU = max(lastU, poseU)
                    break
                }

                val tNow = SystemClock.uptimeMillis()
                if (u - lastU >= PANO_STEP_RAD && (tNow >= nextGrabAt || u - lastU >= PANO_MAX_GAP_RAD)) {
                    val rawFrame = grabOnMain()
                    // rest the main thread after each grab so the live preview and the guide keep drawing at full speed
                    nextGrabAt = SystemClock.uptimeMillis() + min(PANO_MAX_REST_MS, max(PANO_MIN_GRAB_GAP_MS, lastGrabMs * PANO_GRAB_REST_FACTOR))
                    // read the pose right after the frame was grabbed so both belong together
                    val axisNow = if (vert) tracker.pitch else tracker.yaw
                    val poseU = max(0f, dir * axisNow)
                    val posePitch = if (vert) -tracker.yaw else tracker.pitch
                    if (rawFrame != null) {
                        if (ch.trySend(PanoFrame(rawFrame, poseU, posePitch, false)).isSuccess) {
                            lastU = poseU
                        } else {
                            try { rawFrame.recycle() } catch (_: Throwable) {}   // queue full: grab again on the next pass
                        }
                    }
                }
                delay(10)
            }

            // Let the worker finish the queued bands, then crop and save
            ch.close()
            workerJob.join()
            tracker.stop()
            capturing = false
            if (lastU < PANO_MIN_SWEEP_RAD) {
                releaseSession(sess)
                progress = 0f
                sweepDeg = 0f
                hint = PanoHint.SHORT
                return
            }
            saving = true
            hint = PanoHint.SAVING
            val uri = withContext(Dispatchers.IO) {
                val result = sess.result()
                if (result == null) null else {
                    val upright = if (vert) rotateBack(result) else result
                    if (upright !== result) result.recycle()
                    val saved = savePanoramaToGallery(context, upright)
                    upright.recycle()
                    saved
                }
            }
            releaseSession(sess)
            saving = false
            progress = 0f
            sweepDeg = 0f
            hint = if (uri != null) PanoHint.READY else PanoHint.SAVE_FAILED
            withContext(Dispatchers.Main) { onSaved(uri) }
        } finally {
            tracker.stop()
            withContext(NonCancellable) {
                channel?.cancel()
                worker?.cancelAndJoin()
                created?.let { releaseSession(it) }
            }
            if (capturing || saving) {
                capturing = false
                saving = false
            }
        }
    }

    private fun finishWithHint(h: PanoHint) {
        tracker.stop()
        capturing = false
        saving = false
        progress = 0f
        sweepDeg = 0f
        hint = h
    }
}

private fun savePanoramaToGallery(context: Context, bmp: Bitmap): Uri? {
    val now = System.currentTimeMillis()
    val name = "PANO_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_TAKEN, now)
        put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CameraApp")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = try {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (e: Exception) {
        null
    } ?: return null
    return try {
        val stream = resolver.openOutputStream(uri) ?: throw IllegalStateException("no output stream")
        stream.use { out ->
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw IllegalStateException("compress failed")
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        uri
    } catch (e: Exception) {
        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
        null
    }
}

@Composable
fun rememberPanoramaController(grabFrame: () -> Bitmap?, onSaved: (Uri?) -> Unit): PanoramaController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val grab = rememberUpdatedState(grabFrame)
    val saved = rememberUpdatedState(onSaved)
    val controller = remember { PanoramaController(context.applicationContext, scope, { grab.value() }, { saved.value(it) }) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        // A sweep cannot continue while the app is in the background (the sensor and preview stop), so drop it.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && controller.capturing) controller.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.cancel()
        }
    }
    return controller
}

private val PanoAmber = Color(0xFFFFC83D)

/** Arrow head used by the guide frame: points the way the sweep goes ([dir] +1 right, -1 left). */
private fun DrawScope.drawPanoArrow(center: Offset, dir: Int, arrowSize: Float, color: Color) {
    val d = if (dir >= 0) 1f else -1f
    drawCircle(Color.Black.copy(alpha = 0.22f), radius = arrowSize * 0.82f, center = center)
    val head = Path().apply {
        moveTo(center.x + d * arrowSize * 0.58f, center.y)
        lineTo(center.x - d * arrowSize * 0.42f, center.y - arrowSize * 0.52f)
        lineTo(center.x - d * arrowSize * 0.16f, center.y)
        lineTo(center.x - d * arrowSize * 0.42f, center.y + arrowSize * 0.52f)
        close()
    }
    drawPath(head, color)
}

/**
 * The guide shown over the viewfinder in Panorama mode: a rounded frame with a level line and an arrow. Before the
 * capture the arrow sits at the start edge (tap it to change direction). While sweeping, the arrow travels along
 * the line, the stitched strip grows behind it at a fixed scale (it never stretches or gets cut), and the arrow
 * rides to the side if the phone wanders, so keeping it on the line keeps the shot straight. Sideways sweeps use a
 * wide frame, up / down sweeps a tall one. Everything animated is read in the draw phase only.
 */
@Composable
private fun PanoramaGuide(
    controller: PanoramaController,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dir = controller.direction
    val vertical = controller.vertical
    val busy = controller.capturing || controller.saving
    val idleAlpha by animateFloatAsState(if (busy) 0f else 1f, tween(260), label = "PanoIdleAlpha")
    val chevronPhase = rememberInfiniteTransition(label = "PanoChevrons").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "PanoChevronPhase"
    )
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.then(
            if (vertical) Modifier.width(104.dp).height(230.dp) else Modifier.fillMaxWidth(0.9f).height(104.dp)
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = size.width
            val sh = size.height
            val len = if (vertical) sh else sw       // length along the sweep
            val cross = if (vertical) sw else sh
            // Up / down: draw the same sideways guide on a canvas turned a quarter turn, so "right" points up
            withTransform({
                if (vertical) {
                    translate(sw / 2f - len / 2f, sh / 2f - cross / 2f)
                    rotate(-90f, Offset(len / 2f, cross / 2f))
                }
            }) {
                drawPanoGuide(controller, len, cross, dir, busy, idleAlpha, chevronPhase.value)
            }
        }

        // Tapping the arrow (or the start end of the frame) changes the sweep direction.
        if (!busy) {
            Box(
                modifier = Modifier
                    .align(
                        if (vertical) (if (dir >= 0) Alignment.BottomCenter else Alignment.TopCenter)
                        else (if (dir >= 0) Alignment.CenterStart else Alignment.CenterEnd)
                    )
                    .then(if (vertical) Modifier.size(width = 104.dp, height = 76.dp) else Modifier.size(width = 76.dp, height = 104.dp))
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleDirection
                    )
            )
        }
    }
}

/** Draws the guide as a sideways one in a [w] x [h] area ("right" = the sweep direction +1). */
private fun DrawScope.drawPanoGuide(
    controller: PanoramaController,
    w: Float,
    h: Float,
    dir: Int,
    busy: Boolean,
    idleAlpha: Float,
    chevron: Float
) {
    val corner = 16.dp.toPx()
    val inset = 26.dp.toPx()
    val cy = h / 2f
    val frame = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, androidx.compose.ui.geometry.CornerRadius(corner)))
    }
    drawPath(frame, Color.Black.copy(alpha = 0.26f))

    val p = controller.progress.coerceIn(0f, 1f)
    val travel = w - 2f * inset
    val markerX = if (dir >= 0) inset + p * travel else w - inset - p * travel

    // the stitched strip so far: a small live bitmap drawn at a fixed scale, so it never stretches while it grows
    val s = controller.liveSession
    val img = controller.liveImage
    if (controller.liveTick >= 0 && img != null && s != null && busy && !s.isReleased && !s.liveBitmap.isRecycled) {
        val ls = s.liveScale
        val x0 = s.liveX0
        val x1 = s.liveX1
        if (x1 - x0 >= 2) {
            val sx = travel / ((s.canvasW - s.workW) * ls)               // guide pixels per live pixel along the sweep
            val dstLeft = if (dir >= 0) {
                inset + (x0 - (s.workW / 2f) * ls) * sx
            } else {
                (w - inset) - (((s.canvasW - s.workW / 2f) * ls) - x0) * sx
            }
            val dstW = (x1 - x0) * sx
            val srcY = (s.drift * ls).roundToInt().coerceIn(0, img.height - 1)
            val srcH = (s.workH * ls).roundToInt().coerceIn(1, img.height - srcY)
            if (dstW >= 1f) {
                clipPath(frame) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset(x0, srcY),
                        srcSize = IntSize(x1 - x0, srcH),
                        dstOffset = IntOffset(dstLeft.roundToInt(), 0),
                        dstSize = IntSize(dstW.roundToInt().coerceAtLeast(1), h.roundToInt()),
                        filterQuality = FilterQuality.Low
                    )
                }
            }
        }
    }

    // level line
    drawLine(
        color = Color.White.copy(alpha = 0.55f),
        start = Offset(inset * 0.5f, cy),
        end = Offset(w - inset * 0.5f, cy),
        strokeWidth = 1.2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // the arrow: it wanders with the phone; amber when it strays far from the line
    val dev = controller.drift
    val arrowY = cy + dev * (h * 0.34f)
    val strayed = abs(dev) > 0.7f
    val arrowColor = if (strayed) PanoAmber else Color.White
    val sz = 11.dp.toPx()

    // idle: soft chevrons running ahead of the arrow, showing where to pan
    if (idleAlpha > 0.01f) {
        val d = if (dir >= 0) 1f else -1f
        for (i in 1..3) {
            val t = ((chevron + (3 - i) / 3f) % 1f)
            val a = (1f - abs(t - 0.5f) * 2f).coerceIn(0f, 1f) * idleAlpha * 0.9f
            val cx = markerX + d * (sz * 1.5f + i * 15.dp.toPx())
            val sc = 6.dp.toPx()
            drawLine(Color.White.copy(alpha = a), Offset(cx - d * sc, arrowY - sc * 1.25f), Offset(cx, arrowY), 2.2.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White.copy(alpha = a), Offset(cx, arrowY), Offset(cx - d * sc, arrowY + sc * 1.25f), 2.2.dp.toPx(), StrokeCap.Round)
        }
    }
    drawPanoArrow(Offset(markerX, arrowY), dir, sz, arrowColor)

    // frame border on top of everything
    drawPath(frame, Color.White.copy(alpha = 0.92f), style = Stroke(width = 1.5.dp.toPx()))
}

/** Small glass button that cycles the sweep direction (right, left, up, down). Sits next to the shutter in Panorama mode. */
@Composable
fun PanoDirectionButton(
    vertical: Boolean,
    direction: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // arrow drawn pointing right; rotate it to point right, left, up or down
    val target = when {
        !vertical && direction >= 0 -> 0f
        !vertical -> 180f
        direction >= 0 -> 270f
        else -> 90f
    }
    var angle by remember { mutableFloatStateOf(target) }
    var last by remember { mutableFloatStateOf(target) }
    LaunchedEffect(target) {
        var d = (target - last) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        angle += d
        last = target
    }
    val rot = animateFloatAsState(
        targetValue = angle,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "PanoDirectionTurn"
    )
    LiquidGlassContainer(
        shape = CircleShape,
        modifier = modifier
            .size(54.dp)
            .then(if (enabled) Modifier.bouncyClickable(onClick) else Modifier)
    ) {
        Canvas(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { rotationZ = rot.value }
        ) {
            val w = size.width
            val h = size.height
            val sw = 2.6.dp.toPx()
            drawLine(Color.White, Offset(w * 0.08f, h / 2f), Offset(w * 0.90f, h / 2f), sw, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.90f, h / 2f), Offset(w * 0.60f, h * 0.20f), sw, StrokeCap.Round)
            drawLine(Color.White, Offset(w * 0.90f, h / 2f), Offset(w * 0.60f, h * 0.80f), sw, StrokeCap.Round)
        }
    }
}

/** Guide frame, direction arrow, live stitched strip and hints drawn over the viewfinder while Panorama mode is active. */
@Composable
fun PanoramaOverlay(
    controller: PanoramaController,
    modifier: Modifier = Modifier,
    onToggleDirection: () -> Unit = { controller.toggleDirection() }
) {
    val busy = controller.capturing || controller.saving
    val hint = controller.hint
    val dir = controller.direction
    val vertical = controller.vertical
    val sweepDegrees by remember(controller) { derivedStateOf { controller.sweepDeg.roundToInt() } }
    // half of the guide frame's height, so the labels sit just outside it
    val halfFrame = if (vertical) 115 else 52

    Box(modifier = modifier) {
        PanoramaGuide(
            controller = controller,
            onToggleDirection = onToggleDirection,
            modifier = Modifier.align(Alignment.Center)
        )

        // hint above the frame
        BlurGlassBar(
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-(halfFrame + 40)).dp)
        ) {
            AnimatedContent(
                targetState = hint,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                label = "PanoHintFade"
            ) { h ->
                Text(
                    text = h.label(vertical, dir),
                    color = if (h.isWarning) PanoAmber else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // below the frame: how far the sweep has gone, or how to change direction
        if (controller.capturing) {
            BlurGlassBar(
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (halfFrame + 32).dp)
            ) {
                Text(
                    text = "$sweepDegrees°",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        } else if (!busy) {
            Text(
                text = "Tap the arrow to change direction",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (halfFrame + 26).dp)
            )
        }
    }
}
