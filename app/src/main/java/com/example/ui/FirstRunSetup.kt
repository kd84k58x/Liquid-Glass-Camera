package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ai.GeminiSearchService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Version of this setup. CameraUI.kt shows the setup again when the saved "setup_version" is lower than this.
 * Phones that finished an older setup have no "setup_version" saved (0), so they run this one once.
 * Raise it by 1 whenever the setup changes in a way that every phone should run again.
 */
const val SETUP_VERSION = 2

// Google's four brand colours, used for the static progress gradient
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleGreen = Color(0xFF34A853)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleRed = Color(0xFFEA4335)

/**
 * What the one-time setup works out for this phone.
 *
 * @param maxZoom        the camera's maximum zoom ratio
 * @param zoomPresets    the far zoom buttons to show next to 1x / 2x / 5x (for example 10, 30, 50)
 * @param videoRes       video quality the app starts with ("720p", "1080p" or "4K")
 * @param videoFps       frame rate the app starts with ("30" or "60")
 * @param supports4k     this phone can record 4K video
 * @param supports60     this phone can record 60 fps video
 * @param googleSummary  one line from Google (via Gemini) about this phone's camera, or null
 * @param lookupError    why the Google lookup did not work (for example the API key was rejected), or null
 * @param googleSources  the websites Google Search used for the lookup (empty when search was not used)
 */
data class CameraSetupProfile(
    val maxZoom: Float,
    val zoomPresets: List<Int>,
    val videoRes: String,
    val videoFps: String,
    val supports4k: Boolean,
    val supports60: Boolean,
    val googleSummary: String? = null,
    val lookupError: String? = null,
    val googleSources: List<String> = emptyList()
)

/** Keeps the far-zoom row short: at most 4 buttons. */
private fun trimPresets(ladder: List<Int>): List<Int> =
    if (ladder.size <= 4) ladder
    else listOf(ladder.first(), ladder[ladder.size / 2], ladder[ladder.size - 2], ladder.last()).distinct()

/** The far-zoom buttons (10x, 30x, 50x, 100x ...) that fit a camera whose largest zoom is [maxZoom]. */
private fun zoomLadder(maxZoom: Float): List<Int> {
    val ladder = listOf(10, 30, 50, 100).filter { it <= maxZoom + 0.5f }.toMutableList()
    val top = kotlin.math.floor(maxZoom + 0.05f).toInt()
    if (top >= 10 && top > (ladder.lastOrNull() ?: 0)) ladder.add(top)
    if (ladder.isEmpty() && maxZoom > 5.5f) ladder.add(kotlin.math.floor(maxZoom).toInt())
    return trimPresets(ladder)
}

/**
 * Reads the real camera hardware of this phone (zoom range, video modes, frame rates) and picks sensible
 * starting settings. Works offline and needs no camera permission.
 */
fun analyzeDeviceCamera(context: Context): CameraSetupProfile {
    var maxZoom = 1f
    var supports60 = false
    try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val backId = manager.cameraIdList.firstOrNull { id ->
            try {
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } catch (e: Exception) {
                false
            }
        }
        if (backId != null) {
            val chars = manager.getCameraCharacteristics(backId)
            if (Build.VERSION.SDK_INT >= 30) {
                chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { maxZoom = it.upper }
            }
            if (maxZoom <= 1.01f) {
                maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            }
            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            supports60 = fpsRanges?.any { it.upper >= 60 } == true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    fun hasQuality(quality: Int): Boolean = try {
        CamcorderProfile.hasProfile(quality)
    } catch (e: Exception) {
        false
    }

    val supports4k = hasQuality(CamcorderProfile.QUALITY_2160P)
    val has1080 = hasQuality(CamcorderProfile.QUALITY_1080P)
    // 1080p / 30 is the smooth, safe start; 4K and 60 stay available in the quality menu.
    val videoRes = if (has1080 || supports4k) "1080p" else "720p"

    return CameraSetupProfile(
        maxZoom = maxZoom,
        zoomPresets = zoomLadder(maxZoom),
        videoRes = videoRes,
        videoFps = "30",
        supports4k = supports4k,
        supports60 = supports60
    )
}

// ---------------------------------------------------------------------------------------------
// Gemini + Google Search lookup of this phone's real camera specs
// ---------------------------------------------------------------------------------------------

private class GeminiSpecs(
    val maxZoom: Float?,
    val zoomButtons: List<Int>,
    val has4k: Boolean?,
    val has60: Boolean?,
    val summary: String?,
    val sources: List<String> = emptyList()
)

/** The same key the Search mode uses (see GeminiSearchService.resolveApiKey). Null when none is set. */
private fun findGeminiKey(@Suppress("UNUSED_PARAMETER") context: Context): String? =
    GeminiSearchService.resolveApiKey().takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }

/**
 * Asks Gemini (with Google Search turned on) for the real camera specs of [deviceName].
 * Returns the specs, or a short human-readable reason why it did not work. Never throws.
 * Blocking: call it from Dispatchers.IO.
 */
private fun lookupSpecsWithGemini(context: Context, deviceName: String): Pair<GeminiSpecs?, String?> {
    val key = findGeminiKey(context)
        ?: return null to "No Gemini API key found"

    val (models, listError) = geminiCandidateModels(key)
    if (listError != null) return null to listError

    var lastError: String? = null
    var hitQuota = false
    for (model in models) {
        var withSearch = true
        var attempts = 0
        var waited = false
        while (attempts < 4) {
            attempts++
            val prompt = buildSpecsPrompt(deviceName, withSearch)
            val result: Pair<Int, String>? = try {
                httpCall(GeminiSearchService.requestUrl(model, key), key, buildGeminiBody(prompt, withSearch))
            } catch (e: java.net.UnknownHostException) {
                return null to "No internet connection"
            } catch (e: java.net.SocketTimeoutException) {
                lastError = "Google lookup timed out"
                null
            } catch (e: Exception) {
                lastError = "Google lookup failed"
                null
            }
            if (result == null) break
            val (code, text) = result
            if (code in 200..299) {
                val specs = parseGeminiSpecs(text)
                if (specs != null) return specs to null
                lastError = "Gemini gave no usable answer"
                break
            }
            if (geminiKeyRejected(code, text)) return null to "Gemini API key was rejected"
            if (code == 400 && withSearch) {
                // this model does not offer the Google Search tool: ask it again without the tool
                withSearch = false
                continue
            }
            if (code == 429) {
                hitQuota = true
                if (withSearch) {
                    // The free "Google Search" allowance is much smaller than the normal one, so it runs out first.
                    // Ask the same model again without the search tool before giving up on it.
                    withSearch = false
                    continue
                }
                // Short per-minute limit: wait the few seconds Google asks for and try this model once more.
                val waitMs = retryDelayMillis(text)
                if (!waited && waitMs in 1L..8000L) {
                    waited = true
                    try {
                        Thread.sleep(waitMs + 250L)
                    } catch (e: InterruptedException) {
                        return null to "Google lookup stopped"
                    }
                    continue
                }
                lastError = "Gemini quota reached"
                break // next model: every model has its own allowance
            }
            lastError = when (code) {
                404 -> "Gemini model not available"
                else -> "Gemini error $code"
            }
            break
        }
    }
    return null to (if (hitQuota) "Gemini quota reached" else lastError ?: "Google lookup failed")
}

