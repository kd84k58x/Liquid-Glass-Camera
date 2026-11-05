package com.example.update

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads release info from Firestore and resolves the APK download URL from Firebase Storage.
 * All functions return [Result] and never throw (except for coroutine cancellation).
 *
 * Firebase is resolved lazily, inside [safe], on first use. Constructing this class never touches
 * Firebase, so a missing/broken Firebase configuration can no longer crash app startup; it surfaces
 * as a failed [Result] instead.
 */
class UpdateRepository(
    private val firestoreProvider: () -> FirebaseFirestore,
    private val storageProvider: () -> FirebaseStorage,
) {

    /** Production constructor: ensures Firebase is initialised on first use, never during construction. */
    constructor(context: Context) : this(
        firestoreProvider = {
            requireFirebase(context)
            FirebaseFirestore.getInstance()
        },
        storageProvider = {
            requireFirebase(context)
            FirebaseStorage.getInstance()
        },
    )

    /** Fetches the latest published release (always from the server, never from cache). */
    suspend fun fetchLatest(): Result<UpdateInfo> = safe {
        val firestore = firestoreProvider()
        val snapshot = withTimeoutOrNull(UpdateConfig.FETCH_TIMEOUT_MS) {
            firestore.collection(UpdateConfig.COLLECTION)
                .document(UpdateConfig.DOCUMENT)
                .get(Source.SERVER)
                .await()
        } ?: throw UpdateException("Timed out while contacting the update server")

        if (!snapshot.exists()) throw UpdateException("No release has been published yet")
        val data = snapshot.data ?: throw UpdateException("Release info is empty")

        try {
            UpdateInfo.fromMap(data)
        } catch (e: IllegalArgumentException) {
            throw UpdateException(e.message ?: "Release info is malformed", e)
        }
    }

    /** Fetches the latest release and compares it with [installedVersionCode]. */
    suspend fun checkForUpdate(installedVersionCode: Long): Result<UpdateCheckResult> =
        fetchLatest().map { info ->
            if (info.isNewerThan(installedVersionCode)) UpdateCheckResult.Available(info)
            else UpdateCheckResult.UpToDate
        }

    /** Returns a direct https download URL for the APK described by [info]. */
    suspend fun resolveDownloadUrl(info: UpdateInfo): Result<String> = safe {
        if (info.apkUrl.isNotEmpty()) return@safe info.apkUrl

        val storage = storageProvider()
        val ref = if (info.apkPath.startsWith("gs://") || info.apkPath.startsWith("https://")) {
            storage.getReferenceFromUrl(info.apkPath)
        } else {
            storage.reference.child(info.apkPath.trimStart('/'))
        }
        val url = withTimeoutOrNull(UpdateConfig.FETCH_TIMEOUT_MS) { ref.downloadUrl.await() }
            ?: throw UpdateException("Timed out while locating the update file")
        url.toString()
    }

    private inline fun <T> safe(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}

/** Throws [UpdateException] (caught by [UpdateRepository.safe]) when Firebase cannot be initialised. */
private fun requireFirebase(context: Context) {
    if (!FirebaseBootstrap.ensureInitialized(context)) {
        throw UpdateException(FirebaseBootstrap.NOT_CONFIGURED_MESSAGE)
    }
}

/** Minimal Task -> suspend bridge so we don't need kotlinx-coroutines-play-services. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        when {
            task.isSuccessful -> {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            }
            task.isCanceled -> cont.resumeWithException(UpdateException("Request was cancelled"))
            else -> cont.resumeWithException(
                task.exception ?: UpdateException("Unknown Firebase error")
            )
        }
    }
}