package com.example.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaActionSound
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.sin

enum class ShutterSoundType(val id: String, val title: String, val subtitle: String) {
    DEFAULT("default", "Default Shutter", "Standard system mechanical click"),
    CUSTOM_1("custom_1", "Custom Shutter 1", "Mechanical analog film shutter (hi.mp3)"),
    CUSTOM_2("custom_2", "Custom Shutter 2", "Crisp electronic blip shutter (hi1.mp3)")
}

enum class RecordSoundType(val id: String, val title: String, val subtitle: String) {
    DEFAULT("default", "Default System Tone", "Standard prompt sound"),
    CHIME("chime", "Chime & Beep", "Melodic chime start / soft beep stop"),
    PULSE("pulse", "Modern Pulse", "Electronic pulse start / low tone stop"),
    RETRO("retro", "Retro Camcorder", "Classic dual-tone beep / cam click")
}

enum class TimerSoundType(val id: String, val title: String, val subtitle: String = "") {
    DEFAULT_TICK("default_tick", "Standard Tick", "Crisp studio clock tick"),
    DIGITAL_BEEP("digital_beep", "Digital Beep", "High frequency 880Hz electronic pulse"),
    WOOD_CLICK("wood_click", "Percussion Tap", "Warm acoustic block click"),
    DISABLED("disabled", "Mute (Disabled)", "Silent countdown with visual flash")
}

