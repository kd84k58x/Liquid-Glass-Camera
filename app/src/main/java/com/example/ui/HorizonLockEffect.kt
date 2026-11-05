package com.example.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Horizon lock for video, the same idea as Samsung's: whatever angle the phone is held at, the horizon in the
 * picture stays level.
 *
 * How it really works (nothing here is a UI trick):
 *  - A CameraX [CameraEffect] sits between the camera and BOTH the viewfinder and the video encoder, so what you see
 *    is what gets recorded.
 *  - Every camera frame goes through an OpenGL shader that rotates the picture by the angle the phone is tilted by
 *    and crops in just enough that no empty corners show.
 *  - The tilt comes from the gyroscope fused with the accelerometer (game rotation vector, 200 Hz). The orientation
 *    is looked up at the moment the frame was exposed, not "now", so the picture does not lag behind fast moves.
 *
 * Only video (and its viewfinder) is touched. Photos are never processed by this effect.
 */
enum class HorizonLockMode(val label: String, val limitDeg: Float) {
    OFF("Off", 0f),

    /** Corrects up to 15 degrees of tilt each way. Smaller crop (about 1.45x on a 9:16 video). */
    LIGHT("15°", 15f),

    /** Keeps the horizon level at ANY angle, up to a full roll. Fixed crop (about 2.1x on a 9:16 video). */
    FULL("Full", 180f);

    companion object {
        fun fromName(name: String?): HorizonLockMode = values().firstOrNull { it.name == name } ?: OFF
    }
}

class HorizonLockEffect private constructor(
    private val processor: HorizonLockProcessor
) : CameraEffect(
    CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
    processor.executor,
    processor,
    Consumer<Throwable> { t -> Log.e(TAG, "Horizon lock failed", t) }
) {
    /** Off / 15° / Full. Safe to change at any time, it is read on every frame. */
    var mode: HorizonLockMode = HorizonLockMode.OFF
        set(value) {
            field = value
            processor.limitDeg = value.limitDeg
        }

    /** Tell the effect which camera is active, the front camera sees the world mirrored. */
    var isFrontCamera: Boolean
        get() = processor.isFront
        set(value) { processor.isFront = value }

    /** Surface.ROTATION_0 / 90 / 180 / 270 of the screen. */
    var displayRotation: Int
        get() = processor.displayRotation
        set(value) { processor.displayRotation = value }

    /** Frees the GL thread and the sensors. Call it once, when the camera screen is gone. */
    fun release() = processor.release()

    companion object {
        private const val TAG = "HorizonLock"

        fun create(context: Context): HorizonLockEffect =
            HorizonLockEffect(HorizonLockProcessor(context.applicationContext))
    }
}

// If a test on the phone shows the horizon tilting TWICE as much instead of being fixed, change this to -1f.
private const val ROLL_SIGN = 1f

internal class HorizonLockProcessor(private val appContext: Context) : SurfaceProcessor {

    private val tag = "HorizonLock"

    // One thread does everything: GL, the camera callbacks and the sensor callbacks.
    private val thread = HandlerThread("HorizonLockGL").apply { start() }
    private val handler = Handler(thread.looper)
    val executor = Executor { r -> handler.post(r) }

    @Volatile var limitDeg = 0f
    @Volatile var isFront = false
    @Volatile var displayRotation = Surface.ROTATION_0

    // ---------- GL state (GL thread only) ----------
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var aPos = 0
    private var aTex = 0
    private var uTexMatrix = 0
    private var uSize = 0
    private var uCos = 0
    private var uSin = 0
    private var uScale = 0
    private var uTex = 0

    private var inputTexId = 0
    private var inputTexture: SurfaceTexture? = null

    private class OutputSlot(val output: SurfaceOutput, val eglSurface: EGLSurface, val size: Size)
    private val outputs = ArrayList<OutputSlot>()

    private val texMatrix = FloatArray(16)
    private val adjMatrix = FloatArray(16)

    private val vertexBuf: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texBuf: FloatBuffer = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    // ---------- orientation ----------
    private val history = UpHistory()
    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null
    private var filteredDeg = Float.NaN
    private val upTmp = FloatArray(3)

    private var released = false

    // ===================================================================================================
    //  SurfaceProcessor
    // ===================================================================================================

