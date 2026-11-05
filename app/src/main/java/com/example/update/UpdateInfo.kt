package com.example.update

import com.google.firebase.Timestamp

/**
 * Central configuration for the in-app updater.
 *
 * Firestore document (default: `app_updates/latest`) expected fields:
 *
 *   versionCode              Number   (required)  e.g. 2045699
 *   versionName              String   (optional)  e.g. "2.4.56.99arm64"
 *   apkPath                  String   (one of apkPath / apkUrl is required)
 *                                      Firebase Storage path, e.g. "updates/liquid-camera-2045699.apk"
 *                                      (a "gs://bucket/path" URL is accepted too)
 *   apkUrl                   String   (alternative to apkPath, must be https://)
 *   releaseNotes             String   (optional)
 *   mandatory                Boolean  (optional, default false)
 *   minSupportedVersionCode  Number   (optional, installs older than this are forced to update)
 *   sha256                   String   (strongly recommended) hex SHA-256 of the APK
 *   fileSizeBytes            Number   (optional) used for progress / disk-space check
 *   publishedAt              Timestamp (optional)
 */
object UpdateConfig {
    const val COLLECTION = "app_updates"
    const val DOCUMENT = "latest"

    /** Must match the authority declared in AndroidManifest.xml: ${applicationId}.updatefileprovider */
    const val FILE_PROVIDER_SUFFIX = ".updatefileprovider"

    /**
     * Sub-directory of cacheDir where APKs are stored. Your res/xml/update_file_paths.xml
     * must expose it, e.g.  <cache-path name="updates" path="updates/" />
     */
    const val APK_SUBDIR = "updates"

    const val FETCH_TIMEOUT_MS = 15_000L
}

/** Thrown for expected, user-presentable update failures. */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Release metadata published in Firestore. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkPath: String,
    val apkUrl: String,
    val releaseNotes: String,
    /**
     * Whether this update must be installed. UpdateViewModel replaces the raw Firestore value with
     * the *effective* value (raw flag OR installed version < minSupportedVersionCode).
     */
    val mandatory: Boolean,
    val minSupportedVersionCode: Long,
    val sha256: String,
    val fileSizeBytes: Long,
    val publishedAtMillis: Long?,
) {
    fun isNewerThan(installedVersionCode: Long): Boolean = versionCode > installedVersionCode

    fun isMandatoryFor(installedVersionCode: Long): Boolean =
        mandatory || (minSupportedVersionCode > 0 && installedVersionCode < minSupportedVersionCode)

    companion object {
        /** @throws IllegalArgumentException if required fields are missing or invalid. */
        fun fromMap(data: Map<String, Any?>): UpdateInfo {
            fun long(key: String): Long? = (data[key] as? Number)?.toLong()
            fun string(key: String): String = (data[key] as? String).orEmpty().trim()

            val versionCode = long("versionCode")
                ?: throw IllegalArgumentException("Release info is missing 'versionCode'")
            require(versionCode > 0) { "Release info has an invalid 'versionCode'" }

            val apkPath = string("apkPath")
            val apkUrl = string("apkUrl")
            require(apkPath.isNotEmpty() || apkUrl.isNotEmpty()) {
                "Release info needs 'apkPath' or 'apkUrl'"
            }
            require(apkUrl.isEmpty() || apkUrl.startsWith("https://")) {
                "'apkUrl' must use https"
            }

            return UpdateInfo(
                versionCode = versionCode,
                versionName = string("versionName").ifEmpty { versionCode.toString() },
                apkPath = apkPath,
                apkUrl = apkUrl,
                releaseNotes = string("releaseNotes"),
                mandatory = data["mandatory"] as? Boolean ?: false,
                minSupportedVersionCode = long("minSupportedVersionCode") ?: 0L,
                sha256 = string("sha256").lowercase(),
                fileSizeBytes = long("fileSizeBytes") ?: 0L,
                publishedAtMillis = (data["publishedAt"] as? Timestamp)?.toDate()?.time,
            )
        }
    }
}

/** Outcome of comparing the published release with the installed version. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val info: UpdateInfo) : UpdateCheckResult
}