object SoundUtils {
    private const val SAMPLE_RATE = 44100
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "CameraSoundWorker").apply { priority = Thread.MAX_PRIORITY }
    }

    @Volatile
    private var isInitialized = false

    private val soundPool: SoundPool by lazy {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attributes)
            .build()
    }

    private val soundIdMap = ConcurrentHashMap<String, Int>()
    private val pcmCache = ConcurrentHashMap<String, ByteArray>()

    fun init(context: Context) {
        if (isInitialized) return
        executor.execute {
            try {
                val cacheDir = File(context.cacheDir, "camera_sounds").apply { mkdirs() }

                // Load raw audio resources if present
                try {
                    val rawHiId = context.resources.getIdentifier("hi", "raw", context.packageName)
                    if (rawHiId != 0) {
                        val soundId = soundPool.load(context, rawHiId, 1)
                        if (soundId != 0) {
                            soundIdMap["shutter_custom_1"] = soundId
                        }
                    }
                } catch (ignored: Exception) {}

                try {
                    var rawHi1Id = context.resources.getIdentifier("hi1", "raw", context.packageName)
                    if (rawHi1Id == 0) {
                        rawHi1Id = context.resources.getIdentifier("hi2", "raw", context.packageName)
                    }
                    if (rawHi1Id != 0) {
                        val soundId = soundPool.load(context, rawHi1Id, 1)
                        if (soundId != 0) {
                            soundIdMap["shutter_custom_2"] = soundId
                        }
                    }
                } catch (ignored: Exception) {}
                
                // Pre-generate all sound buffers
                val soundGenerators = mutableMapOf(
                    "shutter_default_fallback" to ::generateDefaultShutterPcm,
                    "record_start_default" to ::generateDefaultRecordStartPcm,
                    "record_stop_default" to ::generateDefaultRecordStopPcm,
                    "record_start_chime" to ::generateChimeStartPcm,
                    "record_stop_chime" to ::generateChimeStopPcm,
                    "record_start_pulse" to ::generatePulseStartPcm,
                    "record_stop_pulse" to ::generatePulseStopPcm,
                    "record_start_retro" to ::generateRetroStartPcm,
                    "record_stop_retro" to ::generateRetroStopPcm,
                    "timer_default" to ::generateStandardTimerPcm,
                    "timer_digital" to ::generateDigitalBeepPcm,
                    "timer_wood" to ::generateWoodClickPcm
                )

                if (!soundIdMap.containsKey("shutter_custom_1")) {
                    soundGenerators["shutter_custom_1"] = ::generateFilmShutterPcm
                }
                if (!soundIdMap.containsKey("shutter_custom_2")) {
                    soundGenerators["shutter_custom_2"] = ::generateBlipShutterPcm
                }

                soundGenerators.forEach { (key, generator) ->
                    try {
                        val pcm = generator()
                        pcmCache[key] = pcm
                        val wavFile = File(cacheDir, "$key.wav")
                        if (!wavFile.exists() || wavFile.length() == 0L) {
                            FileOutputStream(wavFile).use { fos ->
                                fos.write(pcmToWav(pcm, SAMPLE_RATE))
                            }
                        }
                        val soundId = soundPool.load(wavFile.absolutePath, 1)
                        if (soundId != 0) {
                            soundIdMap[key] = soundId
                        }
                    } catch (ignored: Exception) {}
                }
                isInitialized = true
            } catch (ignored: Exception) {}
        }
    }

    fun playShutter(context: Context, soundType: ShutterSoundType, mediaActionSound: MediaActionSound?) {
        init(context)
        when (soundType) {
            ShutterSoundType.DEFAULT -> {
                try {
                    mediaActionSound?.play(MediaActionSound.SHUTTER_CLICK) ?: playSoundKey("shutter_default_fallback", ::generateDefaultShutterPcm)
                } catch (e: Exception) {
                    playSoundKey("shutter_default_fallback", ::generateDefaultShutterPcm)
                }
            }
            ShutterSoundType.CUSTOM_1 -> {
                playSoundKey("shutter_custom_1", ::generateFilmShutterPcm)
            }
            ShutterSoundType.CUSTOM_2 -> {
                playSoundKey("shutter_custom_2", ::generateBlipShutterPcm)
            }
        }
    }

    fun playRecordStartSound(context: Context, soundType: RecordSoundType, mediaActionSound: MediaActionSound?) {
        init(context)
        when (soundType) {
            RecordSoundType.DEFAULT -> {
                try {
                    mediaActionSound?.play(MediaActionSound.START_VIDEO_RECORDING) ?: playSoundKey("record_start_default", ::generateDefaultRecordStartPcm)
                } catch (e: Exception) {
                    playSoundKey("record_start_default", ::generateDefaultRecordStartPcm)
                }
            }
            RecordSoundType.CHIME -> playSoundKey("record_start_chime", ::generateChimeStartPcm)
            RecordSoundType.PULSE -> playSoundKey("record_start_pulse", ::generatePulseStartPcm)
            RecordSoundType.RETRO -> playSoundKey("record_start_retro", ::generateRetroStartPcm)
        }
    }

    fun playRecordStopSound(context: Context, soundType: RecordSoundType, mediaActionSound: MediaActionSound?) {
        init(context)
        when (soundType) {
            RecordSoundType.DEFAULT -> {
                try {
                    mediaActionSound?.play(MediaActionSound.STOP_VIDEO_RECORDING) ?: playSoundKey("record_stop_default", ::generateDefaultRecordStopPcm)
                } catch (e: Exception) {
                    playSoundKey("record_stop_default", ::generateDefaultRecordStopPcm)
                }
            }
            RecordSoundType.CHIME -> playSoundKey("record_stop_chime", ::generateChimeStopPcm)
            RecordSoundType.PULSE -> playSoundKey("record_stop_pulse", ::generatePulseStopPcm)
            RecordSoundType.RETRO -> playSoundKey("record_stop_retro", ::generateRetroStopPcm)
        }
    }

    fun playTimerTick(soundType: TimerSoundType) {
        when (soundType) {
            TimerSoundType.DEFAULT_TICK -> playSoundKey("timer_default", ::generateStandardTimerPcm)
            TimerSoundType.DIGITAL_BEEP -> playSoundKey("timer_digital", ::generateDigitalBeepPcm)
            TimerSoundType.WOOD_CLICK -> playSoundKey("timer_wood", ::generateWoodClickPcm)
            TimerSoundType.DISABLED -> { /* Muted */ }
        }
    }

    private fun playSoundKey(key: String, fallbackGenerator: () -> ByteArray) {
        val soundId = soundIdMap[key]
        if (soundId != null && soundId > 0) {
            val streamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            if (streamId != 0) return
        }

        // Asynchronous fallback via non-cutting AudioTrack
        executor.execute {
            val pcm = pcmCache.getOrPut(key) { fallbackGenerator() }
            playPcmNonCutting(pcm)
        }
    }

    private fun playPcmNonCutting(data: ByteArray) {
        var audioTrack: AudioTrack? = null
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(data.size, minBufSize)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(data, 0, data.size)
            audioTrack.play()

            // Calculate duration and provide generous buffer so sound tail finishes naturally
            val durationMs = (data.size / 2) * 1000L / SAMPLE_RATE
            Thread.sleep(durationMs + 180L)
        } catch (ignored: Exception) {
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (ignored: Exception) {}
        }
    }

    // --- High Quality Synthesizers ---

    private fun generateFilmShutterPcm(): ByteArray {
        // Mechanical SLR shutter: mirror slap + curtain transit + latch release (~190ms)
        val numSamples = (SAMPLE_RATE * 0.19).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0

            // Stage 1: Mirror lift (0 to 25ms)
            if (t < 0.025) {
                val progress = t / 0.025
                val env = (1.0 - progress) * (1.0 - progress)
                sample += (sin(2.0 * Math.PI * 650.0 * t) * 0.6 + sin(2.0 * Math.PI * 1300.0 * t) * 0.3) * env
            }
            // Stage 2: Main curtain snap (25ms to 90ms)
            if (t in 0.025..0.090) {
                val t2 = t - 0.025
                val env = exp(-t2 * 45.0)
                sample += (sin(2.0 * Math.PI * 1800.0 * t2) * 0.7 + sin(2.0 * Math.PI * 3200.0 * t2) * 0.35 + sin(2.0 * Math.PI * 850.0 * t2) * 0.4) * env
            }
            // Stage 3: Return spring latch (80ms to 190ms)
            if (t >= 0.080) {
                val t3 = t - 0.080
                val env = exp(-t3 * 28.0)
                sample += (sin(2.0 * Math.PI * 1100.0 * t3) * 0.5 + sin(2.0 * Math.PI * 2200.0 * t3) * 0.25) * env
            }

            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateBlipShutterPcm(): ByteArray {
        // Modern crisp electronic chirp (140ms with smooth decay)
        val numSamples = (SAMPLE_RATE * 0.14).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 9.0)
            // Frequency sweep from 2200Hz down to 800Hz
            val currentFreq = 2200.0 - (1400.0 * progress)
            val sample = (sin(2.0 * Math.PI * currentFreq * t) * 0.75 + sin(4.0 * Math.PI * currentFreq * t) * 0.25) * env

            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateDefaultShutterPcm(): ByteArray {
        // Full standard camera click (~160ms)
        val numSamples = (SAMPLE_RATE * 0.16).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 14.0)
            val sample = (sin(2.0 * Math.PI * 1400.0 * t) * 0.6 + sin(2.0 * Math.PI * 2800.0 * t) * 0.3 + sin(2.0 * Math.PI * 700.0 * t) * 0.4) * env

            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateChimeStartPcm(): ByteArray {
        // 3-note ascending cheerful chime (C5 -> E5 -> G5, ~320ms)
        val numSamples = (SAMPLE_RATE * 0.32).toInt()
        val pcm = ByteArray(numSamples * 2)
        val notes = listOf(523.25 to 0.0, 659.25 to 0.09, 783.99 to 0.18)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for ((freq, startT) in notes) {
                if (t >= startT) {
                    val noteT = t - startT
                    val env = exp(-noteT * 11.0)
                    sample += (sin(2.0 * Math.PI * freq * noteT) * 0.65 + sin(4.0 * Math.PI * freq * noteT) * 0.25) * env
                }
            }
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateChimeStopPcm(): ByteArray {
        // 2-note descending chime (G5 -> C5, ~260ms)
        val numSamples = (SAMPLE_RATE * 0.26).toInt()
        val pcm = ByteArray(numSamples * 2)
        val notes = listOf(783.99 to 0.0, 523.25 to 0.11)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for ((freq, startT) in notes) {
                if (t >= startT) {
                    val noteT = t - startT
                    val env = exp(-noteT * 12.0)
                    sample += (sin(2.0 * Math.PI * freq * noteT) * 0.65 + sin(4.0 * Math.PI * freq * noteT) * 0.25) * env
                }
            }
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generatePulseStartPcm(): ByteArray {
        // Modern pulse rising tone (A4 -> E5, ~220ms)
        val numSamples = (SAMPLE_RATE * 0.22).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val freq = 440.0 + (220.0 * progress)
            val env = if (progress < 0.1) progress / 0.1 else exp(-(progress - 0.1) * 6.5)
            val sample = (sin(2.0 * Math.PI * freq * t) * 0.75 + sin(4.0 * Math.PI * freq * t) * 0.2) * env

            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generatePulseStopPcm(): ByteArray {
        // Modern pulse descending tone (E5 -> A4, ~190ms)
        val numSamples = (SAMPLE_RATE * 0.19).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val freq = 660.0 - (220.0 * progress)
            val env = if (progress < 0.1) progress / 0.1 else exp(-(progress - 0.1) * 7.5)
            val sample = (sin(2.0 * Math.PI * freq * t) * 0.75 + sin(4.0 * Math.PI * freq * t) * 0.2) * env

            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateRetroStartPcm(): ByteArray {
        // Camcorder dual-tone beep (1200Hz + 1800Hz burst, ~200ms)
        val numSamples = (SAMPLE_RATE * 0.20).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            if (t in 0.0..0.08) {
                sample = sin(2.0 * Math.PI * 1200.0 * t) * 0.7
            } else if (t in 0.10..0.19) {
                sample = sin(2.0 * Math.PI * 1800.0 * t) * 0.7
            }
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateRetroStopPcm(): ByteArray {
        // Retro camcorder low stop cue (600Hz, ~160ms)
        val numSamples = (SAMPLE_RATE * 0.16).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 8.0)
            val sample = sin(2.0 * Math.PI * 600.0 * t) * 0.75 * env
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateDefaultRecordStartPcm(): ByteArray {
        val numSamples = (SAMPLE_RATE * 0.20).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 7.0)
            val sample = (sin(2.0 * Math.PI * 880.0 * t) * 0.6 + sin(2.0 * Math.PI * 1320.0 * t) * 0.3) * env
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateDefaultRecordStopPcm(): ByteArray {
        val numSamples = (SAMPLE_RATE * 0.18).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 8.0)
            val sample = (sin(2.0 * Math.PI * 660.0 * t) * 0.6 + sin(2.0 * Math.PI * 440.0 * t) * 0.4) * env
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateStandardTimerPcm(): ByteArray {
        val numSamples = (SAMPLE_RATE * 0.065).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 18.0)
            val sample = (sin(2.0 * Math.PI * 1400.0 * t) * 0.7 + sin(2.0 * Math.PI * 2800.0 * t) * 0.3) * env
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateDigitalBeepPcm(): ByteArray {
        val numSamples = (SAMPLE_RATE * 0.085).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            // Smooth attack and release to eliminate click artifacts
            val env = if (progress < 0.1) progress / 0.1 else exp(-(progress - 0.1) * 8.0)
            val sample = sin(2.0 * Math.PI * 2000.0 * t) * 0.8 * env
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun generateWoodClickPcm(): ByteArray {
        val numSamples = (SAMPLE_RATE * 0.055).toInt()
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val env = exp(-progress * 24.0)
            val sample = (sin(2.0 * Math.PI * 620.0 * t) * 0.65 + sin(2.0 * Math.PI * 1240.0 * t) * 0.35) * env
            val val16 = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
            pcm[i * 2] = (val16.toInt() and 0x00ff).toByte()
            pcm[i * 2 + 1] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }
        return pcm
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 1 * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = 1
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        val wav = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, wav, 0, header.size)
        System.arraycopy(pcmData, 0, wav, header.size, pcmData.size)
        return wav
    }
}



