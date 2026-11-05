package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GeminiSearchResult(
    val title: String,
    val category: String,
    val summary: String,
    val keyPoints: List<String>,
    val webSearchQueries: List<String>,
    val fullMarkdown: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object GeminiSearchService {
    private const val TAG = "GeminiSearchService"

    // >>> PASTE YOUR GEMINI API KEY BETWEEN THE QUOTES BELOW (get one at https://aistudio.google.com/apikey) <<<
    // Do not share this file or upload it to GitHub once your key is inside.
    private const val MANUAL_API_KEY = ""
    // Strictly gemini-3.6-flash per user request
    private const val MODEL_NAME = "gemini-3.6-flash"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    /** The key actually used: the Gradle/BuildConfig key when it is real, otherwise MANUAL_API_KEY. */
    fun resolveApiKey(): String {
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        return if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey.trim() else MANUAL_API_KEY.trim()
    }

    /**
     * Every Gemini API key (the older "AIza..." keys and the newer "AQ...." auth keys that Google AI Studio
     * now creates) works on the same Gemini API address. The key is sent in the x-goog-api-key header,
     * never in the URL.
     */
    @Suppress("UNUSED_PARAMETER")
    fun requestUrl(model: String, key: String): String =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT =
        "You are the answer engine inside a camera app. Look at the photo and do exactly what the user asks. " +
        "Answer the request directly and concisely, like a knowledgeable friend. Never repeat these instructions, " +
        "never describe a response format and never echo field names such as TITLE or SUMMARY. " +
        "title: a short plain name for the subject. category: one or two words. " +
        "summary: the direct answer to the request in one to three short sentences. " +
        "key_points: two to four short extra facts, no bullet characters. " +
        "web_searches: two or three useful web search queries."

    private fun responseSchema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        fun strList() = JSONObject().put("type", "ARRAY").put("items", str())
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject()
                .put("title", str())
                .put("category", str())
                .put("summary", str())
                .put("key_points", strList())
                .put("web_searches", strList()))
            .put("required", JSONArray(listOf("title", "summary")))
    }

    private fun buildPayload(model: String, base64Data: String, userText: String): JSONObject = JSONObject().apply {
        put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", base64Data)))
            .put(JSONObject().put("text", userText)))))
        put("generationConfig", JSONObject().apply {
            put("temperature", 0.3)
            put("maxOutputTokens", 900)
            put("responseMimeType", "application/json")
            put("responseSchema", responseSchema())
            // no hidden "thinking" time on the 2.5 models: this is the biggest speed-up
            if (model.contains("2.5-flash")) {
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
        })
    }

    private fun failure(title: String, summary: String, error: String) = GeminiSearchResult(
        title = title,
        category = "Search",
        summary = summary,
        keyPoints = emptyList(),
        webSearchQueries = emptyList(),
        fullMarkdown = summary,
        isSuccess = false,
        errorMessage = error
    )

    suspend fun searchWithImage(
        bitmap: Bitmap,
        customQuery: String? = null
    ): GeminiSearchResult = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey()

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiSearchResult(
                title = "Gemini API Key Required",
                category = "Configuration",
                summary = "Please configure your GEMINI_API_KEY in the AI Studio Secrets panel to enable real-time Gemini AI visual search.",
                keyPoints = listOf(
                    "Open the Secrets panel in Google AI Studio",
                    "Add GEMINI_API_KEY with your valid Gemini API key",
                    "Real-time visual search and web queries will activate automatically"
                ),
                webSearchQueries = listOf("How to get Gemini API key", "Google AI Studio Secrets"),
                fullMarkdown = "Gemini API key is not configured yet. Set `GEMINI_API_KEY` in the Secrets panel to activate live visual intelligence.",
                isSuccess = false,
                errorMessage = "API key missing or placeholder"
            )
        }

        // Strictly Gemini 3.6 Flash: no other model is ever used.
        val candidateModels = listOf(MODEL_NAME)

        try {
            val base64Data = compressBitmapToBase64(bitmap)
            val userText = if (customQuery.isNullOrBlank()) {
                "Identify what is in this photo and tell me the key facts about it."
            } else customQuery

            var keyRejected = false
            var lastError = "No response from Gemini"
            var busy = false
            // 503 / 429 / 500 mean Gemini is only busy for a moment, so try the same model again after a short pause
            for (attempt in 0 until 4) {
            busy = false
            for (model in candidateModels) {
                try {
                    val body = buildPayload(model, base64Data, userText)
                        .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(requestUrl(model, apiKey))
                        .header("x-goog-api-key", apiKey)
                        .post(body)
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (response.isSuccessful && responseBody.isNotBlank()) {
                            val parsed = parseGeminiResponse(responseBody)
                            if (parsed.title.isNotBlank() || parsed.summary.isNotBlank()) {
                                return@withContext parsed
                            }
                        } else {
                            Log.w(TAG, "Model $model returned HTTP ${response.code}: $responseBody")
                            lastError = "Gemini returned HTTP ${response.code} (tried: $model)"
                            if (response.code == 503 || response.code == 429 || response.code == 500) busy = true
                            if (response.code == 401 || response.code == 403 ||
                                (response.code == 400 && responseBody.contains("API key", ignoreCase = true))
                            ) {
                                keyRejected = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed calling model $model: ${e.message}")
                    lastError = e.message ?: lastError
                }
                // a rejected key is rejected for every model, so stop trying and say so
                if (keyRejected) break
            }
            if (keyRejected || !busy) break
            delay(1500L * (attempt + 1))
            }
            if (keyRejected) {
                return@withContext GeminiSearchResult(
                    title = "Gemini API key rejected",
                    category = "Configuration",
                    summary = "Google refused the API key in this app. Create a new key in Google AI Studio (it starts with AIza), turn on the Generative Language API for it, and make sure it has no app or IP restrictions.",
                    keyPoints = listOf(
                        "Open aistudio.google.com/apikey and create a new key",
                        "Paste it as GEMINI_API_KEY in your Gradle config, or into MANUAL_API_KEY in GeminiSearchService.kt",
                        "Rebuild the app"
                    ),
                    webSearchQueries = listOf("Google AI Studio API key"),
                    fullMarkdown = "Google refused the Gemini API key.",
                    isSuccess = false,
                    errorMessage = "Gemini API key rejected"
                )
            }
            failure(
                title = "Couldn't get an answer",
                summary = if (busy) "Gemini is very busy right now. Please try again in a few seconds."
                          else "Gemini didn't respond in time. Check your connection and try again.",
                error = lastError
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini search", e)
            failure("Couldn't get an answer", "Something went wrong while searching. Please try again.", e.message ?: "error")
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap): String {
        val maxDimension = 768
        val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /** Structured JSON answer (responseSchema). Returns null when the text is not that JSON. */
    private fun parseStructured(text: String): GeminiSearchResult? {
        val o = try { JSONObject(text) } catch (e: Exception) { return null }
        fun clean(s: String) = s.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim()
        fun list(name: String): List<String> {
            val a = o.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { clean(a.optString(it)) }.filter { it.isNotEmpty() }
        }
        val title = o.optString("title").trim()
        val summary = o.optString("summary").trim()
        if (title.isBlank() && summary.isBlank()) return null
        val points = list("key_points").take(4)
        val searches = list("web_searches").take(4).ifEmpty {
            listOf("$title overview", "$title facts", "$title reviews price")
        }
        return GeminiSearchResult(
            title = title.ifBlank { summary.take(45) },
            category = o.optString("category").trim().ifBlank { "Identified Object" },
            summary = summary,
            keyPoints = points,
            webSearchQueries = searches,
            fullMarkdown = buildString { append(summary); points.forEach { append("\n- ").append(it) } },
            isSuccess = true
        )
    }

    private fun parseGeminiResponse(jsonString: String): GeminiSearchResult {
        val json = JSONObject(jsonString)
        val candidates = json.optJSONArray("candidates")
        val candidate = candidates?.optJSONObject(0)
        val content = candidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        val fullTextBuilder = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i)
                val text = part?.optString("text")
                if (!text.isNullOrBlank()) {
                    fullTextBuilder.append(text)
                }
            }
        }

        val fullText = fullTextBuilder.toString().trim()
        parseStructured(fullText)?.let { return it }
        if (fullText.isBlank()) {
            return GeminiSearchResult(
                title = "No Results",
                category = "General",
                summary = "Gemini AI did not return any identifiable content for this frame.",
                keyPoints = emptyList(),
                webSearchQueries = listOf("Visual search tips"),
                fullMarkdown = "",
                isSuccess = true
            )
        }

        var title = ""
        var category = "Identified Object"
        var summary = ""
        val keyPoints = mutableListOf<String>()
        val webSearches = mutableListOf<String>()

        val lines = fullText.lines()
        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("TITLE:", ignoreCase = true) -> {
                    title = trimmed.substringAfter(":").trim().removePrefix("**").removeSuffix("**").trim()
                    currentSection = "TITLE"
                }
                trimmed.startsWith("CATEGORY:", ignoreCase = true) -> {
                    category = trimmed.substringAfter(":").trim().removePrefix("**").removeSuffix("**").trim()
                    currentSection = "CATEGORY"
                }
                trimmed.startsWith("SUMMARY:", ignoreCase = true) -> {
                    summary = trimmed.substringAfter(":").trim()
                    currentSection = "SUMMARY"
                }
                trimmed.startsWith("KEY_POINTS:", ignoreCase = true) || trimmed.startsWith("KEY POINTS:", ignoreCase = true) -> {
                    currentSection = "KEY_POINTS"
                }
                trimmed.startsWith("WEB_SEARCHES:", ignoreCase = true) || trimmed.startsWith("WEB SEARCHES:", ignoreCase = true) -> {
                    currentSection = "WEB_SEARCHES"
                }
                trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•") -> {
                    val bulletText = trimmed.substring(1).trim().removePrefix("**").removeSuffix("**").trim()
                    if (bulletText.isNotEmpty()) {
                        when (currentSection) {
                            "KEY_POINTS" -> keyPoints.add(bulletText)
                            "WEB_SEARCHES" -> webSearches.add(bulletText)
                            "SUMMARY" -> summary += " $bulletText"
                        }
                    }
                }
                else -> {
                    if (currentSection == "SUMMARY" && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        summary = if (summary.isEmpty()) trimmed else "$summary $trimmed"
                    }
                }
            }
        }

        if (title.isBlank()) {
            // Extract first prominent line as title
            val firstLine = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: "Visual Search Result"
            title = firstLine.take(45).removePrefix("**").removeSuffix("**")
        }

        if (summary.isBlank()) {
            summary = fullText.take(240).trim()
        }

        if (webSearches.isEmpty()) {
            webSearches.add("$title official overview")
            webSearches.add("$title specifications facts")
            webSearches.add("$title reviews price")
        }

        return GeminiSearchResult(
            title = title,
            category = category,
            summary = summary,
            keyPoints = keyPoints.take(4),
            webSearchQueries = webSearches.take(4),
            fullMarkdown = fullText,
            isSuccess = true
        )
    }
}