private fun buildSpecsPrompt(deviceName: String, withSearch: Boolean): String {
    val intro = if (withSearch) {
        "Use Google Search to find the camera specifications of this phone. Search several different websites and " +
            "cross-check them: the manufacturer's official product page, GSMArena, and at least one detailed review " +
            "site. Make sure it is this exact model and not a sibling (Pro / Plus / Ultra / FE / Lite / older " +
            "generation). For max_zoom give the highest total zoom ratio (optical plus digital) that the " +
            "manufacturer officially advertises for the back cameras: \"30x zoom\" means 30, \"100x Space Zoom\" " +
            "means 100. If the sites disagree, use the manufacturer's number. The phone: "
    } else {
        "From what you know, give the official camera specifications of this phone (max_zoom is the highest " +
            "total zoom ratio, optical plus digital, that the manufacturer advertises): "
    }
    return intro +
        "\"$deviceName\" (manufacturer ${Build.MANUFACTURER}, model code ${Build.MODEL}). " +
        "Reply with ONLY one JSON object and nothing else, in exactly this shape: " +
        "{\"max_zoom\": number (largest total zoom ratio including digital zoom, as advertised), " +
        "\"zoom_buttons\": [numbers: the zoom steps a camera app should offer, for example 1,2,5,10,30,50], " +
        "\"video_4k\": true or false, \"video_60fps\": true or false, " +
        "\"summary\": \"one short sentence about the main camera and zoom\"}"
}

/** How long Google asks us to wait after a 429 (milliseconds), or 0 when the answer does not say. */
private fun retryDelayMillis(errorText: String): Long {
    val match = Regex("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"").find(errorText)
        ?: Regex("retry in ([0-9.]+)s", RegexOption.IGNORE_CASE).find(errorText)
    val seconds = match?.groupValues?.get(1)?.toDoubleOrNull() ?: return 0L
    return (seconds * 1000).toLong()
}

private fun buildGeminiBody(prompt: String, withSearch: Boolean): String {
    val o = JSONObject().put(
        "contents",
        org.json.JSONArray().put(
            JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
        )
    )
    if (withSearch) o.put("tools", org.json.JSONArray().put(JSONObject().put("google_search", JSONObject())))
    return o.toString()
}

/** One HTTP call to the Gemini API. GET when [body] is null, POST otherwise. Returns (status code, response text). */
private fun httpCall(url: String, key: String, body: String?): Pair<Int, String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = if (body == null) "GET" else "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 40000
        conn.setRequestProperty("x-goog-api-key", key)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to text
    } finally {
        conn.disconnect()
    }
}

private fun geminiKeyRejected(code: Int, text: String): Boolean =
    code == 401 || code == 403 || (code == 400 && text.contains("API key", ignoreCase = true))

/**
 * Asks Google which models this key can really use (so a model that was retired never causes an error),
 * and returns the Flash models to try, best first. If the list cannot be read, a safe default list is used.
 */
private fun geminiCandidateModels(key: String): Pair<List<String>, String?> {
    // Every model has its own free allowance, so a longer list means fewer "quota reached" results. The lite
    // models have the biggest allowance and are plenty for a small JSON answer.
    val preferred = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite"
    )
    try {
        val (code, text) = httpCall("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", key, null)
        if (geminiKeyRejected(code, text)) return emptyList<String>() to "Gemini API key was rejected"
        if (code in 200..299) {
            val arr = JSONObject(text).optJSONArray("models")
            val available = mutableListOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var canGenerate = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) if (methods.optString(j) == "generateContent") canGenerate = true
                    }
                    if (canGenerate) available.add(m.optString("name").removePrefix("models/"))
                }
            }
            if (available.isNotEmpty()) {
                val skip = listOf("image", "tts", "live", "audio", "embedding", "robotics", "computer")
                val extra = available.filter { n -> n.contains("flash") && skip.none { n.contains(it) } && n !in preferred }
                val ordered = preferred.filter { it in available } + extra.take(3)
                if (ordered.isNotEmpty()) return ordered to null
            }
        }
    } catch (e: java.net.UnknownHostException) {
        return emptyList<String>() to "No internet connection"
    } catch (e: Exception) {
        // fall through to the default list
    }
    return preferred to null
}

private fun parseGeminiSpecs(responseText: String): GeminiSpecs? = try {
    val root = JSONObject(responseText)
    val candidate = root.getJSONArray("candidates").getJSONObject(0)
    val parts = candidate.getJSONObject("content").getJSONArray("parts")
    // the websites Google Search really used (only present when the search tool was on)
    val sources = mutableListOf<String>()
    candidate.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")?.let { chunks ->
        for (i in 0 until chunks.length()) {
            val title = chunks.optJSONObject(i)?.optJSONObject("web")?.optString("title").orEmpty().trim()
            if (title.isNotBlank() && title !in sources) sources.add(title)
        }
    }
    val sb = StringBuilder()
    for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
    val raw = sb.toString()
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) {
        null
    } else {
        val json = JSONObject(raw.substring(start, end + 1))
        val buttons = mutableListOf<Int>()
        json.optJSONArray("zoom_buttons")?.let { arr ->
            for (i in 0 until arr.length()) buttons.add(arr.optDouble(i, 0.0).toInt())
        }
        GeminiSpecs(
            maxZoom = if (json.has("max_zoom")) json.optDouble("max_zoom", 0.0).toFloat().takeIf { it > 0f } else null,
            zoomButtons = buttons.filter { it > 0 },
            has4k = if (json.has("video_4k")) json.optBoolean("video_4k") else null,
            has60 = if (json.has("video_60fps")) json.optBoolean("video_60fps") else null,
            summary = json.optString("summary").takeIf { it.isNotBlank() },
            sources = sources.take(5)
        )
    }
} catch (e: Exception) {
    null
}

/**
 * Combines the phone's own camera data with what Google/Gemini found. The bigger zoom wins: many phones tell other
 * apps only 10x through the camera API while the maker advertises 30x or more, so the phone's own number is
 * often too low. A Gemini zoom outside a sane range is ignored. Video modes only ever get added, never removed.
 */