    override fun onInputSurface(request: SurfaceRequest) {
        runOnGl {
            if (released) { request.willNotProvideSurface(); return@runOnGl }
            try {
                ensureGl()
                makeCurrent(pbuffer)

                val tex = IntArray(1)
                GLES20.glGenTextures(1, tex, 0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

                val st = SurfaceTexture(tex[0])
                st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                val surface = Surface(st)
                st.setOnFrameAvailableListener({ drawFrame(st) }, handler)

                inputTexId = tex[0]
                inputTexture = st
                filteredDeg = Float.NaN

                request.provideSurface(surface, executor) {
                    // The camera is done with this input (rebind, camera switch, screen closed): free it.
                    runOnGl {
                        if (inputTexture === st) { inputTexture = null; inputTexId = 0 }
                        try { st.setOnFrameAvailableListener(null) } catch (_: Exception) {}
                        try { surface.release() } catch (_: Exception) {}
                        try { st.release() } catch (_: Exception) {}
                        if (eglDisplay != EGL14.EGL_NO_DISPLAY && pbuffer != EGL14.EGL_NO_SURFACE) {
                            makeCurrent(pbuffer)
                            GLES20.glDeleteTextures(1, tex, 0)
                        }
                        if (inputTexture == null) stopSensors()
                    }
                }
                startSensors()
            } catch (t: Throwable) {
                Log.e(tag, "input surface failed", t)
                try { request.willNotProvideSurface() } catch (_: Exception) {}
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        runOnGl {
            if (released) { surfaceOutput.close(); return@runOnGl }
            try {
                ensureGl()
                val surface = surfaceOutput.getSurface(executor) { event ->
                    if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) detachOutput(surfaceOutput)
                }
                val eglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0
                )
                if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                    Log.e(tag, "eglCreateWindowSurface failed: ${EGL14.eglGetError()}")
                    surfaceOutput.close()
                    return@runOnGl
                }
                outputs.add(OutputSlot(surfaceOutput, eglSurface, surfaceOutput.size))
            } catch (t: Throwable) {
                Log.e(tag, "output surface failed", t)
                try { surfaceOutput.close() } catch (_: Exception) {}
            }
        }
    }

