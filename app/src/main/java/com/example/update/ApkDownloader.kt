package com.example.update

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface DownloadState {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : DownloadState {
        /** 0f..1f, or -1f when the total size is unknown. */
        val fraction: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
    }

    /** Download finished and (if a checksum was published) verified. */
    data class Completed(val file: File) : DownloadState
}

/**
 * Downloads release APKs into `cacheDir/updates/`. Files are written to a `.part` file first and
 * only renamed once complete and verified, so a half-downloaded APK is never offered for install.
 */
class ApkDownloader(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    private val appContext = context.applicationContext

    val apkDir: File
        get() = File(appContext.cacheDir, UpdateConfig.APK_SUBDIR).also { it.mkdirs() }

    fun targetFile(info: UpdateInfo): File = File(apkDir, fileName(info.versionCode))

    /**
     * Returns an already-downloaded APK for [info] if it is intact, else null.
     * Does blocking disk I/O: call from a background dispatcher.
     */
    fun findValidCached(info: UpdateInfo): File? {
        val file = targetFile(info)
        if (!file.isFile || file.length() == 0L) return null
        return when {
            info.sha256.isNotEmpty() -> file.takeIf { it.sha256Hex() == info.sha256 }
            info.fileSizeBytes > 0 -> file.takeIf { it.length() == info.fileSizeBytes }
            else -> null // nothing to verify against, so don't trust it
        }
    }

    /**
     * Streams the APK from [url]. Emits [DownloadState.Progress] (throttled) and finally
     * [DownloadState.Completed]. Throws [UpdateException]/[IOException] on failure; cancelling the
     * collector aborts the HTTP call and removes the partial file.
     */
    fun download(info: UpdateInfo, url: String): Flow<DownloadState> = flow {
        val cached = findValidCached(info)
        if (cached != null) {
            emit(DownloadState.Completed(cached))
            return@flow
        }

        val target = targetFile(info)
        val part = File(apkDir, target.name + PART_SUFFIX)
        part.delete()
        target.delete()

        val call = client.newCall(Request.Builder().url(url).build())
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        var success = false

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateException("Download failed (HTTP ${response.code})")
                }
                val body = response.body ?: throw UpdateException("Download failed (empty response)")

                val total = body.contentLength().takeIf { it > 0 } ?: info.fileSizeBytes.takeIf { it > 0 } ?: -1L
                if (total > 0 && apkDir.usableSpace < total + FREE_SPACE_MARGIN_BYTES) {
                    throw UpdateException("Not enough free storage to download the update")
                }

                emit(DownloadState.Progress(0L, total))

                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                var read = 0L
                var lastEmit = 0L

                body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n

                            val now = SystemClock.elapsedRealtime()
                            if (now - lastEmit >= PROGRESS_INTERVAL_MS) {
                                lastEmit = now
                                emit(DownloadState.Progress(read, total))
                            }
                        }
                        output.flush()
                    }
                }

                if (total > 0 && read != total) {
                    throw UpdateException("Download was incomplete. Please try again.")
                }
                emit(DownloadState.Progress(read, if (total > 0) total else read))

                if (info.sha256.isNotEmpty()) {
                    val actual = digest.digest().toHex()
                    if (actual != info.sha256) {
                        throw UpdateException("Downloaded file is corrupted (checksum mismatch)")
                    }
                }
            }

            if (!part.renameTo(target)) {
                throw UpdateException("Could not save the downloaded update")
            }
            success = true
            emit(DownloadState.Completed(target))
        } catch (e: IOException) {
            // call.cancel() surfaces as IOException; if we were cancelled, propagate cancellation instead.
            currentCoroutineContext().ensureActive()
            throw e
        } finally {
            cancelHandle?.dispose()
            if (!success) part.delete()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Removes leftovers: partial downloads and APKs whose versionCode is not newer than
     * [installedVersionCode] (i.e. already installed). Blocking: call off the main thread.
     */
    fun deleteObsolete(installedVersionCode: Long) {
        apkDir.listFiles()?.forEach { f ->
            when {
                f.name.endsWith(PART_SUFFIX) -> f.delete()
                else -> {
                    val code = FILE_REGEX.matchEntire(f.name)?.groupValues?.get(1)?.toLongOrNull()
                    if (code != null && code <= installedVersionCode) f.delete()
                }
            }
        }
    }

    /** Blocking: call off the main thread. */
    fun deleteAll() {
        apkDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 100L
        private const val FREE_SPACE_MARGIN_BYTES = 16L * 1024 * 1024
        private const val PART_SUFFIX = ".part"
        private val FILE_REGEX = Regex("""liquid-camera-(\d+)\.apk""")

        private fun fileName(versionCode: Long) = "liquid-camera-$versionCode.apk"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(this).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().toHex()
}
