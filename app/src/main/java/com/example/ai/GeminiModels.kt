package com.example.ai

/**
 * Gemini models offered by the model picker in Search mode.
 *
 * Model IDs come from the Gemini API model list (ai.google.dev/gemini-api/docs/models). That list changes
 * often, so check it and edit this enum to add, remove or rename models.
 */
enum class GeminiModel(val id: String, val title: String, val subtitle: String) {
    FLASH_3_8("gemini-3.8-flash", "Gemini 3.8 Flash", "Fast and balanced"),
    FLASH_LITE_3_5("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Lowest latency"),
    PRO_3_1("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Deeper reasoning"),
    ARGON_4("gemini-4-argon", "Gemini 4 Argon", "Newest flagship"),
    FLASH_2_5("gemini-2.5-flash", "Gemini 2.5 Flash", "Previous generation");

    companion object {
        val DEFAULT = FLASH_3_8

        fun fromId(id: String?): GeminiModel = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * The model currently chosen in the picker. The camera screen keeps this up to date;
 * GeminiSearchService should read [selectedId] when it builds a request, for example:
 *
 *     val modelId = GeminiModelSelection.selectedId
 */
object GeminiModelSelection {
    @Volatile
    var selectedId: String = GeminiModel.DEFAULT.id
}
