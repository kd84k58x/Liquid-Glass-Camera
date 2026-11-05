package com.example.ai

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException

/**
 * What the phone itself reports about its rear camera (Camera2 + CamcorderProfile). These facts are sent to
 * Gemini so it knows exactly which phone it is researching, and they are also the final word on what the app
 * is allowed to switch on: Gemini can recommend, but the hardware decides.
 */
data class DeviceCameraFacts(
    val maxZoom: Float,
    val minZoom: Float,
    val supports60: Boolean,
    val has720: Boolean,
    val has1080: Boolean,
    val has2160: Boolean,
    val has8k: Boolean,
    val focalLengths: List<Float>,
    val pixelArray: String,
    val sensorSizeMm: String,
    val physicalCameraCount: Int,
    val hardwareLevel: String,
    val opticalStabilization: Boolean
)

/** What Gemini found out about the phone's camera. A value of null means "Gemini was not sure". */
data class GeminiCameraInfo(
    val phoneName: String,
    val confident: Boolean,
    val cameraSummary: String,
    val opticalZoomMax: Float,
    val claimedMaxZoom: Float,
    val video4k30: Boolean?,
    val video4k60: Boolean?,
    val video8k: Boolean?,
    val video1080p60: Boolean?,
    val recommendedRes: String,
    val recommendedFps: String,
    val zoomButtons: List<Int>,
    val note: String,
    /** true = Gemini used Google Search for this answer, false = it answered from its own knowledge only */
    val grounded: Boolean,
    val modelUsed: String
)

/** Either [info] (success) or [error] (a short, human readable reason). */
data class GeminiCameraResult(val info: GeminiCameraInfo?, val error: String?)

/**
 * Asks Gemini (with Google Search turned on) about this phone's camera hardware: what the sensors are, whether it
 * records 4K / 4K60 / 8K, how far its zoom is really usable, and which zoom buttons and video quality make sense.
 *
 * It talks to the Gemini REST API directly with HttpURLConnection and org.json, so it needs no extra library.
 * It needs the INTERNET permission (the Search mode already needs it) and a Gemini API key, see [findApiKey].
 */
object GeminiCameraSetupService {

    /**
     * If your app already keeps the Gemini key somewhere (the one GeminiSearchService uses), plug it in here once,
     * for example in your Application class or in MainActivity:
     *
     *     GeminiCameraSetupService.apiKeyProvider = { MyKeys.GEMINI }
     *
     * Without it the key is looked up as described in [findApiKey].
     */
    @Volatile
    var apiKeyProvider: ((Context) -> String?)? = null

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    private const val TOTAL_BUDGET_MS = 45_000L

    /**
     * Where the API key comes from, first match wins:
     * 1. [apiKeyProvider]
     * 2. the preference "gemini_api_key" in "camera_prefs"
     * 3. a BuildConfig field named GEMINI_API_KEY (or GEMINI_KEY / API_KEY) of the app
     */
    fun findApiKey(context: Context): String? {
        try {
            apiKeyProvider?.invoke(context)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE)
                .getString("gemini_api_key", null)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val classNames = listOf(
            context.packageName + ".BuildConfig",
            "com.example.BuildConfig",
            "com.example.ai.BuildConfig",
            "com.example.ui.BuildConfig"
        ).distinct()
        val fieldNames = listOf("GEMINI_API_KEY", "GEMINI_KEY", "API_KEY")
        for (className in classNames) {
            val cls = try { Class.forName(className) } catch (e: Throwable) { continue }
            for (fieldName in fieldNames) {
                try {
                    val value = cls.getField(fieldName).get(null) as? String
                    if (!value.isNullOrBlank()) return value.trim()
                } catch (e: Throwable) {
                    // field not there, try the next one
                }
            }
        }
        return null
    }