    private fun detachOutput(surfaceOutput: SurfaceOutput) {
        runOnGl {
            val slot = outputs.firstOrNull { it.output === surfaceOutput }
            if (slot != null) {
                outputs.remove(slot)
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    makeCurrent(pbuffer)
                    EGL14.eglDestroySurface(eglDisplay, slot.eglSurface)
                }
            }
            try { surfaceOutput.close() } catch (_: Exception) {}
        }
    }

    // ===================================================================================================
    //  One camera frame
    // ===================================================================================================

    private fun drawFrame(st: SurfaceTexture) {
        if (st !== inputTexture || released) return
        try {
            makeCurrent(pbuffer)
            st.updateTexImage()
        } catch (e: RuntimeException) {
            return // the texture was released while the frame was on its way
        }
        st.getTransformMatrix(texMatrix)
        val frameTs = st.timestamp
        if (outputs.isEmpty()) return

        val limit = limitDeg
        val angleDeg = if (limit > 0f) lockAngleDeg(frameTs, limit) else 0f

        // One zoom for every output, so the viewfinder and the video are cropped the same amount.
        var scale = 1f
        if (limit > 0f) for (o in outputs) scale = min(scale, fitScale(o.size.width, o.size.height, limit))

        for (o in outputs.toList()) renderTo(o, frameTs, angleDeg, scale)
        makeCurrent(pbuffer)
    }

    private fun renderTo(o: OutputSlot, frameTs: Long, angleDeg: Float, scale: Float) {
        if (!EGL14.eglMakeCurrent(eglDisplay, o.eglSurface, o.eglSurface, eglContext)) return
        val w = o.size.width
        val h = o.size.height
        GLES20.glViewport(0, 0, w, h)

        // CameraX hands back the matrix that makes this output upright, cropped and mirrored the way its target wants.
        o.output.updateTransformMatrix(adjMatrix, texMatrix)

        // A mirrored output (selfie viewfinder) sees the world flipped, so the roll has to turn the other way.
        // The front camera's raw picture is not mirrored, the selfie viewfinder is: compare the two matrices.
        val flipped = det2(adjMatrix) * det2(texMatrix) < 0f
        val sign = (if (isFront != flipped) -1f else 1f) * ROLL_SIGN
        val rad = Math.toRadians((sign * angleDeg).toDouble())

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTexId)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, adjMatrix, 0)
        GLES20.glUniform2f(uSize, w.toFloat(), h.toFloat())
        GLES20.glUniform1f(uCos, cos(rad).toFloat())
        GLES20.glUniform1f(uSin, sin(rad).toFloat())
        GLES20.glUniform1f(uScale, scale)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuf)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)

        // The encoder needs the real capture time of the frame, otherwise the video timing goes wrong.
        EGLExt.eglPresentationTimeANDROID(eglDisplay, o.eglSurface, frameTs)
        EGL14.eglSwapBuffers(eglDisplay, o.eglSurface)
    }

    /** 2x2 determinant of the top-left part of a column-major 4x4 matrix: negative means the picture is mirrored. */
    private fun det2(m: FloatArray): Float = m[0] * m[5] - m[4] * m[1]

    // ===================================================================================================
    //  The angle
    // ===================================================================================================

    /**
     * How far the phone is rolled clockwise from level, in degrees (what the picture has to be turned by),
     * measured when this frame was exposed.
     */
    private fun lockAngleDeg(frameTs: Long, limit: Float): Float {
        if (!history.sample(queryTime(frameTs), upTmp)) return if (filteredDeg.isNaN()) 0f else filteredDeg.coerceIn(-limit, limit)
        val ux = upTmp[0]
        val uy = upTmp[1]

        // "Up" as seen from the screen, whichever way the screen is turned.
        val dx: Float
        val dy: Float
        when (displayRotation) {
            Surface.ROTATION_90 -> { dx = -uy; dy = ux }
            Surface.ROTATION_180 -> { dx = -ux; dy = -uy }
            Surface.ROTATION_270 -> { dx = uy; dy = -ux }
            else -> { dx = ux; dy = uy }
        }
        val target = Math.toDegrees(atan2(-dx.toDouble(), dy.toDouble())).toFloat()

        // When the camera points straight up or down there is no horizon to follow: hold the last angle.
        val inPlane = hypot(dx, dy)
        if (filteredDeg.isNaN()) {
            filteredDeg = target
        } else {
            val gain = ((inPlane - 0.12f) / 0.2f).coerceIn(0f, 1f)
            filteredDeg += wrap180(target - filteredDeg) * gain
            filteredDeg = wrap180(filteredDeg)
        }
        return filteredDeg.coerceIn(-limit, limit)
    }

    private fun wrap180(a: Float): Float {
        var v = a
        while (v > 180f) v -= 360f
        while (v <= -180f) v += 360f
        return v
    }

    /**
     * The sensor clock the frame was captured on is not always the one the gyroscope uses. Work out how old the frame
     * is (30-80 ms is normal) and ask the orientation history for that moment. Returns Long.MAX_VALUE (= use the
     * newest sample) when the two clocks cannot be matched.
     */
    private fun queryTime(frameTs: Long): Long {
        val nowReal = SystemClock.elapsedRealtimeNanos()
        val dReal = nowReal - frameTs
        if (dReal in 0..500_000_000L) return nowReal - dReal
        val dMono = System.nanoTime() - frameTs
        if (dMono in 0..500_000_000L) return nowReal - dMono
        return Long.MAX_VALUE
    }

    /**
     * Zoom factor that keeps a rotated copy of the output rectangle fully inside the camera picture, for any roll up
     * to [limitDeg]. Smaller = tighter crop.
     */
    private val fitCache = HashMap<Long, Float>()
    private fun fitScale(w: Int, h: Int, limitDeg: Float): Float {
        val key = (w.toLong() shl 40) xor (h.toLong() shl 16) xor limitDeg.toInt().toLong()
        return fitCache.getOrPut(key) {
            val a = w / 2.0
            val b = h / 2.0
            var worst = 1.0
            val maxDeg = min(limitDeg, 90f).toDouble() // the worst case of a full turn is inside 0..90 degrees
            var d = 0.0
            while (d <= maxDeg + 1e-6) {
                val r = Math.toRadians(d)
                val c = abs(cos(r))
                val s = abs(sin(r))
                val sx = a / (a * c + b * s)
                val sy = b / (a * s + b * c)
                worst = min(worst, min(sx, sy))
                d += 0.5
            }
            (worst * 0.985).toFloat() // a hair of margin so the edge pixels are never sampled
        }
    }

    // ===================================================================================================
    //  Sensors
    // ===================================================================================================

    private fun startSensors() {
        if (sensorListener != null) return
        val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        // Gyro + accelerometer fused, no compass: not thrown off by metal, and smooth. Plain accelerometer is the last resort.
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return

        val rot = FloatArray(9)
        val lp = FloatArray(3)
        var lpInit = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rot, e.values)
                        // third row of the rotation matrix = world "up" in phone coordinates
                        history.add(e.timestamp, rot[6], rot[7], rot[8])
                    }
                    else -> {
                        if (!lpInit) { lp[0] = e.values[0]; lp[1] = e.values[1]; lp[2] = e.values[2]; lpInit = true }
                        else for (i in 0..2) lp[i] = 0.85f * lp[i] + 0.15f * e.values[i]
                        val n = sqrt(lp[0] * lp[0] + lp[1] * lp[1] + lp[2] * lp[2])
                        if (n > 1e-3f) history.add(e.timestamp, lp[0] / n, lp[1] / n, lp[2] / n)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        // 5000 us = 200 Hz, the most Android allows without the HIGH_SAMPLING_RATE_SENSORS permission.
        if (sm.registerListener(listener, sensor, 5000, handler)) {
            sensorManager = sm
            sensorListener = listener
        }
    }

    private fun stopSensors() {
        sensorListener?.let { sensorManager?.unregisterListener(it) }
        sensorListener = null
        sensorManager = null
    }

    /** The last ~1 second of "up" vectors, so a frame can be matched with the phone's orientation at its exposure. */
    private class UpHistory(private val cap: Int = 256) {
        private val t = LongArray(cap)
        private val x = FloatArray(cap)
        private val y = FloatArray(cap)
        private val z = FloatArray(cap)
        private var head = 0   // next write position
        private var count = 0

        @Synchronized fun add(ts: Long, ux: Float, uy: Float, uz: Float) {
            t[head] = ts; x[head] = ux; y[head] = uy; z[head] = uz
            head = (head + 1) % cap
            if (count < cap) count++
        }

        /** Fills [out] with the interpolated up vector at [q]. Long.MAX_VALUE = newest sample. */
        @Synchronized fun sample(q: Long, out: FloatArray): Boolean {
            if (count == 0) return false
            val newest = (head - 1 + cap) % cap
            if (q >= t[newest]) { out[0] = x[newest]; out[1] = y[newest]; out[2] = z[newest]; return true }
            var i1 = newest
            for (k in 1 until count) {
                val i0 = (newest - k + cap) % cap
                if (t[i0] <= q) {
                    val span = (t[i1] - t[i0]).toFloat()
                    val f = if (span > 0f) ((q - t[i0]).toFloat() / span).coerceIn(0f, 1f) else 0f
                    var vx = x[i0] + (x[i1] - x[i0]) * f
                    var vy = y[i0] + (y[i1] - y[i0]) * f
                    var vz = z[i0] + (z[i1] - z[i0]) * f
                    val n = sqrt(vx * vx + vy * vy + vz * vz)
                    if (n > 1e-4f) { vx /= n; vy /= n; vz /= n }
                    out[0] = vx; out[1] = vy; out[2] = vz
                    return true
                }
                i1 = i0
            }
            val oldest = (newest - count + 1 + cap) % cap
            out[0] = x[oldest]; out[1] = y[oldest]; out[2] = z[oldest]
            return true
        }
    }

    // ===================================================================================================
    //  GL setup / teardown
    // ===================================================================================================

    private fun ensureGl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) return

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val ver = IntArray(2)
        check(EGL14.eglInitialize(display, ver, 0, ver, 1)) { "eglInitialize failed" }

        val EGL_RECORDABLE_ANDROID = 0x3142
        fun choose(recordable: Boolean): EGLConfig? {
            val attribs = ArrayList<Int>().apply {
                addAll(listOf(
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT
                ))
                if (recordable) addAll(listOf(EGL_RECORDABLE_ANDROID, 1))
                add(EGL14.EGL_NONE)
            }.toIntArray()
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            return if (EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) && num[0] > 0) configs[0] else null
        }
        val config = choose(true) ?: choose(false) ?: error("no EGL config")

        val ctx = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(ctx != null && ctx != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        val pb = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        check(pb != null && pb != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }

        eglDisplay = display
        eglConfig = config
        eglContext = ctx
        pbuffer = pb
        makeCurrent(pbuffer)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPosition")
        aTex = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uSize = GLES20.glGetUniformLocation(program, "uSize")
        uCos = GLES20.glGetUniformLocation(program, "uCos")
        uSin = GLES20.glGetUniformLocation(program, "uSin")
        uScale = GLES20.glGetUniformLocation(program, "uScale")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
    }

    private fun makeCurrent(surface: EGLSurface) {
        EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)
    }

    fun release() {
        handler.post {
            if (released) return@post
            released = true
            stopSensors()
            inputTexture?.let {
                try { it.setOnFrameAvailableListener(null) } catch (_: Exception) {}
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                makeCurrent(pbuffer)
                for (o in outputs) {
                    EGL14.eglDestroySurface(eglDisplay, o.eglSurface)
                    try { o.output.close() } catch (_: Exception) {}
                }
                outputs.clear()
                if (program != 0) GLES20.glDeleteProgram(program)
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, pbuffer)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
            thread.quitSafely()
        }
    }

    private fun runOnGl(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader compile failed: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "program link failed: " + GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private companion object {
        fun floatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(data); position(0)
            }

        // The picture is turned in the OUTPUT's own pixel space (so a 9:16 frame rotates without stretching),
        // zoomed by uScale, and only then sent through the matrix CameraX gave for this output.
        const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            uniform vec2 uSize;
            uniform float uCos;
            uniform float uSin;
            uniform float uScale;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTex;
            void main() {
                gl_Position = aPosition;
                vec2 p = (aTexCoord.xy - 0.5) * uSize;
                p = vec2(uCos * p.x - uSin * p.y, uSin * p.x + uCos * p.y) * uScale;
                vec2 t = p / uSize + 0.5;
                vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
        """
    }
}
