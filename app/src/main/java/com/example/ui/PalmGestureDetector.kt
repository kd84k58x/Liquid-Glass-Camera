package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A real hand detector for the "Palm Gesture Shutter".
 *
 * It runs MediaPipe's Gesture Recognizer on the phone (no photo ever leaves the device): a palm detector finds the hand,
 * a landmark model reads the 21 joints of the hand, and a classifier names the gesture.
 *
 * A palm counts as "open" only when the classifier and the joints agree, so a bright wall, a face, a lamp or a half closed
 * hand cannot trigger the shutter, and a real open palm that the classifier scores badly (tilted, far away) is not missed:
 *  - the classifier says "Open_Palm", AND at least 3 of the 4 fingers really are stretched out, OR
 *  - all 4 fingers and the thumb are stretched out and spread (the joints alone are enough).
 * Up to two hands are looked at, either one can be the palm. Very small hands (far in the background) are ignored.
 *
 * [isPalmHeld] adds a short memory on top: the palm has to be seen in most of the last few looks, but one missed frame
 * does not start the count again, which is what made the old version feel hit and miss.
 *
 * The model bundle (gesture_recognizer.task, about 8 MB) is looked for in this order:
 *  1. app/src/main/assets/gesture_recognizer.task  (put it there to ship the app fully offline)
 *  2. a copy kept in the app's private files folder
 *  3. downloaded once from Google's model storage, then kept in (2)
 *
 * [create] blocks (it may download), so call it from Dispatchers.IO. [isOpenPalm], [isPalmHeld], [resetHold] and [close]
 * belong on the same background thread that reads the camera frames.
 */
class PalmGestureDetector private constructor(private val recognizer: GestureRecognizer) {

    // the last few looks (true = a palm was seen), used by isPalmHeld
    private val history = BooleanArray(HOLD_WINDOW)
    private var historyIndex = 0

    /** How many of the last looks saw a palm. 0 means the hand is gone. */
    val palmVotes: Int
        get() = history.count { it }

    /** Forget the recent looks (call when the camera is busy, or right after a shot). */
    fun resetHold() {
        history.fill(false)
        historyIndex = 0
    }

    /**
     * Looks at this frame and returns true once the palm has been steady: seen in at least [HOLD_NEEDED] of the last
     * [HOLD_WINDOW] looks, and in this one. Call it about every [LOOK_INTERVAL_MS]. The frame is not closed here.
     */
    fun isPalmHeld(frame: ImageProxy): Boolean {
        val open = isOpenPalm(frame)
        history[historyIndex] = open
        historyIndex = (historyIndex + 1) % HOLD_WINDOW
        return open && palmVotes >= HOLD_NEEDED
    }

