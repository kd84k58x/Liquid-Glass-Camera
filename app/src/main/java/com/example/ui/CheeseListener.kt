package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Listens for the word "cheese" with Android's SpeechRecognizer and calls [onCheese] the moment it is heard.
 * It uses partial results, so the shutter fires while the word is still being said, not after the sentence ends.
 * Prefers the on-device recogniser (Android 13+ with an offline speech pack), otherwise the phone's normal one.
 * Every method must be called on the main thread.
 */
class CheeseListener(
    private val context: Context,
    private val canFire: () -> Boolean,
    private val onCheese: () -> Unit,
    private val onUnavailable: () -> Unit
) : RecognitionListener {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var destroyed = false
    private var firedThisPhrase = false
    private var lastFireTime = 0L
    private var onDeviceFailed = false
    private var quickErrors = 0

    private val restart = Runnable { if (wanted && !destroyed) listen() }

    fun start() {
        if (destroyed || wanted) return
        wanted = true
        listen()
    }

    fun stop() {
        wanted = false
        main.removeCallbacks(restart)
        releaseRecognizer()
    }

    fun destroy() {
        destroyed = true
        stop()
    }

    private fun releaseRecognizer() {
        try { recognizer?.cancel() } catch (ignored: Exception) {}
        try { recognizer?.destroy() } catch (ignored: Exception) {}
        recognizer = null
    }

    private fun listen() {
        try {
            if (recognizer == null) {
                recognizer = when {
                    Build.VERSION.SDK_INT >= 33 && !onDeviceFailed && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
                    else -> null
                }
                if (recognizer == null) {
                    wanted = false
                    onUnavailable()
                    return
                }
                recognizer?.setRecognitionListener(this)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            firedThisPhrase = false
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            scheduleRestart(600L, rebuild = true)
        }
    }

    private fun scheduleRestart(delayMs: Long, rebuild: Boolean = false) {
        if (!wanted || destroyed) return
        if (rebuild) releaseRecognizer()
        main.removeCallbacks(restart)
        main.postDelayed(restart, delayMs)
    }

    private fun heardCheese(bundle: Bundle?): Boolean {
        val words = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        return words.any { CHEESE.containsMatchIn(it.lowercase()) }
    }

    private fun fireIfCheese(bundle: Bundle?) {
        if (firedThisPhrase || !heardCheese(bundle)) return
        firedThisPhrase = true
        val now = System.currentTimeMillis()
        if (now - lastFireTime < 1200L || !canFire()) return
        lastFireTime = now
        onCheese()
        // start a fresh listen so the next "cheese" is not mixed with this phrase
        try { recognizer?.cancel() } catch (ignored: Exception) {}
        scheduleRestart(350L)
    }

    override fun onPartialResults(partialResults: Bundle?) = fireIfCheese(partialResults)

    override fun onResults(results: Bundle?) {
        quickErrors = 0
        fireIfCheese(results)
        scheduleRestart(120L)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                wanted = false
                onUnavailable()
            }
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(150L)
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                onDeviceFailed = true                        // no offline pack: use the normal recogniser instead
                scheduleRestart(300L, rebuild = true)
            }
            else -> {
                quickErrors++                                // busy, network, client...: back off a little, rebuild
                scheduleRestart((300L * quickErrors).coerceAtMost(3000L), rebuild = true)
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) { firedThisPhrase = false }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private companion object {
        // "cheese" as speech engines tend to write it
        val CHEESE = Regex("\\b(cheese|cheeze|cheez|chees|chiz)\\b")
    }
}