private fun mergeProfiles(hw: CameraSetupProfile, gem: GeminiSpecs?, err: String?): CameraSetupProfile {
    val gemZoom = gem?.maxZoom?.takeIf { it in 1.5f..300f }
    val maxZ = maxOf(hw.maxZoom, gemZoom ?: 0f)
    val fromGem = gem?.zoomButtons.orEmpty()
        .filter { it > 5 && it <= maxZ + 0.5f }
        .distinct()
        .sorted()
    // Use Google's button list only when it reaches the top of the zoom range; otherwise rebuild it from the
    // merged maximum so a 30x phone never ends up with a 10x button row.
    val presets = if (fromGem.isNotEmpty() && fromGem.last() >= maxZ - 1f) trimPresets(fromGem) else zoomLadder(maxZ)
    return hw.copy(
        maxZoom = maxZ,
        zoomPresets = presets,
        supports4k = hw.supports4k || gem?.has4k == true,
        supports60 = hw.supports60 || gem?.has60 == true,
        googleSummary = gem?.summary,
        lookupError = err,
        googleSources = gem?.sources.orEmpty()
    )
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * One-time setup shown on the very first launch (and again after the app's data is cleared):
 * 0. welcome  ->  1. terms & conditions  ->  2. device name  ->  3. checks the camera and searches Google (Gemini),
 *  with a "Skip testing" slider  ->  4. result
 *  ->  5. the "Setting up your camera UI" loading screen (CameraUiLoadingScreen.kt), which cross-fades in after "Start Camera" is slid.
 * Agreeing on page 1 saves "terms_accepted" (and "terms_accepted_at") in "camera_prefs".
 * Everything is saved in "camera_prefs" the moment "Start Camera" is slid; "setup_done" tells CameraUI.kt it can skip
 * this screen. The camera itself opens when the arrow button on the loading screen is tapped (onFinished).
 */
@Composable
fun FirstRunSetupScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE) }

    // transparent navigation bar
    SideEffect {
        view.context.findActivity()?.window?.let { w ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
            // Without this, the OS falls back to panning the whole window to keep the focused
            // field visible *while* imePadding() below also reserves space for the keyboard, so
            // the layout gets shifted twice: the top bar/heading pan up off-screen under the
            // status bar, and a dead gap of background is left above the keyboard. ADJUST_RESIZE
            // is what makes Compose's ime insets (and imePadding()) report/behave correctly, so
            // set it here instead of trusting the manifest's windowSoftInputMode.
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
        }
    }

    var page by remember { mutableIntStateOf(0) }              // 0 welcome, 1 terms, 2 name, 3 checking, 4 result
    // true after "Start Camera" was slid: the video loading screen is shown on top until its arrow button is tapped
    var showLoading by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf(Build.MODEL ?: "") }
    var profile by remember { mutableStateOf<CameraSetupProfile?>(null) }
    var stepIndex by remember { mutableIntStateOf(0) }
    val steps = listOf(
        "Reading your camera hardware",
        "Searching Google for ${deviceName.trim().ifBlank { "your phone" }} specs",
        "Matching zoom and video modes",
        "Tuning your presets"
    )

    LaunchedEffect(page) {
        if (page == 3) {
            stepIndex = 0
            val hw = withContext(Dispatchers.Default) { analyzeDeviceCamera(context) }
            delay(600)
            stepIndex = 1
            val (gem, err) = withContext(Dispatchers.IO) { lookupSpecsWithGemini(context, deviceName.trim()) }
            stepIndex = 2
            delay(600)
            stepIndex = 3
            profile = mergeProfiles(hw, gem, err)
            delay(600)
            page = 4
        }
    }

    val finish: () -> Unit = {
        val p = profile
        val editor = prefs.edit()
            .putString("device_name", deviceName.trim())
            .putBoolean("setup_done", true)
            .putInt("setup_version", SETUP_VERSION)
            // shown on the "Set Up Camera With Gemini" row in Camera Settings
            .putString("setup_ai_phone", deviceName.trim())
            .putString("setup_ai_summary", p?.googleSummary.orEmpty())
        if (p != null) {
            editor
                .putString("zoom_presets", p.zoomPresets.joinToString(","))
                .putFloat("setup_max_zoom", p.maxZoom)
                .putString("setup_video_res", p.videoRes)
                .putString("setup_video_fps", p.videoFps)
                .putBoolean("setup_supports_4k", p.supports4k)
                .putBoolean("setup_supports_60", p.supports60)
        }
        editor.apply()
        // the settings are saved; now the loading screen plays, and its arrow button calls onFinished()
        showLoading = true
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (page == 0) 0f else 0.38f,
        animationSpec = tween(450),
        label = "SetupScrim"
    )

    // After "Start Camera" is slid, the setup controls fade out while the loading screen fades in over them (the
    // cross-fade). Both use the same length (LoaderCrossFadeMs, see CameraUiLoadingScreen.kt).
    val setupAlpha by animateFloatAsState(
        targetValue = if (showLoading) 0f else 1f,
        animationSpec = tween(LoaderCrossFadeMs, easing = FastOutSlowInEasing),
        label = "SetupFadeOut"
    )

    var bgOrigin by remember { mutableStateOf(Offset.Zero) }
    // Carousel position: 0, 1, 2 ... one whole number per photo. It only moves when the person swipes the free space.
    val carousel = remember { Animatable(0f) }
    val carouselScope = rememberCoroutineScope()
    var swipeStart by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { bgOrigin = it.positionInRoot() }
            // Swipe on free space to slide the background photos. Buttons and the text field consume their own
            // drags first, so this parent only sees swipes that start on empty space.
            .pointerInput(Unit) {
                val widthPx = size.width.toFloat().coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = {
                        swipeStart = carousel.value.roundToInt().toFloat()
                        carouselScope.launch { carousel.stop() }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        carouselScope.launch { carousel.snapTo(carousel.value - dragAmount / widthPx) }
                    },
                    onDragEnd = {
                        val delta = carousel.value - swipeStart
                        val target = swipeStart + when {
                            delta > 0.2f -> 1f
                            delta < -0.2f -> -1f
                            else -> 0f
                        }
                        carouselScope.launch {
                            carousel.animateTo(target, tween(450, easing = FastOutSlowInEasing))
                        }
                    },
                    onDragCancel = {
                        carouselScope.launch {
                            carousel.animateTo(swipeStart, tween(300, easing = FastOutSlowInEasing))
                        }
                    }
                )
            }
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        // Background: the photos slide past in a carousel (slide_1 ... slide_4 in res/drawable)
        CarouselLayer(carousel = carousel, scrimAlpha = scrimAlpha, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = setupAlpha }
                .statusBarsPadding()
                // navigationBarsPadding() + imePadding() as two separate paddings SUM their bottom
                // insets (nav bar height + keyboard height) even though the keyboard already covers
                // the nav bar area — that's the gap of bare background sitting above the keyboard.
                // union() takes the larger of the two instead, so it's nav-bar height when the
                // keyboard is closed and keyboard height (not keyboard + nav bar) when it's open.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // top: app icon + step progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.icon_2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stepsDone = when (page) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 3 }
                    repeat(4) { i ->
                        val fill by animateFloatAsState(
                            targetValue = if (i <= stepsDone) 1f else 0f,
                            animationSpec = tween(400),
                            label = "TopStep$i"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fill)
                                    .fillMaxHeight()
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            // The text area takes the free space between the top bar and the buttons and scrolls when the keyboard
            // is open, so nothing is ever pushed under the status bar.
            val textScroll = rememberScrollState()
            val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            LaunchedEffect(imeOpen, page) {
                delay(120)
                textScroll.animateScrollTo(textScroll.maxValue)
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) {
                // the terms page fills the whole free area and scrolls inside itself (12.dp = the spacer above it)
                val termsHeight = (maxHeight - 12.dp).coerceAtLeast(0.dp)
                Column(modifier = Modifier.verticalScroll(textScroll)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(200)) },
                        label = "SetupPage"
                    ) { p ->
                        when (p) {
                            0 -> WelcomeText()
                            1 -> TermsText(modifier = Modifier.fillMaxWidth().height(termsHeight))
                            2 -> NameText(
                                name = deviceName,
                                onNameChange = { deviceName = it },
                                onDone = { if (deviceName.isNotBlank()) page = 3 }
                            )
                            3 -> CheckingText(steps = steps, stepIndex = stepIndex)
                            else -> ResultText(name = deviceName.trim(), profile = profile)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (page == 3) {
                // While the camera check and the Google lookup run: slide to skip them. The phone's own camera data
                // is used instead (read in a moment, no internet needed) and the result page opens.
                GlassSlideButton(
                    label = "Skip testing",
                    enabled = true,
                    screenWidth = screenW,
                    screenHeight = screenH,
                    bgOrigin = bgOrigin,
                    carousel = carousel,
                    scrimAlpha = scrimAlpha,
                    modifier = Modifier.fillMaxWidth(),
                    onComplete = {
                        if (page == 3) {
                            carouselScope.launch {
                                val hw = withContext(Dispatchers.Default) { analyzeDeviceCamera(context) }
                                // changing the page also stops the running check (its LaunchedEffect is keyed on page)
                                profile = mergeProfiles(hw, null, "Testing skipped")
                                page = 4
                            }
                        }
                    }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // result page only: slide to run the camera check (and the Google lookup) again
                    if (page == 4) {
                        GlassSlideButton(
                            label = "Retest",
                            enabled = true,
                            screenWidth = screenW,
                            screenHeight = screenH,
                            bgOrigin = bgOrigin,
                            carousel = carousel,
                            scrimAlpha = scrimAlpha,
                            modifier = Modifier.fillMaxWidth(),
                            onComplete = {
                                profile = null
                                page = 3
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // back button: terms -> welcome, name page -> terms, result page -> name page (to change the name, then retest)
                        if (page == 1 || page == 2 || page == 4) {
                            // elastic press: the glass swells and wobbles like a drop of liquid while it is held
                            val backInteraction = remember { MutableInteractionSource() }
                            val backPressed by backInteraction.collectIsPressedAsState()
                            val backLiquid by animateFloatAsState(
                                targetValue = if (backPressed) 1f else 0f,
                                animationSpec = spring(dampingRatio = 0.4f, stiffness = 110f),
                                label = "BackLiquid"
                            )
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clickable(
                                        interactionSource = backInteraction,
                                        indication = null
                                    ) { page = if (page == 4) 2 else page - 1 }
                            ) {
                                GlassPanel(
                                    modifier = Modifier.fillMaxSize(),
                                    carousel = carousel,
                                    scrimAlpha = scrimAlpha,
                                    screenWidth = screenW,
                                    screenHeight = screenH,
                                    bgOrigin = bgOrigin,
                                    liquid = backLiquid
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        ArrowIcon(forward = false, color = Color.White)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        GlassSlideButton(
                            label = when (page) {
                                0 -> "Continue"
                                1 -> "I Agree & Continue"
                                2 -> "Continue"
                                else -> "Start Camera"
                            },
                            enabled = page != 2 || deviceName.isNotBlank(),
                            screenWidth = screenW,
                            screenHeight = screenH,
                            bgOrigin = bgOrigin,
                            carousel = carousel,
                            scrimAlpha = scrimAlpha,
                            modifier = Modifier.weight(1f),
                            onComplete = {
                                when (page) {
                                    0 -> page = 1
                                    1 -> {
                                        // remember that the terms were accepted (and when)
                                        prefs.edit()
                                            .putBoolean("terms_accepted", true)
                                            .putLong("terms_accepted_at", System.currentTimeMillis())
                                            .apply()
                                        page = 2
                                    }
                                    2 -> page = 3
                                    else -> finish()
                                }
                            }
                        )
                    }
                }
            }
        }

        // "Setting up your camera UI": video background, Google-colour slider, blur-swapping bold status text, and the
        // white glowing arrow button that opens the camera. It cross-fades in on top of the whole setup page, so it also
        // blocks its touches.
        if (showLoading) {
            CameraUiLoadingScreen(
                onContinue = onFinished,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page texts
// ---------------------------------------------------------------------------------------------

@Composable
private fun WelcomeText() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Welcome to", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Camera ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("App") }
            },
            color = Color.White,
            fontSize = 46.sp,
            lineHeight = 50.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Capture sharper photos and smoother video with a camera built around liquid glass. " +
                "Zoom further, search anything you see with Gemini, and let us tune everything to match your phone.",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Terms & Conditions page
// ---------------------------------------------------------------------------------------------

private class TermsSection(val heading: String, val paragraphs: List<String>)

private val termsSections = listOf(
    TermsSection(
        "1. Acceptance of Terms",
        listOf("By accessing or using Liquid Camera Beta, you acknowledge that you have read, understood, and agreed to these Terms & Conditions. If you do not agree with these terms, please do not proceed with using the application.")
    ),
    TermsSection(
        "2. Camera Permissions",
        listOf("Liquid Camera Beta requires camera access to provide photography and video recording functionality. The application may use supported camera hardware and features available on your device to deliver its camera experience.")
    ),
    TermsSection(
        "3. Microphone Permissions",
        listOf("Microphone access may be requested when you use video recording features that include audio. You can deny this permission, but video recordings may not contain audio.")
    ),
    TermsSection(
        "4. Photos and Media Access",
        listOf("The application may request access to photos and media to save, display, and manage photographs and videos created through the app. Access will depend on the Android permissions granted and the features you use.")
    ),
    TermsSection(
        "5. Internet Connectivity",
        listOf("Certain features may require an internet connection. These may include AI-powered services, online searches, content retrieval, and other connected features. Internet availability and third-party service functionality are not guaranteed.")
    ),
    TermsSection(
        "6. Location Permissions",
        listOf("Location access may be requested if you use features that attach geographical information to photographs or videos. Location permission is not required for ordinary camera functionality unless a specific feature depends on it.")
    ),
    TermsSection(
        "7. Notifications",
        listOf("The application may request notification permission to provide relevant alerts, feature updates, and service-related information. You can manage notification access through your device settings.")
    ),
    TermsSection(
        "8. User Responsibility",
        listOf("You are responsible for using the application lawfully and respecting the privacy and rights of others. Always obtain appropriate permission before capturing or sharing photographs or videos of other people where required.")
    ),
    TermsSection(
        "9. Application Availability and Performance",
        listOf("Liquid Camera Beta is provided as a developing application. Certain features may change, become temporarily unavailable, or behave differently depending on your device hardware, Android version, and software configuration. We cannot guarantee uninterrupted or error-free operation.")
    ),
    TermsSection(
        "10. Privacy and Data Handling",
        listOf("The application should handle camera content, permissions, and other information according to its actual privacy practices. Please review the application's Privacy Policy, where available, to understand what information is collected, processed, stored, or shared.")
    ),
    TermsSection(
        "11. Updates to These Terms",
        listOf("These Terms & Conditions may be updated as the application develops. Any revised terms should be presented to users when necessary, particularly when changes materially affect their rights or responsibilities.")
    ),
    TermsSection(
        "12. Agreement and Consent",
        listOf(
            "By selecting \"I Agree & Continue\", you confirm that you have read and understood these Terms & Conditions and agree to follow them while using Liquid Camera Beta.",
            "If you do not agree, you may choose not to continue with the application."
        )
    )
)

/**
 * The agreement page: a fixed title, then separate headings and paragraphs in their own smooth scroller.
 * The top and bottom edges fade out softly. The top fade only appears once you have scrolled, and the bottom
 * fade disappears when you reach the end.
 */
@Composable
private fun TermsText(modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val fadePx = with(LocalDensity.current) { 36.dp.toPx() }
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Terms & ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Conditions") }
            },
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 38.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Liquid Camera Beta",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // fading edges: the content is drawn on its own layer, then multiplied by a vertical alpha mask
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val topFade = (scroll.value / fadePx).coerceIn(0f, 1f)
                    val bottomFade = ((scroll.maxValue - scroll.value) / fadePx).coerceIn(0f, 1f)
                    val f = (fadePx / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.45f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 1f - topFade),
                            f to Color.Black,
                            1f - f to Color.Black,
                            1f to Color.Black.copy(alpha = 1f - bottomFade),
                            startY = 0f,
                            endY = size.height
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            Column(modifier = Modifier.verticalScroll(scroll)) {
                termsSections.forEach { section ->
                    Text(
                        text = section.heading,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 21.sp
                    )
                    section.paragraphs.forEach { paragraph ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = paragraph,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
                // room at the end so the last lines can scroll clear of the bottom fade
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NameText(name: String, onNameChange: (String) -> Unit, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Name your ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("device") }
            },
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 38.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "We use it for your \"Shot on\" watermark and to look up your phone's camera.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            placeholder = { Text("Device name", color = Color.White.copy(alpha = 0.4f)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.8f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = Color.White,
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.08f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CheckingText(steps: List<String>, stepIndex: Int) {
    val progress by animateFloatAsState(
        targetValue = (stepIndex + 1f) / steps.size,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "SetupProgress"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Setting up your ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("camera") }
            },
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedContent(
            targetState = steps[stepIndex.coerceIn(0, steps.lastIndex)],
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "SetupStepText"
        ) { label ->
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        GoogleProgressBar(progress = progress)
    }
}

/** Static blue / green / yellow / red gradient (Google's colours). The fill grows; the colours do not move. */
@Composable
private fun GoogleProgressBar(progress: Float) {
    var trackWidth by remember { mutableFloatStateOf(1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .onSizeChanged { trackWidth = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(GoogleBlue, GoogleGreen, GoogleYellow, GoogleRed),
                        startX = 0f,
                        endX = trackWidth
                    )
                )
        )
    }
}

@Composable
private fun ResultText(name: String, profile: CameraSetupProfile?) {
    val zoomText = remember(profile) {
        val p = profile
        if (p == null) "Automatic" else buildList {
            add("1x")
            if (p.maxZoom >= 2f) add("2x")
            if (p.maxZoom >= 5f) add("5x")
            p.zoomPresets.forEach { add("${it}x") }
        }.joinToString("  ·  ")
    }
    val videoText = if (profile == null) "1080p · 30fps" else "${profile.videoRes} · ${profile.videoFps}fps"
    val extraVideo = buildList {
        if (profile?.supports4k == true) add("4K")
        if (profile?.supports60 == true) add("60fps")
    }.joinToString(" and ")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("All ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("set") }
            },
            color = Color.White,
            fontSize = 32.sp,
            lineHeight = 38.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your camera is ready on $name",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        GoogleProgressBar(progress = 1f)
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResultRow("Zoom buttons", zoomText)
            if (profile != null && profile.maxZoom > 1.05f) ResultRow("Maximum zoom", "${profile.maxZoom.toInt()}x")
            ResultRow("Video starts at", videoText)
            if (extraVideo.isNotEmpty()) ResultRow("Also available", extraVideo)
            if (!profile?.googleSummary.isNullOrBlank()) {
                ResultRow("From Google", profile?.googleSummary.orEmpty())
                val sites = profile?.googleSources.orEmpty()
                if (sites.isNotEmpty()) {
                    ResultRow("Websites checked", sites.joinToString("  ·  "))
                } else {
                    ResultRow("Source", "Gemini's own knowledge, because Google Search was not available. Slide Retest to search the web.")
                }
            } else if (!profile?.lookupError.isNullOrBlank()) {
                val err = profile?.lookupError.orEmpty()
                // "quota reached" is not something the person can fix, so say it plainly and point at Retest
                val reason = if (err.contains("quota", ignoreCase = true)) "Google is busy right now" else err
                val hint = if (err.contains("key", ignoreCase = true)) "" else " Slide Retest to try Google again."
                ResultRow("Camera data", "Your phone's own camera data is used. $reason.$hint")
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------------------------
// Carousel background, glass panel and the glass slide-to-continue pill
// ---------------------------------------------------------------------------------------------

/** The photos of the carousel, in this order: res/drawable/hi.jpg, hello.jpg, meow.jpg, melody.jpg */
private val SlideImages = listOf(
    com.example.R.drawable.hi,
    com.example.R.drawable.hello,
    com.example.R.drawable.meow,
    com.example.R.drawable.melody
)

/**
 * The whole scene behind the page: two neighbouring photos sliding sideways ([carousel] counts 0, 1, 2 ...; the
 * photos repeat) plus the dark shading that keeps the text readable. The glass parts draw this same scene again
 * (see BackdropCopy), so what they show behind them always matches what is really there.
 */
@Composable
private fun CarouselLayer(
    carousel: Animatable<Float, AnimationVector1D>,
    scrimAlpha: Float,
    modifier: Modifier = Modifier
) {
    val position = carousel.value
    val base = kotlin.math.floor(position).toInt()
    val frac = position - base
    val n = SlideImages.size
    Box(modifier = modifier.clipToBounds()) {
        Image(
            painter = painterResource(id = SlideImages[Math.floorMod(base, n)]),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -frac * size.width }
        )
        if (frac > 0.001f) {
            Image(
                painter = painterResource(id = SlideImages[Math.floorMod(base + 1, n)]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - frac) * size.width }
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.35f),
                        0.30f to Color.Transparent,
                        0.50f to Color.Black.copy(alpha = 0.25f),
                        1.0f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )
    }
}

/**
 * A copy of the scene, the size of the whole screen, moved so that the point (cx, cy) of the screen sits in the
 * middle of the parent, then magnified around that point. The parent must centre it (contentAlignment = Center).
 */
@Composable
private fun BackdropCopy(
    carousel: Animatable<Float, AnimationVector1D>,
    scrimAlpha: Float,
    screenWidth: Dp,
    screenHeight: Dp,
    cx: Float,
    cy: Float,
    scale: Float,
    blurRadius: Dp
) {
    val density = LocalDensity.current
    val sw = with(density) { screenWidth.toPx() }
    val sh = with(density) { screenHeight.toPx() }
    Box(
        modifier = Modifier
            .requiredSize(screenWidth, screenHeight)
            .graphicsLayer {
                translationX = sw / 2f - cx
                translationY = sh / 2f - cy
                transformOrigin = TransformOrigin((cx / sw).coerceIn(0f, 1f), (cy / sh).coerceIn(0f, 1f))
                scaleX = scale
                scaleY = scale
            }
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded) else Modifier)
    ) {
        CarouselLayer(carousel = carousel, scrimAlpha = scrimAlpha, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Draws [content] through a single colour channel (0 = red, 1 = green, 2 = blue) and ADDS the result to what is
 * already in the parent layer. Three of these at slightly different sizes give colour fringes (chromatic
 * aberration), the way real glass splits light.
 */
@Composable
private fun ChannelLayer(
    channel: Int,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val matrix = remember(channel) {
        val m = FloatArray(20)
        m[channel * 5 + channel] = 1f   // keep only this colour
        m[18] = 1f                      // keep alpha
        ColorMatrix(m)
    }
    Box(
        modifier = modifier.drawWithContent {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    colorFilter = ColorFilter.colorMatrix(matrix)
                    blendMode = BlendMode.Plus
                }
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                drawContent()
                canvas.restore()
            }
        },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The scene (and optionally an [overlay], such as the pill's label) seen through glass with colour fringes:
 * red, green and blue are magnified by slightly different amounts. The parent must have a fixed size.
 */
@Composable
private fun AberrationBackdrop(
    modifier: Modifier,
    carousel: Animatable<Float, AnimationVector1D>,
    scrimAlpha: Float,
    screenWidth: Dp,
    screenHeight: Dp,
    cx: Float,
    cy: Float,
    scaleR: Float,
    scaleG: Float,
    scaleB: Float,
    overlay: (@Composable (Float) -> Unit)? = null
) {
    Box(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        listOf(0 to scaleR, 1 to scaleG, 2 to scaleB).forEach { (channel, sc) ->
            ChannelLayer(channel = channel, modifier = Modifier.fillMaxSize()) {
                BackdropCopy(carousel, scrimAlpha, screenWidth, screenHeight, cx, cy, sc, 0.dp)
                overlay?.invoke(sc)
            }
        }
    }
}

/** A drawn arrow, exactly centred (a text arrow never sits in the middle). A faint dark shadow keeps it readable on photos. */
@Composable
private fun ArrowIcon(forward: Boolean, color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val dir = if (forward) 1f else -1f
        val tail = w / 2f - dir * w * 0.34f
        val tip = w / 2f + dir * w * 0.34f
        val head = w * 0.24f
        fun arrow(c: Color, stroke: Float) {
            drawLine(c, Offset(tail, cy), Offset(tip, cy), stroke, StrokeCap.Round)
            drawLine(c, Offset(tip - dir * head, cy - head), Offset(tip, cy), stroke, StrokeCap.Round)
            drawLine(c, Offset(tip - dir * head, cy + head), Offset(tip, cy), stroke, StrokeCap.Round)
        }
        arrow(Color.Black.copy(alpha = 0.30f), 4.2.dp.toPx())
        arrow(color, 2.2.dp.toPx())
    }
}

// ---------------------------------------------------------------------------------------------
// Liquid glass
//   Android 13+ : one AGSL shader per glass shape. It does real refraction (the rim bends the scene like the
//                 edge of a thick convex lens), colour dispersion, saturation, depth shading, a two-sided rim
//                 light and a soft drop shadow. The shape itself is drawn by the shader (no clip needed).
//   Older phones: the layered red / green / blue copy of the scene (the previous look).
// To change the look, edit PanelLook (buttons and pill) and LensLook (the held thumb) below.
// ---------------------------------------------------------------------------------------------

/** Extra room around every glass shape: the shader draws the shadow and reads the scene outside the rim here. */
private val GlassMargin = 32.dp

/**
 * @param bevel        width of the rim where light bends (bigger = thicker, more "glassy" edge)
 * @param refraction   how far the rim pulls the scene in from outside the shape (0 = flat glass, 1 = strong)
 * @param magnify      zoom of the flat middle of the glass (1 = none)
 * @param dispersion   colour split at the rim: red, green and blue bend by slightly different amounts
 * @param saturation   colour boost of whatever is seen through the glass (1 = unchanged)
 * @param tint         colour laid over the glass; its alpha is how much
 * @param frost        small blur of the scene behind the glass (0 = perfectly sharp)
 * @param specular     brightness of the rim light
 * @param innerShadow  darkening of the rim on the side away from the light (gives volume)
 * @param dropShadow   strength of the soft shadow under the glass
 * @param shadowRadius how far that shadow spreads
 * @param body        lens curvature across the whole glass: the middle is magnified more than the edges, so text
 *                    under the glass bends and swells like it does under a real glass bead (0 = flat)
 */
private data class LiquidGlassLook(
    val bevel: Dp,
    val refraction: Float,
    val magnify: Float,
    val dispersion: Float,
    val saturation: Float,
    val tint: Color,
    val frost: Dp,
    val specular: Float,
    val innerShadow: Float,
    val dropShadow: Float,
    val shadowRadius: Dp,
    val body: Float = 0f
)

private val PanelLook = LiquidGlassLook(
    bevel = 16.dp,
    refraction = 0.85f,
    magnify = 1.03f,
    dispersion = 0.18f,
    saturation = 1.35f,
    tint = Color.White.copy(alpha = 0.06f),
    frost = 1.5.dp,
    specular = 0.80f,
    innerShadow = 0.20f,
    dropShadow = 0.24f,
    shadowRadius = 10.dp
)

private val LensLook = LiquidGlassLook(
    bevel = 22.dp,
    refraction = 0.90f,
    magnify = 1.0f,
    dispersion = 0.38f,
    saturation = 1.25f,
    tint = Color.White.copy(alpha = 0.03f),
    frost = 0.dp,
    specular = 0.90f,
    innerShadow = 0.18f,
    dropShadow = 0.30f,
    shadowRadius = 12.dp,
    body = 0.10f
)

/**
 * The glass shader. Its input "content" is the scene (plus, for the lens, the pill's label), drawn at normal size
 * and centred on the glass. For every pixel it:
 *  1. works out the distance to the glass edge (a rounded-box distance field) and the edge's outward direction,
 *  2. near the edge slides the place it reads the scene from OUTWARDS, so the rim shows squeezed-in content from
 *     beyond the shape, exactly like a thick convex lens (red / green / blue slide by slightly different amounts),
 *  3. boosts saturation, adds a faint tint, shades the side away from the light and adds a two-sided rim light,
 *  4. outside the shape it only draws the soft drop shadow.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 uLayer;     // layer size in px (glass + margin on every side)
uniform float2 uHalf;      // half width / half height of the glass shape in px
uniform float uCorner;     // corner radius in px (min of uHalf = pill or circle)
uniform float uBevel;      // rim width in px
uniform float uRefract;    // rim bend strength
uniform float uMagnify;    // zoom of the middle
uniform float uBody;       // lens curvature over the whole body (middle magnified more than the edges)
uniform float uDisp;       // colour dispersion
uniform float uSat;        // saturation
uniform float4 uTint;      // rgb + amount
uniform float uSpec;       // rim light strength
uniform float uInner;      // inner shadow strength
uniform float uDrop;       // drop shadow strength
uniform float uShadow;     // drop shadow radius in px
uniform float4 uClip;      // safe area to read the scene from: left, top, right, bottom
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

    // soft drop shadow, a little lower than the glass
    float sd = sdBox(p - float2(0.0, uShadow * 0.25), uHalf, uCorner);
    float sh = (1.0 - smoothstep(-uShadow * 0.2, uShadow, sd)) * uDrop;
    half4 shadow = half4(0.0, 0.0, 0.0, half(sh));
    if (cover <= 0.0) {
        return shadow;
    }

    float inside = max(-d, 0.0);                   // distance from the edge, inwards
    float2 n = sdNormal(p, uHalf, uCorner);        // outward direction of the nearest edge

    // Glass-bead rim: at the very edge the scene is read from INSIDE the shape and the reading point moves slower
    // than the screen point (it even reverses a little right at the edge). That stretches whatever is under the rim
    // into smears and streaks, the way real thick glass does, and fades to a flat middle.
    float k = 1.0 - clamp(inside / uBevel, 0.0, 1.0);
    float disp = uRefract * uBevel * 0.55 * k * k;

    // where each colour channel reads the scene: outwards at the rim, slightly zoomed in the middle
    float2 qn = p / max(uHalf, float2(1.0));
    float r2 = clamp(dot(qn, qn), 0.0, 1.0);
    float zoom = 1.0 + uBody * (1.0 - r2) * (1.0 - r2 * 0.5);   // smooth bulge: strongest in the middle, none at the rim
    float2 base = p / (uMagnify * zoom);
    float2 uvR = c + base * (1.0 - 0.04 * uDisp) - n * disp * (1.0 - uDisp);
    float2 uvG = c + base - n * disp;
    float2 uvB = c + base * (1.0 + 0.04 * uDisp) - n * disp * (1.0 + uDisp);
    float3 col = float3(
        content.eval(clamp(uvR, uClip.xy, uClip.zw)).r,
        content.eval(clamp(uvG, uClip.xy, uClip.zw)).g,
        content.eval(clamp(uvB, uClip.xy, uClip.zw)).b
    );

    // colour: richer and very slightly lifted
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

    // rim light: a thin bright line plus a soft glow, strong on the lit side and weaker on the opposite side
    float thin = 1.0 - smoothstep(0.0, 1.7 * uPx, inside);
    float soft = 1.0 - smoothstep(0.0, uBevel * 0.6, inside);
    float side = pow(toward, 1.5) + 0.55 * pow(away, 1.5);
    col += uSpec * (thin * (0.16 + 0.95 * side) + soft * 0.22 * side);
    col = clamp(col, 0.0, 1.0);

    half4 glass = half4(half3(col), 1.0);
    return glass * half(cover) + shadow * half(1.0 - cover);
}
"""

/**
 * The scene seen through liquid glass (Android 13+). The parent must give it a fixed size = the glass size. It is
 * drawn inside a layer that is [GlassMargin] bigger on every side, so the rim can read content from outside the
 * shape and the shadow has room; the shader draws the shape itself, so no clip is needed.
 *
 * [bulgeX] / [bulgeY] grow the shape (1 = normal size): used for the wobbling "drop of liquid" swell.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiquidGlassBackdrop(
    modifier: Modifier,
    carousel: Animatable<Float, AnimationVector1D>,
    scrimAlpha: Float,
    screenWidth: Dp,
    screenHeight: Dp,
    cx: Float,
    cy: Float,
    look: LiquidGlassLook,
    bulgeX: Float = 1f,
    bulgeY: Float = 1f,
    overlay: (@Composable (Float) -> Unit)? = null
) {
    val density = LocalDensity.current
    val marginInt = with(density) { GlassMargin.toPx() }.roundToInt()
    val marginPx = marginInt.toFloat()
    val screenW = with(density) { screenWidth.toPx() }
    val screenH = with(density) { screenHeight.toPx() }
    val dpPx = with(density) { 1.dp.toPx() }
    val shader = remember { RuntimeShader(LIQUID_GLASS_AGSL) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                // the layer is bigger than the glass by the margin, but reports the glass size to its parent
                .layout { measurable, constraints ->
                    val w = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
                    val h = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
                    val placeable = measurable.measure(Constraints.fixed(w + 2 * marginInt, h + 2 * marginInt))
                    layout(w, h) { placeable.place(-marginInt, -marginInt) }
                }
                .graphicsLayer {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@graphicsLayer
                    val hx = (w / 2f - marginPx) * bulgeX
                    val hy = (h / 2f - marginPx) * bulgeY
                    val bevelPx = minOf(look.bevel.toPx(), minOf(hx, hy) * 0.9f)

                    // the scene copy is centred on the glass, so the screen sits at (w/2 - cx, h/2 - cy) in this layer
                    val screenLeft = w / 2f - cx
                    val screenTop = h / 2f - cy

                    shader.setFloatUniform("uLayer", w, h)
                    shader.setFloatUniform("uHalf", hx, hy)
                    shader.setFloatUniform("uCorner", minOf(hx, hy))
                    shader.setFloatUniform("uBevel", maxOf(bevelPx, 1f))
                    shader.setFloatUniform("uRefract", look.refraction)
                    shader.setFloatUniform("uMagnify", look.magnify)
                    shader.setFloatUniform("uBody", look.body)
                    shader.setFloatUniform("uDisp", look.dispersion)
                    shader.setFloatUniform("uSat", look.saturation)
                    shader.setFloatUniform("uTint", look.tint.red, look.tint.green, look.tint.blue, look.tint.alpha)
                    shader.setFloatUniform("uSpec", look.specular)
                    shader.setFloatUniform("uInner", look.innerShadow)
                    shader.setFloatUniform("uDrop", look.dropShadow)
                    shader.setFloatUniform("uShadow", look.shadowRadius.toPx())
                    shader.setFloatUniform(
                        "uClip",
                        maxOf(0.5f, screenLeft),
                        maxOf(0.5f, screenTop),
                        minOf(w - 0.5f, screenLeft + screenW),
                        minOf(h - 0.5f, screenTop + screenH)
                    )
                    shader.setFloatUniform("uLight", -0.55f, -0.835f)   // light comes from the top left
                    shader.setFloatUniform("uPx", dpPx)

                    val bend = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                    val frost = look.frost.toPx()
                    renderEffect = (
                        if (frost >= 0.5f) {
                            android.graphics.RenderEffect.createChainEffect(
                                bend,
                                android.graphics.RenderEffect.createBlurEffect(
                                    frost, frost, android.graphics.Shader.TileMode.CLAMP
                                )
                            )
                        } else {
                            bend
                        }
                        ).asComposeRenderEffect()
                },
            contentAlignment = Alignment.Center
        ) {
            BackdropCopy(carousel, scrimAlpha, screenWidth, screenHeight, cx, cy, 1f, 0.dp)
            overlay?.invoke(1f)
        }
    }
}

/**
 * Liquid glass for the buttons and the pill. [liquid] (0 = resting, 1 = held) swells the glass like a drop of
 * liquid and bends the scene harder; feed it from a spring and it wobbles and settles.
 */
@Composable
private fun GlassPanel(
    modifier: Modifier,
    carousel: Animatable<Float, AnimationVector1D>,
    scrimAlpha: Float,
    screenWidth: Dp,
    screenHeight: Dp,
    bgOrigin: Offset,
    liquid: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(Offset.Zero) }
    // liquid: 0 = resting, 1 = held. It may swing a little past those values while the spring bounces.
    val l = liquid.coerceIn(-0.25f, 1.4f)
    val lPos = l.coerceAtLeast(0f)
    val cx = rootPos.x - bgOrigin.x + sizePx.x / 2f
    val cy = rootPos.y - bgOrigin.y + sizePx.y / 2f

    Box(
        modifier = modifier.onGloballyPositioned {
            rootPos = it.positionInRoot()
            sizePx = Offset(it.size.width.toFloat(), it.size.height.toFloat())
        }
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LiquidGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                carousel = carousel,
                scrimAlpha = scrimAlpha,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                cx = cx,
                cy = cy,
                look = PanelLook.copy(
                    refraction = PanelLook.refraction + 0.30f * lPos,
                    dispersion = PanelLook.dispersion + 0.10f * lPos,
                    specular = PanelLook.specular + 0.25f * lPos
                ),
                bulgeX = 1f + 0.03f * l,
                bulgeY = 1f + 0.16f * l
            )
        } else {
            // Android 12 and older: the previous glass (magnified red / green / blue copies of the scene)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1f + 0.025f * l
                        scaleY = 1f + 0.16f * l
                    }
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AberrationBackdrop(
                        modifier = Modifier.fillMaxSize(),
                        carousel = carousel,
                        scrimAlpha = scrimAlpha,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        cx = cx,
                        cy = cy,
                        scaleR = 1.02f + 0.03f * lPos,
                        scaleG = 1.03f + 0.04f * lPos,
                        scaleB = 1.04f + 0.05f * lPos
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.22f), shape = CircleShape)
                )
            }
        }
        content()
    }
}