    /** True when an open palm is in this camera frame (one look, no memory). The frame is not closed here. */
    fun isOpenPalm(frame: ImageProxy): Boolean {
        val raw = frame.toBitmap()                       // ARGB_8888
        val rotation = frame.imageInfo.rotationDegrees
        // hands are recognised upright, so turn the sensor frame the way the user sees it
        val upright = if (rotation == 0) raw else Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true
        )
        val mpImage = BitmapImageBuilder(upright).build()
        try {
            val result = recognizer.recognize(mpImage)
            val gestures = result.gestures()
            val hands = result.landmarks()
            val w = upright.width.toFloat()
            val h = upright.height.toFloat()
            // gestures() and landmarks() hold one entry per hand, in the same order
            for (i in hands.indices) {
                val top = gestures.getOrNull(i)?.firstOrNull()
                val classifierSaysOpen = top != null && top.categoryName() == OPEN_PALM && top.score() >= MIN_SCORE
                if (isOpenHand(hands[i], w, h, classifierSaysOpen)) return true
            }
            return false
        } finally {
            mpImage.close()
            if (upright !== raw) upright.recycle()
            raw.recycle()
        }
    }

    /** Checks the 21 joints of one hand. [w] and [h] are the frame size, so distances are real pixel distances. */
    private fun isOpenHand(pts: List<NormalizedLandmark>, w: Float, h: Float, classifierSaysOpen: Boolean): Boolean {
        if (pts.size < 21) return false
        fun dist(a: Int, b: Int): Float {
            val dx = (pts[a].x() - pts[b].x()) * w
            val dy = (pts[a].y() - pts[b].y()) * h
            return sqrt(dx * dx + dy * dy)
        }

        val palmWidth = dist(INDEX_MCP, PINKY_MCP)
        if (palmWidth < MIN_PALM_FRACTION * min(w, h)) return false      // a tiny, far away hand

        // a finger is stretched when its tip is clearly further from the wrist than its middle joint and its knuckle
        var stretched = 0
        for (f in 0 until 4) {
            val tip = FINGER_TIPS[f]
            val pip = FINGER_PIPS[f]
            val mcp = FINGER_MCPS[f]
            if (dist(WRIST, tip) > dist(WRIST, pip) * 1.12f && dist(WRIST, tip) > dist(WRIST, mcp) * 1.5f) stretched++
        }
        // the thumb is stretched when its tip is further from the little finger's knuckle than its middle joint,
        // and not folded across the palm
        val thumbOut = dist(THUMB_TIP, PINKY_MCP) > dist(THUMB_IP, PINKY_MCP) * 1.05f &&
            dist(THUMB_TIP, INDEX_MCP) > palmWidth * 0.5f
        val spread = dist(INDEX_TIP, PINKY_TIP) >= palmWidth * 0.8f

        if (classifierSaysOpen) return stretched >= 3
        return stretched == 4 && thumbOut && spread
    }

    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "PalmGesture"
        const val MODEL_FILE = "gesture_recognizer.task"
        private const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/latest/gesture_recognizer.task"
        private const val MIN_MODEL_BYTES = 1_000_000L
        private const val OPEN_PALM = "Open_Palm"
        private const val MIN_SCORE = 0.5f

        /** How often the camera code should look at a frame (about 12 looks a second). */
        const val LOOK_INTERVAL_MS = 80L
        private const val HOLD_WINDOW = 6        // the last 6 looks...
        private const val HOLD_NEEDED = 4        // ...4 of them with a palm, so about a third of a second
        private const val MIN_PALM_FRACTION = 0.06f

        // MediaPipe hand joints
        private const val WRIST = 0
        private const val THUMB_IP = 3
        private const val THUMB_TIP = 4
        private const val INDEX_MCP = 5
        private const val INDEX_TIP = 8
        private const val PINKY_MCP = 17
        private const val PINKY_TIP = 20
        private val FINGER_MCPS = intArrayOf(5, 9, 13, 17)
        private val FINGER_PIPS = intArrayOf(6, 10, 14, 18)
        private val FINGER_TIPS = intArrayOf(8, 12, 16, 20)

        /** Loads (and if needed downloads) the model. Returns null when the detector cannot start. Blocking. */
        fun create(context: Context): PalmGestureDetector? {
            return try {
                val appContext = context.applicationContext
                val base = BaseOptions.builder()
                val shipped = try {
                    appContext.assets.open(MODEL_FILE).close()
                    true
                } catch (e: Exception) {
                    false
                }
                if (shipped) {
                    base.setModelAssetPath(MODEL_FILE)
                } else {
                    val file = File(appContext.filesDir, MODEL_FILE)
                    if (!file.exists() || file.length() < MIN_MODEL_BYTES) {
                        if (!download(file)) return null
                    }
                    base.setModelAssetBuffer(mapFile(file))
                }
                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(base.build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(2)                              // either hand can be the palm
                    .setMinHandDetectionConfidence(0.4f)         // lower than before: far or tilted palms are found too
                    .setMinHandPresenceConfidence(0.4f)          // (the joint check above keeps false alarms out)
                    .setMinTrackingConfidence(0.5f)
                    .build()
                PalmGestureDetector(GestureRecognizer.createFromOptions(appContext, options))
            } catch (e: Throwable) {
                Log.e(TAG, "palm detector could not start", e)
                null
            }
        }

        /** The model file as a direct buffer (MediaPipe needs a direct one); mapped, so it is not copied into memory. */
        private fun mapFile(file: File): ByteBuffer =
            FileInputStream(file).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()) }

        /** Downloads the model next to [target] and only moves it into place when it arrived complete. */
        private fun download(target: File): Boolean {
            val part = File(target.parentFile, target.name + ".part")
            var connection: HttpURLConnection? = null
            return try {
                val c = URL(MODEL_URL).openConnection() as HttpURLConnection
                connection = c
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                if (c.responseCode != HttpURLConnection.HTTP_OK) return false
                val expected = c.contentLengthLong
                c.inputStream.use { input -> part.outputStream().use { out -> input.copyTo(out) } }
                val complete = part.length() >= MIN_MODEL_BYTES && (expected <= 0L || part.length() == expected)
                if (complete) {
                    target.delete()
                    part.renameTo(target)
                } else {
                    part.delete()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "model download failed", e)
                part.delete()
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}
