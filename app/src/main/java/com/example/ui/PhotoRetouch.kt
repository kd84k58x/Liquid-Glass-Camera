package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** How much retouch is applied to a photo after it is taken. */
enum class RetouchLevel(val label: String, val strength: Float) {
    OFF("Off", 0f),
    LIGHT("Light", 0.45f),
    NATURAL("Natural", 0.75f),
    STRONG("Strong", 1f)
}

/** A photo decoded for editing: upright pixels, plus the EXIF tags worth keeping when it is saved again. */
class DecodedPhoto(val bitmap: Bitmap, val exif: Map<String, String>)

/**
 * On-device "AI retouch". No network, nothing leaves the phone.
 *
 *  1. ML Kit finds the faces.
 *  2. Skin smoothing, only on skin-coloured pixels inside each face: the skin is blurred, and the blur is blended
 *     back in less where the picture has a strong edge (eyes, brows, lips, hair line), so features stay sharp
 *     while spots, pores and uneven tone fade. A little texture is always kept so the skin does not look like plastic.
 *  3. Auto enhance for the whole photo: auto levels, gamma that brings the average brightness to a pleasant
 *     level (lifts dark photos), a gentle S-curve, a little more colour and "clarity" (local contrast).
 *     Clarity is turned almost off on faces so it does not bring the skin texture back.
 *
 * Call everything here from a background thread (Dispatchers.IO). Face detection waits for ML Kit and throws on
 * the main thread.
 */
object PhotoRetouch {

    /** Photos larger than this (long edge) are decoded at a smaller size, so a 50MP photo cannot run out of memory. */
    private const val MAX_EDGE = 4096