/** The text inside the pill: the label and the three small chevrons. */
@Composable
private fun PillLabel(label: String, alpha: Float, startPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = startPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = "\u203A \u203A \u203A",
            color = Color.White.copy(alpha = alpha * 0.6f),
            fontSize = 15.sp
        )
    }
}

/**
 * A liquid glass pill. Slide the round thumb to the right to continue.
 * While the pill is held the thumb turns into a glass lens (the arrow stays). The lens is elastic: it swells with a
 * slow bounce, stretches along the drag like a drop of liquid (the thumb follows the finger on a spring, so it trails
 * behind, overshoots and wobbles) and bounces back on release. Under the lens the photo AND the pill's text are
 * refracted, magnified and split into colour fringes. The lens is drawn outside the pill, so it is never cut off.
 */
@Composable
private fun GlassSlideButton(
    label: String,
    enabled: Boolean,
    screenWidth: Dp,
    screenHeight: Dp,
    bgOrigin: Offset,
    carousel: Animatable<Float, AnimationVector1D>,
    scrimAlpha: Float,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val thumbSize = 48.dp
    val pad = 6.dp
    val trackHeight = 60.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val padPx = with(density) { pad.toPx() }
    val trackHeightPx = with(density) { trackHeight.toPx() }

    var trackW by remember { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    var trackRoot by remember { mutableStateOf(Offset.Zero) }

    val maxOffset = (trackW - thumbPx - 2f * padPx).coerceAtLeast(1f)
    val maxOffsetState = rememberUpdatedState(maxOffset)
    val enabledState = rememberUpdatedState(enabled)
    val onCompleteState = rememberUpdatedState(onComplete)
    val progress = (dragX / maxOffset).coerceIn(0f, 1f)
    // the label stays fully visible while sliding (it no longer fades away); only a disabled button is dimmed
    val labelAlpha = if (enabled) 1f else 0.4f

    // Slow and bouncy: on hold the lens swells past full size, wobbles and settles; on release it bounces back.
    val lens by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 110f),
        label = "SlideLens"
    )
    // the spring swings a little past 0..1: opacity and visibility use the clamped value, the bounce uses the raw one
    val lensVis = lens.coerceIn(0f, 1f)
    val lensPos = lens.coerceAtLeast(0f)
    // the held thumb is this much bigger than the resting one
    val grow = 1f + 0.50f * lens.coerceAtLeast(-0.1f)

    // Elastic follow: the thumb chases the finger on a springy rope. The gap between finger and thumb is what
    // stretches the lens, and because the spring overshoots, the lens wobbles when you stop or let go.
    val follow by animateFloatAsState(
        targetValue = dragX,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 330f),
        label = "SlideFollow"
    )
    val thumbX = follow.coerceIn(0f, maxOffset)
    val stretch = (abs(dragX - follow) / thumbPx).coerceAtMost(0.55f)
    val stretchX = 1f + 0.55f * stretch         // longer along the drag (a bit, max about 1.3x) ...
    val squashY = 1f / sqrt(stretchX)           // ... and thinner across it, so the volume stays about the same
    val lensW = thumbSize * (grow * stretchX)
    val lensH = thumbSize * (grow * squashY)

    // centre of the lens in screen coordinates, worked out directly so the glass never lags a frame behind the thumb
    val cx = trackRoot.x - bgOrigin.x + padPx + thumbX + thumbPx / 2f
    val cy = trackRoot.y - bgOrigin.y + trackHeightPx / 2f

    // no clip here: the held thumb is bigger than the pill and must be able to grow past its edge
    Box(
        modifier = modifier
            .height(trackHeight)
            .onSizeChanged { trackW = it.width.toFloat() }
            .onGloballyPositioned { trackRoot = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!enabledState.value) {
                        return@awaitEachGesture
                    }
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    var tracking = true
                    while (tracking) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            tracking = false
                        } else {
                            val dx = change.positionChange().x
                            if (dx != 0f) {
                                change.consume()
                                dragX = (dragX + dx).coerceIn(0f, maxOffsetState.value)
                            }
                        }
                    }
                    pressed = false
                    val reached = dragX >= maxOffsetState.value * 0.82f
                    val from = dragX
                    scope.launch {
                        if (reached) {
                            animate(from, maxOffsetState.value, animationSpec = tween(120)) { v, _ -> dragX = v }
                            onCompleteState.value()
                            delay(300)
                            animate(dragX, 0f, animationSpec = spring(stiffness = Spring.StiffnessLow)) { v, _ -> dragX = v }
                        } else {
                            animate(
                                from, 0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                            ) { v, _ -> dragX = v }
                        }
                    }
                }
            }
    ) {
        GlassPanel(
            modifier = Modifier.fillMaxSize(),
            carousel = carousel,
            scrimAlpha = scrimAlpha,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            bgOrigin = bgOrigin,
            liquid = lens           // the pill bulges and bends the scene more while held, bouncing with the lens
        ) {
            PillLabel(label = label, alpha = labelAlpha, startPadding = thumbSize + pad * 2)
        }

        // the pill's text, drawn again at pill size and moved so it lines up with the real one (used inside the lens)
        val trackDp = with(density) { trackW.toDp() }
        val labelCopy: @Composable (Float) -> Unit = { sc ->
            Box(
                modifier = Modifier
                    .requiredSize(trackDp, trackHeight)
                    .graphicsLayer {
                        val lensCx = padPx + thumbX + thumbPx / 2f
                        translationX = trackW / 2f - lensCx
                        transformOrigin = TransformOrigin((lensCx / trackW.coerceAtLeast(1f)).coerceIn(0f, 1f), 0.5f)
                        scaleX = sc
                        scaleY = sc
                    }
            ) {
                PillLabel(label = label, alpha = labelAlpha, startPadding = thumbSize + pad * 2)
            }
        }

        // the thumb (outside the clipped glass)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = pad)
                .offset { IntOffset(thumbX.roundToInt(), 0) }
                .size(thumbSize),
            contentAlignment = Alignment.Center
        ) {
            // resting thumb: dark disc
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 1f - lensVis
                        scaleX = grow
                        scaleY = grow
                    }
                    .clip(CircleShape)
                    .background(Color(0xFF16161A))
            )

            // held thumb: an elastic liquid glass lens that refracts the scene and the pill's text
            if (lensVis > 0.01f) {
                Box(
                    modifier = Modifier
                        .requiredSize(lensW, lensH)
                        .graphicsLayer { alpha = lensVis }
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        LiquidGlassBackdrop(
                            modifier = Modifier.fillMaxSize(),
                            carousel = carousel,
                            scrimAlpha = scrimAlpha,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                            cx = cx,
                            cy = cy,
                            // the bouncy lens builds up the bend, the zoom, the colour fringes and the rim light
                            look = LensLook.copy(
                                refraction = LensLook.refraction + 0.20f * lensPos,
                                magnify = 1f + 0.08f * lensPos,
                                body = LensLook.body + 0.08f * lensPos,
                                dispersion = LensLook.dispersion + 0.12f * lensPos,
                                specular = LensLook.specular + 0.20f * lensPos
                            ),
                            overlay = labelCopy
                        )
                    } else {
                        // Android 12 and older: the previous lens (1.2x / 1.32x / 1.44x red / green / blue copies)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.30f), shape = CircleShape)
                        ) {
                            AberrationBackdrop(
                                modifier = Modifier.fillMaxSize(),
                                carousel = carousel,
                                scrimAlpha = scrimAlpha,
                                screenWidth = screenWidth,
                                screenHeight = screenHeight,
                                cx = cx,
                                cy = cy,
                                scaleR = 1f + 0.20f * lensPos,
                                scaleG = 1f + 0.32f * lensPos,
                                scaleB = 1f + 0.44f * lensPos,
                                overlay = labelCopy
                            )
                        }
                    }
                }
            }

            // the arrow always stays, resting or held
            ArrowIcon(
                forward = true,
                color = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = grow
                        scaleY = grow
                    }
            )
        }
    }
}