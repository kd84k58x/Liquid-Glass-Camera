package com.example.update

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

sealed interface InstallResult {
    /** The system package installer was opened. */
    data object Launched : InstallResult

    /** "Install unknown apps" is not allowed for this app yet. */
    data object NeedsPermission : InstallResult

    data class Failed(val message: String) : InstallResult
}

/** Validates a downloaded APK and hands it to the system package installer via FileProvider. */
class ApkInstaller(context: Context) {
    private val appContext = context.applicationContext

    /** True if the app may launch the installer (always true below API 26). */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    /** Intent for the "Install unknown apps" settings screen of this app (API 26+). */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the settings screen. Returns false if it could not be opened. */
    fun openUnknownSourcesSettings(): Boolean = try {
        appContext.startActivity(unknownSourcesSettingsIntent())
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    /**
     * Sanity-checks the APK before installing: it must parse, belong to this app, and carry the
     * version announced in Firestore. (Signature compatibility is enforced by Android itself:
     * an APK signed with a different key will be rejected by the installer.)
     *
     * @return an error message, or null if the file looks good. Blocking: call off the main thread.
     */
    fun validate(file: File, info: UpdateInfo): String? {
        if (!file.isFile || file.length() == 0L) return "The downloaded update file is missing"

        val pm = appContext.packageManager
        val archive = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, 0)
            }
        } catch (_: Exception) {
            null
        } ?: return "The downloaded file is not a valid APK"

        if (archive.packageName != appContext.packageName) {
            return "The update is for a different app (${archive.packageName})"
        }
        val archiveVersion = PackageInfoCompat.getLongVersionCode(archive)
        if (archiveVersion != info.versionCode) {
            return "The update file has version $archiveVersion but ${info.versionCode} was expected"
        }
        return null
    }

    fun buildInstallIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            appContext,
            appContext.packageName + UpdateConfig.FILE_PROVIDER_SUFFIX,
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Launches the system installer for [file]. */
    fun install(file: File): InstallResult {
        if (!canInstallPackages()) return InstallResult.NeedsPermission
        if (!file.isFile) return InstallResult.Failed("The update file no longer exists")

        return try {
            appContext.startActivity(buildInstallIntent(file))
            InstallResult.Launched
        } catch (_: ActivityNotFoundException) {
            InstallResult.Failed("No package installer found on this device")
        } catch (e: IllegalArgumentException) {
            // FileProvider could not map the file: res/xml/update_file_paths.xml is missing the cache "updates/" path.
            InstallResult.Failed("Update file is not shareable (check update_file_paths.xml): ${e.message}")
        } catch (e: SecurityException) {
            InstallResult.Failed("Installer launch was blocked: ${e.message}")
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