    private val KEEP_TAGS = arrayOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP
    )

    // ------------------------------------------------------------------------------------------------------
    // load / save
    // ------------------------------------------------------------------------------------------------------

    /**
     * Decodes [uri] to a mutable ARGB_8888 bitmap that is turned the right way up (the EXIF rotation is applied to
     * the pixels). Saving it again without EXIF therefore never leaves a photo lying on its side.
     */
    fun decodeUpright(context: Context, uri: Uri): DecodedPhoto? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val tags = HashMap<String, String>()
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                for (tag in KEEP_TAGS) exif.getAttribute(tag)?.let { tags[tag] = it }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) {
            val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (turned !== bmp) bmp.recycle()
            bmp = turned
        }
        if (!bmp.isMutable || bmp.config != Bitmap.Config.ARGB_8888) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, true)
            bmp.recycle()
            bmp = copy
        }
        return DecodedPhoto(bmp, tags)
    }

    /** Writes the kept EXIF tags (date, location, camera) back into the JPEG at [uri] and marks it upright. */
    fun restoreExif(context: Context, uri: Uri, tags: Map<String, String>) {
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                for ((k, v) in tags) exif.setAttribute(k, v)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // the retouch
    // ------------------------------------------------------------------------------------------------------

    /** Retouches [src] and returns the result (the same bitmap when it can be edited in place). */
    fun retouch(src: Bitmap, strength: Float): Bitmap {
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0f) return src
        val bmp = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, true)

        val faces = detectFaces(bmp)
        for (face in faces) {
            try {
                smoothFace(bmp, face, s)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        try {
            enhance(bmp, s, faces)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bmp
    }

    private fun detectFaces(bmp: Bitmap): List<Rect> {
        val scale = min(1f, 1024f / max(bmp.width, bmp.height))
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).roundToInt().coerceAtLeast(1), (bmp.height * scale).roundToInt().coerceAtLeast(1), true
            )
        } else bmp
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.08f)
                .build()
        )
        return try {
            val found = Tasks.await(detector.process(InputImage.fromBitmap(small, 0)), 5, TimeUnit.SECONDS)
            found.map { f ->
                val b = f.boundingBox
                Rect(
                    (b.left / scale).roundToInt(), (b.top / scale).roundToInt(),
                    (b.right / scale).roundToInt(), (b.bottom / scale).roundToInt()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            detector.close()
            if (small !== bmp) small.recycle()
        }
    }

    private fun smooth01(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 1 inside [lo, hi], fading to 0 over [soft] outside it. */
    private fun band(v: Float, lo: Float, hi: Float, soft: Float): Float {
        val a = ((v - (lo - soft)) / soft).coerceIn(0f, 1f)
        val b = (((hi + soft) - v) / soft).coerceIn(0f, 1f)
        return min(a, b)
    }

    private fun smoothFace(bmp: Bitmap, face: Rect, strength: Float) {
        val fw = face.width()
        val fh = face.height()
        if (fw < 40 || fh < 40) return
        // the face box plus forehead, cheeks and chin
        val l = (face.left - fw * 0.20f).toInt().coerceAtLeast(0)
        val t = (face.top - fh * 0.22f).toInt().coerceAtLeast(0)
        val r = (face.right + fw * 0.20f).toInt().coerceAtMost(bmp.width)
        val b = (face.bottom + fh * 0.12f).toInt().coerceAtMost(bmp.height)
        val w = r - l
        val h = b - t
        if (w < 48 || h < 48) return

        val orig = IntArray(w * h)
        bmp.getPixels(orig, 0, w, l, t, w, h)
        val low = boxBlur(orig, w, h, max(2, (fw * 0.014f).toInt()), 3)

        val cx = w / 2f
        val cy = h / 2f
        val maxA = 0.85f * strength
        for (y in 0 until h) {
            val dy = (y - cy) / cy
            for (x in 0 until w) {
                val i = y * w + x
                val dx = (x - cx) / cx
                val win = 1f - smooth01(((dx * dx + dy * dy) - 0.55f) / 0.45f)
                if (win <= 0f) continue

                val c = orig[i]
                val cr8 = (c shr 16) and 0xFF
                val cg8 = (c shr 8) and 0xFF
                val cb8 = c and 0xFF
                val yy = 0.299f * cr8 + 0.587f * cg8 + 0.114f * cb8
                val cb = 128f - 0.168736f * cr8 - 0.331264f * cg8 + 0.5f * cb8
                val cr = 128f + 0.5f * cr8 - 0.418688f * cg8 - 0.081312f * cb8
                // skin colour (YCbCr), not too dark
                val skin = band(cb, 77f, 127f, 6f) * band(cr, 133f, 173f, 6f) * smooth01((yy - 35f) / 25f)
                if (skin <= 0f) continue

                val lw = low[i]
                val lr = (lw shr 16) and 0xFF
                val lg = (lw shr 8) and 0xFF
                val lb = lw and 0xFF
                val ly = 0.299f * lr + 0.587f * lg + 0.114f * lb
                val diff = kotlin.math.abs(yy - ly)
                // strong edges (eyes, brows, lips) keep their detail, small spots and pores do not
                val edge = 1f / (1f + (diff / 30f) * (diff / 30f))

                val a = maxA * skin * win * edge
                val nr = (cr8 + (lr - cr8) * a).roundToInt().coerceIn(0, 255)
                val ng = (cg8 + (lg - cg8) * a).roundToInt().coerceIn(0, 255)
                val nb = (cb8 + (lb - cb8) * a).roundToInt().coerceIn(0, 255)
                orig[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        bmp.setPixels(orig, 0, w, l, t, w, h)
    }

    // ------------------------------------------------------------------------------------------------------
    // auto enhance
    // ------------------------------------------------------------------------------------------------------

    private fun enhance(bmp: Bitmap, strength: Float, faces: List<Rect>) {
        val w = bmp.width
        val h = bmp.height

        // 1. brightness statistics from a coarse sample of the photo
        val hist = IntArray(256)
        var count = 0
        val stepX = max(1, w / 320)
        val stepY = max(1, h / 320)
        val row = IntArray(w)
        var sy = 0
        while (sy < h) {
            bmp.getPixels(row, 0, w, 0, sy, w, 1)
            var sx = 0
            while (sx < w) {
                val c = row[sx]
                val l = (77 * ((c shr 16) and 0xFF) + 150 * ((c shr 8) and 0xFF) + 29 * (c and 0xFF)) shr 8
                hist[l.coerceIn(0, 255)]++
                count++
                sx += stepX
            }
            sy += stepY
        }
        if (count == 0) return

        fun percentile(p: Float): Int {
            val target = (count * p).toInt()
            var acc = 0
            for (i in 0..255) {
                acc += hist[i]
                if (acc >= target) return i
            }
            return 255
        }

        // 2. one lookup table: levels, gamma and a gentle S-curve
        val lo = percentile(0.005f)
        val hi = percentile(0.995f)
        val black = lo * 0.7f * strength
        val white = 255f - (255f - hi) * 0.7f * strength
        val range = max(white - black, 128f)

        var mean = 0.0
        for (i in 0..255) mean += hist[i] * ((i - black) / range).coerceIn(0f, 1f).toDouble()
        mean /= count
        var gamma = if (mean > 0.02) (ln(0.46) / ln(mean)).toFloat() else 1f
        gamma = gamma.coerceIn(0.72f, 1.15f)
        gamma = 1f + (gamma - 1f) * strength

        val curve = 0.30f * strength
        val lut = IntArray(256) { v ->
            val x = ((v - black) / range).coerceIn(0f, 1f).pow(gamma)
            val s = x * x * (3f - 2f * x)
            ((x + (s - x) * curve) * 255f).roundToInt().coerceIn(0, 255)
        }
        val sat = 1f + 0.14f * strength

        // 3. a very small, heavily blurred copy of the photo, for the local contrast ("clarity")
        var low = Bitmap.createScaledBitmap(bmp, max(1, w / 2), max(1, h / 2), true)
        while (low.width > max(8, w / 16) && low.height > 8) {
            val next = Bitmap.createScaledBitmap(low, max(1, low.width / 2), max(1, low.height / 2), true)
            low.recycle()
            low = next
        }
        val lw = low.width
        val lh = low.height
        val lowPx = IntArray(lw * lh)
        low.getPixels(lowPx, 0, lw, 0, 0, lw, lh)
        low.recycle()
        val lowY = FloatArray(lw * lh) {
            val c = lowPx[it]
            0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }

        // faces get almost no clarity (it would bring the skin texture back)
        val faceBoxes = faces.map {
            val fw = it.width()
            val fh = it.height()
            Rect(
                (it.left - fw * 0.2f).toInt(), (it.top - fh * 0.25f).toInt(),
                (it.right + fw * 0.2f).toInt(), (it.bottom + fh * 0.15f).toInt()
            )
        }
        val clarity = 0.30f * strength

        // 4. one pass over the photo, a band of rows at a time
        val bandRows = max(1, 262144 / w)
        val buf = IntArray(w * bandRows)
        var y0 = 0
        while (y0 < h) {
            val rows = min(bandRows, h - y0)
            bmp.getPixels(buf, 0, w, 0, y0, w, rows)
            for (ry in 0 until rows) {
                val y = y0 + ry
                val fy = ((y + 0.5f) * lh / h - 0.5f).coerceIn(0f, (lh - 1).toFloat())
                val iy = fy.toInt()
                val iy2 = min(iy + 1, lh - 1)
                val ty = fy - iy
                for (x in 0 until w) {
                    val i = ry * w + x
                    val c = buf[i]
                    val r0 = (c shr 16) and 0xFF
                    val g0 = (c shr 8) and 0xFF
                    val b0 = c and 0xFF
                    val y0f = 0.299f * r0 + 0.587f * g0 + 0.114f * b0

                    var r = lut[r0].toFloat()
                    var g = lut[g0].toFloat()
                    var b = lut[b0].toFloat()

                    // a little more colour
                    val l1 = 0.299f * r + 0.587f * g + 0.114f * b
                    r = l1 + (r - l1) * sat
                    g = l1 + (g - l1) * sat
                    b = l1 + (b - l1) * sat

                    // local contrast
                    val fx = ((x + 0.5f) * lw / w - 0.5f).coerceIn(0f, (lw - 1).toFloat())
                    val ix = fx.toInt()
                    val ix2 = min(ix + 1, lw - 1)
                    val tx = fx - ix
                    val top = lowY[iy * lw + ix] * (1f - tx) + lowY[iy * lw + ix2] * tx
                    val bot = lowY[iy2 * lw + ix] * (1f - tx) + lowY[iy2 * lw + ix2] * tx
                    val lowAt = top * (1f - ty) + bot * ty
                    var amount = clarity
                    for (fb in faceBoxes) {
                        if (x >= fb.left && x < fb.right && y >= fb.top && y < fb.bottom) {
                            amount *= 0.2f
                            break
                        }
                    }
                    val delta = ((y0f - lowAt) * amount).coerceIn(-40f, 40f)
                    r += delta
                    g += delta
                    b += delta

                    buf[i] = (0xFF shl 24) or
                        (r.roundToInt().coerceIn(0, 255) shl 16) or
                        (g.roundToInt().coerceIn(0, 255) shl 8) or
                        b.roundToInt().coerceIn(0, 255)
                }
            }
            bmp.setPixels(buf, 0, w, 0, y0, w, rows)
            y0 += rows
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // box blur (three passes look like a gaussian blur)
    // ------------------------------------------------------------------------------------------------------

    private fun boxBlur(src: IntArray, w: Int, h: Int, radius: Int, passes: Int): IntArray {
        var a = src.copyOf()
        var b = IntArray(src.size)
        repeat(passes) {
            blurRows(a, b, w, h, radius)
            blurColumns(b, a, w, h, radius)
        }
        return a
    }

    private fun blurRows(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[row + min(x + r + 1, w - 1)]
                val rem = src[row + max(x - r, 0)]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }

    private fun blurColumns(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[min(y + r + 1, h - 1) * w + x]
                val rem = src[max(y - r, 0) * w + x]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
