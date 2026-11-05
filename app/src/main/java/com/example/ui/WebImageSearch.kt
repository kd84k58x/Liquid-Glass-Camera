package com.example.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One image result from Google, with the page it came from so the user can open the source website. */
data class WebImageResult(
    val imageUrl: String,
    val thumbnailUrl: String,
    val pageUrl: String,
    val site: String,
    val title: String
)

/**
 * Google image results through the Programmable Search (Custom Search JSON) API.
 *
 * Needs two values, set once at startup (for example in MainActivity.onCreate or Application.onCreate):
 *   WebImageSearch.apiKey = "<API key with the Custom Search API enabled>"
 *   WebImageSearch.engineId = "<Programmable Search Engine ID (cx), with Image search turned on>"
 * No extra libraries: plain HttpURLConnection + org.json, which ship with Android.
 */
object WebImageSearch {
    // >>> PASTE YOUR VALUES BETWEEN THE QUOTES BELOW <<<
    // 1) MANUAL_API_KEY: API key from Google Cloud Console (Custom Search API must be enabled).
    // 2) MANUAL_ENGINE_ID: Search engine ID (cx) from programmablesearchengine.google.com, with Image search ON.
    // Do not share this file or upload it to GitHub once your keys are inside.
    private const val MANUAL_API_KEY = ""
    private const val MANUAL_ENGINE_ID = "f4cdd735db65a4b64"

    // These can still be set from code at startup; if they are left blank the values above are used.
    @Volatile var apiKey: String = ""
    @Volatile var engineId: String = ""

    private val effectiveKey: String get() = apiKey.ifBlank { MANUAL_API_KEY }
    private val effectiveEngineId: String get() = engineId.ifBlank { MANUAL_ENGINE_ID }

    const val NOT_CONFIGURED = "not_configured"

    val isConfigured: Boolean
        get() = effectiveKey.isNotBlank() && effectiveEngineId.isNotBlank()

    /** Prefers one image per website so the results come from different sites. */
    suspend fun search(query: String, maxResults: Int = 8): Result<List<WebImageResult>> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED))
            runCatching {
                val key = URLEncoder.encode(effectiveKey, "UTF-8")
                val cx = URLEncoder.encode(effectiveEngineId, "UTF-8")
                val q = URLEncoder.encode(query, "UTF-8")
                val url = URL(
                    "https://www.googleapis.com/customsearch/v1?key=$key&cx=$cx&q=$q" +
                        "&searchType=image&num=10&safe=active"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                try {
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        val msg = runCatching {
                            JSONObject(body).getJSONObject("error").getString("message")
                        }.getOrDefault("HTTP $code")
                        throw java.io.IOException(msg)
                    }
                    val items = JSONObject(body).optJSONArray("items")
                    val all = ArrayList<WebImageResult>()
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val o = items.getJSONObject(i)
                            val img = o.optJSONObject("image")
                            val link = o.optString("link")
                            val page = img?.optString("contextLink").orEmpty().ifBlank { link }
                            if (link.isBlank() || page.isBlank()) continue
                            all.add(
                                WebImageResult(
                                    imageUrl = link,
                                    thumbnailUrl = img?.optString("thumbnailLink").orEmpty().ifBlank { link },
                                    pageUrl = page,
                                    site = o.optString("displayLink").removePrefix("www."),
                                    title = o.optString("title")
                                )
                            )
                        }
                    }
                    val picked = ArrayList<WebImageResult>()
                    val seen = HashSet<String>()
                    for (r in all) if (seen.add(r.site) && picked.size < maxResults) picked.add(r)
                    for (r in all) if (picked.size < maxResults && r !in picked) picked.add(r)
                    picked.toList()
                } finally {
                    conn.disconnect()
                }
            }
        }
}