    /**
     * Researches this phone. Never throws: every failure comes back as [GeminiCameraResult.error], so the caller can
     * fall back to what the hardware reports.
     */
    suspend fun research(context: Context, facts: DeviceCameraFacts): GeminiCameraResult =
        withContext(Dispatchers.IO) {
            val apiKey = findApiKey(context)
                ?: return@withContext GeminiCameraResult(null, "No Gemini API key found")

            val prompt = buildPrompt(facts)
            val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS

            // The model picked in Search mode first, then two safe defaults.
            val savedModel = try {
                context.getSharedPreferences("camera_prefs", Context.MODE_PRIVATE).getString("gemini_model", null)
            } catch (e: Exception) {
                null
            }
            val models = listOfNotNull(
                savedModel?.trim()?.takeIf { it.startsWith("gemini") },
                "gemini-2.5-flash",
                "gemini-flash-latest"
            ).distinct()

            var lastError = "Gemini did not answer"
            for (model in models) {
                // First with Google Search (real, current information), then without it if the model refuses the tool.
                for (grounded in listOf(true, false)) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining < 3_000L) return@withContext GeminiCameraResult(null, "Gemini took too long")

                    val body = buildRequestBody(prompt, grounded)
                    val response = try {
                        post(
                            url = BASE_URL + model + ":generateContent",
                            apiKey = apiKey,
                            body = body,
                            readTimeoutMs = minOf(30_000L, remaining).toInt()
                        )
                    } catch (e: UnknownHostException) {
                        return@withContext GeminiCameraResult(null, "No internet connection")
                    } catch (e: SocketTimeoutException) {
                        lastError = "Gemini took too long"
                        break                                   // next model
                    } catch (e: Exception) {
                        lastError = "Could not reach Gemini"
                        break
                    }

                    when (response.first) {
                        in 200..299 -> {
                            val info = parseResponse(response.second, grounded, model)
                            if (info != null) return@withContext GeminiCameraResult(info, null)
                            lastError = "Gemini gave an unexpected answer"
                            // try again without search / with the next model
                        }
                        400 -> {
                            lastError = if (response.second.contains("API key", ignoreCase = true)) {
                                return@withContext GeminiCameraResult(null, "The Gemini API key was rejected")
                            } else {
                                "Gemini rejected the request"
                            }
                            // most likely the Search tool is not allowed for this model: retry without it
                        }
                        401 -> return@withContext GeminiCameraResult(null, "The Gemini API key was rejected")
                        403 -> {
                            lastError = "The Gemini API key is not allowed to do this"
                            // Search grounding can be blocked on its own: retry without it
                        }
                        404 -> {
                            lastError = "That Gemini model is not available"
                            break                               // next model
                        }
                        429 -> {
                            lastError = "Gemini is busy (quota reached)"
                            break
                        }
                        else -> {
                            lastError = "Gemini error ${response.first}"
                            break
                        }
                    }
                }
            }
            GeminiCameraResult(null, lastError)
        }

    // ---------------------------------------------------------------------------------------------------------

    private fun buildPrompt(f: DeviceCameraFacts): String = buildString {
        appendLine("Research this Android phone's REAR camera hardware. Use Google Search to find its exact marketing name and its official camera and video specifications. Do not guess: if you cannot find the exact phone, say so with \"confident\": false and use only what the facts below allow.")
        appendLine()
        appendLine("PHONE (from Android Build):")
        appendLine("- manufacturer: ${Build.MANUFACTURER}")
        appendLine("- brand: ${Build.BRAND}")
        appendLine("- model: ${Build.MODEL}")
        appendLine("- device codename: ${Build.DEVICE}")
        appendLine("- product: ${Build.PRODUCT}")
        if (Build.VERSION.SDK_INT >= 31) {
            appendLine("- chipset: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        }
        appendLine("- Android API level: ${Build.VERSION.SDK_INT}")
        appendLine()
        appendLine("WHAT THE PHONE REPORTS ABOUT ITS REAR CAMERA (Camera2 / CamcorderProfile):")
        appendLine("- zoom ratio range reported to apps: ${"%.1f".format(f.minZoom)}x to ${"%.1f".format(f.maxZoom)}x")
        appendLine("- focal lengths (mm): ${if (f.focalLengths.isEmpty()) "unknown" else f.focalLengths.joinToString(", ") { "%.2f".format(it) }}")
        appendLine("- sensor pixel array: ${f.pixelArray}, physical size: ${f.sensorSizeMm}")
        appendLine("- physical rear cameras behind the main camera id: ${f.physicalCameraCount}")
        appendLine("- camera2 hardware level: ${f.hardwareLevel}, optical stabilization: ${f.opticalStabilization}")
        appendLine("- video profiles offered to apps: 720p=${f.has720}, 1080p=${f.has1080}, 4K=${f.has2160}, 8K=${f.has8k}")
        appendLine("- frame rate range up to 60 fps offered to apps: ${f.supports60}")
        appendLine()
        appendLine("Answer with ONE JSON object and nothing else (no markdown, no code fences, no comments). Keys:")
        appendLine("{")
        appendLine("  \"marketing_name\": string, the phone's real market name,")
        appendLine("  \"confident\": boolean, true only if you found this exact phone,")
        appendLine("  \"camera_summary\": string, at most 110 characters, e.g. \"50 MP main, 12 MP ultrawide, 10 MP 3x telephoto\",")
        appendLine("  \"optical_zoom_max\": number, the longest OPTICAL zoom (1 if there is no telephoto),")
        appendLine("  \"claimed_max_zoom\": number, the maximum zoom the maker advertises (digital / hybrid / AI),")
        appendLine("  \"video_4k_30\": boolean or null, records 4K at 30 fps,")
        appendLine("  \"video_4k_60\": boolean or null, records 4K at 60 fps,")
        appendLine("  \"video_8k\": boolean or null,")
        appendLine("  \"video_1080p_60\": boolean or null,")
        appendLine("  \"recommended_video_resolution\": \"720p\" or \"1080p\" or \"4K\", the best quality this phone records reliably,")
        appendLine("  \"recommended_video_fps\": \"30\" or \"60\", use 60 only if the phone records that resolution at 60 fps,")
        appendLine("  \"recommended_zoom_buttons\": array of up to 4 whole numbers above 5, the far zoom levels worth a button (for example [10, 20, 30]). Only levels up to the highest zoom that still gives a usable picture on this phone, and never above ${"%.0f".format(f.maxZoom)},")
        appendLine("  \"note\": string, at most 120 characters, one useful remark about this phone's camera")
        appendLine("}")
        appendLine("Use null for any boolean you could not verify. Numbers must be plain numbers.")
    }

    private fun buildRequestBody(prompt: String, grounded: Boolean): String {
        val root = JSONObject()
        root.put(
            "contents",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )
        )
        root.put(
            "generationConfig",
            JSONObject().put("temperature", 0.1)
        )
        if (grounded) {
            // Google Search grounding. (It cannot be combined with a forced JSON mime type, which is why the
            // prompt asks for JSON and parseResponse() pulls the object out of the text.)
            root.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }
        return root.toString()
    }

    private fun post(url: String, apiKey: String, body: String, readTimeoutMs: Int): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun parseResponse(raw: String, grounded: Boolean, model: String): GeminiCameraInfo? {
        try {
            val parts = JSONObject(raw)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return null
            val text = buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    if (p.has("text")) append(p.optString("text"))
                }
            }
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val o = JSONObject(text.substring(start, end + 1))

            fun bool(key: String): Boolean? = if (o.has(key) && !o.isNull(key)) o.optBoolean(key) else null
            val buttons = ArrayList<Int>()
            o.optJSONArray("recommended_zoom_buttons")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.optDouble(i, 0.0)
                    if (v > 0.0) buttons.add(v.toInt())
                }
            }
            val name = o.optString("marketing_name", "").trim()
            return GeminiCameraInfo(
                phoneName = if (name.equals("null", true)) "" else name,
                confident = o.optBoolean("confident", false),
                cameraSummary = o.optString("camera_summary", "").trim().take(140),
                opticalZoomMax = o.optDouble("optical_zoom_max", 0.0).toFloat(),
                claimedMaxZoom = o.optDouble("claimed_max_zoom", 0.0).toFloat(),
                video4k30 = bool("video_4k_30"),
                video4k60 = bool("video_4k_60"),
                video8k = bool("video_8k"),
                video1080p60 = bool("video_1080p_60"),
                recommendedRes = o.optString("recommended_video_resolution", "").trim(),
                recommendedFps = o.optString("recommended_video_fps", "").trim(),
                zoomButtons = buttons,
                note = o.optString("note", "").trim().take(160),
                grounded = grounded,
                modelUsed = model
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
